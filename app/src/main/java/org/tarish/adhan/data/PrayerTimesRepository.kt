package org.tarish.adhan.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tarish.adhan.core.di.IoDispatcher
import org.tarish.adhan.core.format.formatCoord
import org.tarish.adhan.core.result.AppError
import org.tarish.adhan.core.result.failure
import org.tarish.adhan.core.result.toAppError
import org.tarish.adhan.core.time.Clock
import org.tarish.adhan.data.local.CachedMonthEntity
import org.tarish.adhan.data.local.PrayerDao
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.data.local.toDomain
import org.tarish.adhan.data.local.toEntity
import org.tarish.adhan.data.remote.AlislamApi
import org.tarish.adhan.data.remote.TimetableMapper
import org.tarish.adhan.domain.model.PrayerDay
import org.tarish.adhan.domain.model.StoredLocation
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface PrayerTimesRepository {
    /** Emits on DB change. */
    fun observeDays(key: String, from: LocalDate, to: LocalDate): Flow<List<PrayerDay>>
    suspend fun daysOnce(key: String, from: LocalDate, to: LocalDate): List<PrayerDay>
    suspend fun getDay(loc: StoredLocation, date: LocalDate, allowNetwork: Boolean = true): Result<PrayerDay>
    suspend fun ensureMonths(loc: StoredLocation, months: List<YearMonth>, force: Boolean = false): Result<Unit>
    /** Required coverage (§6.3 rule 3). */
    suspend fun ensureCoverage(loc: StoredLocation, force: Boolean = false): Result<Unit>
    /** Force current month (+ next if ≤ 7 days left). */
    suspend fun refreshCurrent(loc: StoredLocation): Result<Unit>
    suspend fun cleanup(currentKey: String?)
    suspend fun clearAll()
    suspend fun stats(): Pair<Int, Int>
    /** Emits after a month is written; observers reschedule alarms (§3.3). */
    val monthStored: SharedFlow<String>
}

@Singleton
class PrayerTimesRepositoryImpl @Inject constructor(
    private val api: AlislamApi,
    private val dao: PrayerDao,
    private val settings: SettingsStore,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PrayerTimesRepository {

    private val locks = ConcurrentHashMap<Pair<String, YearMonth>, Mutex>()
    private val _monthStored = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val monthStored: SharedFlow<String> = _monthStored

    override fun observeDays(key: String, from: LocalDate, to: LocalDate): Flow<List<PrayerDay>> =
        dao.range(key, from.toString(), to.toString()).map { list -> list.map { it.toDomain() } }

    override suspend fun daysOnce(key: String, from: LocalDate, to: LocalDate): List<PrayerDay> = withContext(io) {
        dao.rangeOnce(key, from.toString(), to.toString()).map { it.toDomain() }
    }

    override suspend fun getDay(loc: StoredLocation, date: LocalDate, allowNetwork: Boolean): Result<PrayerDay> = withContext(io) {
        dao.day(loc.key, date.toString())?.let { return@withContext Result.success(it.toDomain()) }
        if (!allowNetwork) return@withContext failure(AppError.NoNetwork)
        ensureMonths(loc, listOf(YearMonth.from(date))).getOrElse { return@withContext Result.failure(it) }
        dao.day(loc.key, date.toString())?.let { Result.success(it.toDomain()) } ?: failure(AppError.BadData)
    }

    private suspend fun isComplete(key: String, ym: YearMonth): Boolean {
        val m = dao.month(key, ym.year, ym.monthValue) ?: return false
        return m.dayCount >= ym.lengthOfMonth() - 1
    }

    override suspend fun ensureMonths(loc: StoredLocation, months: List<YearMonth>, force: Boolean): Result<Unit> = withContext(io) {
        var firstError: Throwable? = null
        for (ym in months.distinct()) {
            val lock = locks.getOrPut(loc.key to ym) { Mutex() }
            val r = lock.withLock {
                if (!force && isComplete(loc.key, ym)) Result.success(Unit) else fetchMonth(loc, ym)
            }
            if (r.isFailure && firstError == null) firstError = r.exceptionOrNull()
        }
        firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    private suspend fun fetchMonth(loc: StoredLocation, ym: YearMonth): Result<Unit> = try {
        val dto = api.month(formatCoord(loc.lat), formatCoord(loc.lng), ym.monthValue, ym.year)
        when (val out = TimetableMapper.map(dto, ym, loc.key, loc.zone ?: clock.deviceZone())) {
            TimetableMapper.Outcome.BadData -> failure(AppError.BadData)
            is TimetableMapper.Outcome.Ok -> {
                val now = clock.now().toEpochMilli()
                dao.writeMonth(
                    out.days.map { it.toEntity(now) },
                    CachedMonthEntity(loc.key, ym.year, ym.monthValue, out.zone.id, out.days.size, now),
                )
                settings.setLocationZone(loc.key, out.zone)
                _monthStored.tryEmit(loc.key)
                Result.success(Unit)
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w("Fetch %s failed: %s", ym, e.javaClass.simpleName)
        failure(e.toAppError())
    }

    override suspend fun ensureCoverage(loc: StoredLocation, force: Boolean): Result<Unit> =
        ensureMonths(loc, requiredMonths(loc.zone ?: clock.deviceZone()), force)

    fun requiredMonths(zone: ZoneId): List<YearMonth> = requiredMonths(clock.now().atZone(zone).toLocalDate())

    override suspend fun refreshCurrent(loc: StoredLocation): Result<Unit> {
        val today = clock.now().atZone(loc.zone ?: clock.deviceZone()).toLocalDate()
        val months = buildList {
            add(YearMonth.from(today))
            if (daysLeft(today) <= 7) add(YearMonth.from(today).plusMonths(1))
        }
        return ensureMonths(loc, months, force = true)
    }

    override suspend fun cleanup(currentKey: String?) = withContext(io) {
        val today = clock.now().atZone(clock.deviceZone()).toLocalDate()
        dao.deleteDaysBefore(today.minusDays(7).toString())
        val ym = YearMonth.from(today.minusDays(7))
        dao.deleteMonthsBefore(ym.year * 100 + ym.monthValue)
        val stale = dao.staleKeys(currentKey ?: "", clock.now().toEpochMilli() - 30L * 24 * 3600 * 1000)
        if (stale.isNotEmpty()) {
            dao.deleteDaysForKeys(stale)
            dao.deleteMonthsForKeys(stale)
        }
    }

    override suspend fun clearAll() = withContext(io) { dao.deleteAll() }

    override suspend fun stats(): Pair<Int, Int> = withContext(io) { dao.dayCount() to dao.locationCount() }

    companion object {
        fun daysLeft(today: LocalDate): Int = today.lengthOfMonth() - today.dayOfMonth

        /** Months containing yesterday, today, tomorrow, plus next month when ≤ 7 days remain (BUG-04/14). */
        fun requiredMonths(today: LocalDate): List<YearMonth> = buildList {
            add(YearMonth.from(today.minusDays(1)))
            add(YearMonth.from(today))
            add(YearMonth.from(today.plusDays(1)))
            if (daysLeft(today) <= 7) add(YearMonth.from(today).plusMonths(1))
        }.distinct()
    }
}
