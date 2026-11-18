package org.tarish.adhan.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.tarish.adhan.core.di.IoDispatcher
import org.tarish.adhan.core.format.locationKey
import org.tarish.adhan.core.result.AppError
import org.tarish.adhan.core.result.failure
import org.tarish.adhan.core.time.Clock
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.QiblaMath
import org.tarish.adhan.domain.model.StoredLocation
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class PlaceResult(val name: String, val region: String?, val country: String?, val lat: Double, val lng: Double)

interface LocationRepository {
    val current: Flow<StoredLocation?>
    fun hasPermission(): Boolean
    fun servicesEnabled(): Boolean
    fun geocoderAvailable(): Boolean

    /** §9.2. When [force] is false the 6 h throttle applies and the stored location is returned unchanged. */
    suspend fun refreshFromGps(force: Boolean): Result<StoredLocation?>
    suspend fun search(query: String): Result<List<PlaceResult>>
    suspend fun setManual(place: PlaceResult): StoredLocation

    /** Resolution to turn on location services in place, or null if not resolvable. */
    suspend fun settingsResolution(): IntentSender?
}

@Singleton
class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : LocationRepository {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override val current: Flow<StoredLocation?> = settings.location

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun servicesEnabled(): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    override fun geocoderAvailable(): Boolean = Geocoder.isPresent()

    override suspend fun settingsResolution(): IntentSender? = try {
        val req = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000).build())
            .build()
        LocationServices.getSettingsClient(context).checkLocationSettings(req).await()
        null
    } catch (e: ResolvableApiException) {
        e.resolution.intentSender
    } catch (e: Exception) {
        null
    }

    override suspend fun refreshFromGps(force: Boolean): Result<StoredLocation?> {
        val stored = settings.location.first()
        val now = clock.now()
        if (!force && stored != null && now.toEpochMilli() - settings.lastGpsRefreshAt() <= SIX_HOURS) {
            return Result.success(stored)
        }
        if (!hasPermission()) return failure(AppError.LocationPermissionDenied)
        if (!servicesEnabled()) return failure(AppError.LocationServicesOff)

        val fix = getFix() ?: return failure(AppError.LocationTimeout)
        settings.setLastGpsRefreshAt(now.toEpochMilli())
        val (lat, lng) = fix

        val moved = stored == null ||
            stored.source == StoredLocation.Source.MANUAL ||
            QiblaMath.haversineKm(stored.lat, stored.lng, lat, lng) > 2.0
        if (!moved) return Result.success(stored)

        val addr = reverse(lat, lng)
        val newKey = locationKey(lat, lng)
        val loc = StoredLocation(
            lat = lat, lng = lng,
            city = addr?.let { it.locality ?: it.subAdminArea ?: it.adminArea },
            region = addr?.adminArea,
            country = addr?.countryName,
            zone = stored?.zone?.takeIf { stored.key == newKey },
            source = StoredLocation.Source.GPS,
            updatedAt = now,
        )
        settings.setLocation(loc)
        return Result.success(loc)
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFix(): Pair<Double, Double>? {
        val cts = CancellationTokenSource()
        val loc = try {
            withTimeoutOrNull(10_000) {
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
            }
        } catch (e: Exception) {
            Timber.w("getCurrentLocation failed: %s", e.javaClass.simpleName); null
        } finally {
            cts.cancel()
        }
        if (loc != null) return loc.latitude to loc.longitude
        val last = runCatching { fused.lastLocation.await() }.getOrNull() ?: return null
        val ageMs = clock.now().toEpochMilli() - last.time
        return if (ageMs < 30 * 60_000) last.latitude to last.longitude else null
    }

    private suspend fun reverse(lat: Double, lng: Double): Address? {
        if (!Geocoder.isPresent()) return null
        return runCatching { geocode { g, cb -> fromLocation(g, lat, lng, 1, cb) }.firstOrNull() }.getOrNull()
    }

    override suspend fun search(query: String): Result<List<PlaceResult>> {
        if (!Geocoder.isPresent()) return failure(AppError.GeocoderUnavailable)
        return try {
            val list = geocode { g, cb -> fromName(g, query, 8, cb) }
            val results = list.filter { it.hasLatitude() && it.hasLongitude() }
                .map {
                    PlaceResult(
                        name = it.locality ?: it.featureName ?: it.adminArea ?: query,
                        region = it.adminArea,
                        country = it.countryName,
                        lat = it.latitude, lng = it.longitude,
                    )
                }
                .distinctBy { locationKey(it.lat, it.lng) }
            Result.success(results)
        } catch (e: java.io.IOException) {
            failure(AppError.NoNetwork)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(AppError.Unknown(e))
        }
    }

    override suspend fun setManual(place: PlaceResult): StoredLocation {
        val loc = StoredLocation(
            lat = place.lat, lng = place.lng,
            city = place.name, region = place.region, country = place.country,
            zone = null,
            source = StoredLocation.Source.MANUAL,
            updatedAt = clock.now(),
        )
        settings.setAutoLocation(false)
        settings.setLocation(loc)
        return loc
    }

    private suspend fun geocode(block: (Geocoder, (List<Address>?, Throwable?) -> Unit) -> Unit): List<Address> {
        val g = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                block(g) { list, err ->
                    if (cont.isActive) {
                        if (err != null) cont.resumeWith(Result.failure(err)) else cont.resume(list.orEmpty())
                    }
                }
            }
        } else {
            withContext(io) {
                var out: List<Address> = emptyList()
                var error: Throwable? = null
                block(g) { list, err -> out = list.orEmpty(); error = err }
                error?.let { throw it }
                out
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun fromLocation(g: Geocoder, lat: Double, lng: Double, max: Int, cb: (List<Address>?, Throwable?) -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            g.getFromLocation(lat, lng, max, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) = cb(addresses, null)
                override fun onError(errorMessage: String?) = cb(null, java.io.IOException(errorMessage))
            })
        } else {
            try { cb(g.getFromLocation(lat, lng, max), null) } catch (e: Exception) { cb(null, e) }
        }
    }

    @Suppress("DEPRECATION")
    private fun fromName(g: Geocoder, q: String, max: Int, cb: (List<Address>?, Throwable?) -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            g.getFromLocationName(q, max, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) = cb(addresses, null)
                override fun onError(errorMessage: String?) = cb(null, java.io.IOException(errorMessage))
            })
        } else {
            try { cb(g.getFromLocationName(q, max), null) } catch (e: Exception) { cb(null, e) }
        }
    }

    companion object {
        const val SIX_HOURS = 6L * 3600 * 1000
    }
}
