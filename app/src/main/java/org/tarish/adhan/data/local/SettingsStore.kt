package org.tarish.adhan.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.tarish.adhan.domain.model.AdhanSound
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerNotificationPrefs
import org.tarish.adhan.domain.model.StoredLocation
import org.tarish.adhan.domain.model.ThemeMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsStore {
    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone()
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(m: ThemeMode)
    val autoLocation: Flow<Boolean>
    suspend fun setAutoLocation(v: Boolean)
    val location: Flow<StoredLocation?>
    suspend fun setLocation(l: StoredLocation?)
    suspend fun setLocationZone(key: String, zone: ZoneId)
    val prayerPrefs: Flow<Map<PrayerName, PrayerNotificationPrefs>>
    suspend fun updatePrayerPrefs(p: PrayerName, transform: (PrayerNotificationPrefs) -> PrayerNotificationPrefs)

    val batteryPromptShown: Flow<Boolean>
    suspend fun setBatteryPromptShown()
    val exactAlarmPromptShown: Flow<Boolean>
    suspend fun setExactAlarmPromptShown()
    val lastSyncAt: Flow<Long>
    suspend fun setLastSyncAt(ms: Long)
    suspend fun lastGpsRefreshAt(): Long
    suspend fun setLastGpsRefreshAt(ms: Long)
    suspend fun scheduledAlarmKeys(): Set<Int>
    suspend fun setScheduledAlarmKeys(keys: Set<Int>)
    suspend fun flutterMigrated(): Boolean
    suspend fun applyMigration(values: Map<String, Any>)
}

object SettingsKeys {
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val BATTERY_PROMPT_SHOWN = booleanPreferencesKey("battery_prompt_shown")
    val EXACT_ALARM_PROMPT_SHOWN = booleanPreferencesKey("exact_alarm_prompt_shown")
    val AUTO_LOCATION = booleanPreferencesKey("auto_location")
    val LOC_LAT = doublePreferencesKey("loc_lat")
    val LOC_LNG = doublePreferencesKey("loc_lng")
    val LOC_CITY = stringPreferencesKey("loc_city")
    val LOC_REGION = stringPreferencesKey("loc_region")
    val LOC_COUNTRY = stringPreferencesKey("loc_country")
    val LOC_ZONE = stringPreferencesKey("loc_zone")
    val LOC_SOURCE = stringPreferencesKey("loc_source")
    val LOC_UPDATED_AT = longPreferencesKey("loc_updated_at")
    val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    val LAST_GPS_REFRESH_AT = longPreferencesKey("last_gps_refresh_at")
    val SCHEDULED_ALARM_KEYS = stringSetPreferencesKey("scheduled_alarm_keys")
    val FLUTTER_MIGRATED = booleanPreferencesKey("flutter_migrated")

    fun enabled(p: PrayerName) = booleanPreferencesKey("notif_${p.key}_enabled")
    fun sound(p: PrayerName) = stringPreferencesKey("notif_${p.key}_sound")
    fun advance(p: PrayerName) = intPreferencesKey("notif_${p.key}_advance")
    fun days(p: PrayerName) = stringPreferencesKey("notif_${p.key}_days")

    fun encodeDays(days: Set<DayOfWeek>) = days.map { it.value }.sorted().joinToString(",")
    fun decodeDays(s: String?): Set<DayOfWeek> {
        val set = s?.split(',')?.mapNotNull { it.trim().toIntOrNull()?.takeIf { v -> v in 1..7 } }
            ?.map { DayOfWeek.of(it) }?.toSet().orEmpty()
        return set.ifEmpty { DayOfWeek.entries.toSet() }
    }
}

@Singleton
class DataStoreSettingsStore @Inject constructor(
    private val ds: DataStore<Preferences>,
) : SettingsStore {
    private val k = SettingsKeys

    private fun <T> pref(map: (Preferences) -> T): Flow<T> = ds.data.map(map).distinctUntilChanged()

    override val onboardingDone = pref { it[k.ONBOARDING_DONE] ?: false }
    override suspend fun setOnboardingDone() { ds.edit { it[k.ONBOARDING_DONE] = true } }

    override val themeMode = pref { ThemeMode.fromKey(it[k.THEME_MODE]) }
    override suspend fun setThemeMode(m: ThemeMode) { ds.edit { it[k.THEME_MODE] = m.key } }

    override val autoLocation = pref { it[k.AUTO_LOCATION] ?: true }
    override suspend fun setAutoLocation(v: Boolean) { ds.edit { it[k.AUTO_LOCATION] = v } }

    override val location: Flow<StoredLocation?> = pref { p ->
        val lat = p[k.LOC_LAT] ?: return@pref null
        val lng = p[k.LOC_LNG] ?: return@pref null
        StoredLocation(
            lat = lat, lng = lng,
            city = p[k.LOC_CITY], region = p[k.LOC_REGION], country = p[k.LOC_COUNTRY],
            zone = p[k.LOC_ZONE]?.let { runCatching { ZoneId.of(it) }.getOrNull() },
            source = if (p[k.LOC_SOURCE] == "MANUAL") StoredLocation.Source.MANUAL else StoredLocation.Source.GPS,
            updatedAt = Instant.ofEpochMilli(p[k.LOC_UPDATED_AT] ?: 0L),
        )
    }

    /** Always writes all fields; null clears (BUG-65). */
    override suspend fun setLocation(l: StoredLocation?) {
        ds.edit { p ->
            if (l == null) {
                listOf(k.LOC_LAT, k.LOC_LNG).forEach { p.remove(it) }
                listOf(k.LOC_CITY, k.LOC_REGION, k.LOC_COUNTRY, k.LOC_ZONE, k.LOC_SOURCE).forEach { p.remove(it) }
                p.remove(k.LOC_UPDATED_AT)
                return@edit
            }
            p[k.LOC_LAT] = l.lat
            p[k.LOC_LNG] = l.lng
            p.setOrRemove(k.LOC_CITY, l.city)
            p.setOrRemove(k.LOC_REGION, l.region)
            p.setOrRemove(k.LOC_COUNTRY, l.country)
            p.setOrRemove(k.LOC_ZONE, l.zone?.id)
            p[k.LOC_SOURCE] = l.source.name
            p[k.LOC_UPDATED_AT] = l.updatedAt.toEpochMilli()
        }
    }

    /** Only applies when the stored location still has [key] (avoids a late fetch overwriting a newer location). */
    override suspend fun setLocationZone(key: String, zone: ZoneId) {
        ds.edit { p ->
            val lat = p[k.LOC_LAT] ?: return@edit
            val lng = p[k.LOC_LNG] ?: return@edit
            if (org.tarish.adhan.core.format.locationKey(lat, lng) == key) p[k.LOC_ZONE] = zone.id
        }
    }

    private fun prefsFrom(p: Preferences, prayer: PrayerName) = PrayerNotificationPrefs(
        prayer = prayer,
        enabled = p[k.enabled(prayer)] ?: true,
        soundId = p[k.sound(prayer)]?.takeIf { AdhanSound.byId(it) != null } ?: AdhanSound.DEFAULT_ID,
        advanceMinutes = (p[k.advance(prayer)] ?: 0).coerceIn(0, 30),
        days = SettingsKeys.decodeDays(p[k.days(prayer)]),
    )

    override val prayerPrefs = pref { p -> PrayerName.SALAH.associateWith { prefsFrom(p, it) } }

    override suspend fun updatePrayerPrefs(p: PrayerName, transform: (PrayerNotificationPrefs) -> PrayerNotificationPrefs) {
        ds.edit { m ->
            val new = transform(prefsFrom(m, p))
            m[k.enabled(p)] = new.enabled
            m[k.sound(p)] = new.soundId
            m[k.advance(p)] = new.advanceMinutes.coerceIn(0, 30)
            m[k.days(p)] = SettingsKeys.encodeDays(new.days.ifEmpty { DayOfWeek.entries.toSet() })
        }
    }

    override val batteryPromptShown = pref { it[k.BATTERY_PROMPT_SHOWN] ?: false }
    override suspend fun setBatteryPromptShown() { ds.edit { it[k.BATTERY_PROMPT_SHOWN] = true } }
    override val exactAlarmPromptShown = pref { it[k.EXACT_ALARM_PROMPT_SHOWN] ?: false }
    override suspend fun setExactAlarmPromptShown() { ds.edit { it[k.EXACT_ALARM_PROMPT_SHOWN] = true } }
    override val lastSyncAt = pref { it[k.LAST_SYNC_AT] ?: 0L }
    override suspend fun setLastSyncAt(ms: Long) { ds.edit { it[k.LAST_SYNC_AT] = ms } }
    override suspend fun lastGpsRefreshAt(): Long = ds.data.first()[k.LAST_GPS_REFRESH_AT] ?: 0L
    override suspend fun setLastGpsRefreshAt(ms: Long) { ds.edit { it[k.LAST_GPS_REFRESH_AT] = ms } }
    override suspend fun scheduledAlarmKeys(): Set<Int> =
        ds.data.first()[k.SCHEDULED_ALARM_KEYS].orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
    override suspend fun setScheduledAlarmKeys(keys: Set<Int>) {
        ds.edit { it[k.SCHEDULED_ALARM_KEYS] = keys.map { c -> c.toString() }.toSet() }
    }
    override suspend fun flutterMigrated(): Boolean = ds.data.first()[k.FLUTTER_MIGRATED] ?: false

    /** Writes the values produced by FlutterMigration (keys are §6.1 names) and sets flutter_migrated. */
    override suspend fun applyMigration(values: Map<String, Any>) {
        ds.edit { p ->
            for ((name, v) in values) {
                when (v) {
                    is Boolean -> p[booleanPreferencesKey(name)] = v
                    is Int -> p[intPreferencesKey(name)] = v
                    is Long -> p[longPreferencesKey(name)] = v
                    is Double -> p[doublePreferencesKey(name)] = v
                    is String -> p[stringPreferencesKey(name)] = v
                }
            }
            p[k.FLUTTER_MIGRATED] = true
        }
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, v: T?) {
        if (v == null) remove(key) else this[key] = v
    }
}
