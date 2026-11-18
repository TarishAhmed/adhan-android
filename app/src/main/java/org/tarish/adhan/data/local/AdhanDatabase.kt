package org.tarish.adhan.data.local

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.tarish.adhan.domain.model.PrayerDay
import org.tarish.adhan.domain.model.PrayerName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Entity(tableName = "prayer_day", primaryKeys = ["location_key", "date"])
data class PrayerDayEntity(
    @ColumnInfo(name = "location_key") val locationKey: String,
    @ColumnInfo(name = "date") val date: String,
    val zone: String,
    val fajr: Long, val sunrise: Long, val zuhr: Long, val asr: Long,
    val sunset: Long?, val maghrib: Long, val isha: Long,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

@Entity(tableName = "cached_month", primaryKeys = ["location_key", "year", "month"])
data class CachedMonthEntity(
    @ColumnInfo(name = "location_key") val locationKey: String,
    val year: Int, val month: Int,
    val zone: String,
    @ColumnInfo(name = "day_count") val dayCount: Int,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

@Dao
interface PrayerDao {
    @Query("SELECT * FROM prayer_day WHERE location_key = :key AND date = :date")
    suspend fun day(key: String, date: String): PrayerDayEntity?

    @Query("SELECT * FROM prayer_day WHERE location_key = :key AND date BETWEEN :from AND :to ORDER BY date")
    fun range(key: String, from: String, to: String): Flow<List<PrayerDayEntity>>

    @Query("SELECT * FROM prayer_day WHERE location_key = :key AND date BETWEEN :from AND :to ORDER BY date")
    suspend fun rangeOnce(key: String, from: String, to: String): List<PrayerDayEntity>

    @Query("SELECT * FROM cached_month WHERE location_key = :key AND year = :y AND month = :m")
    suspend fun month(key: String, y: Int, m: Int): CachedMonthEntity?

    @Upsert suspend fun upsertDays(days: List<PrayerDayEntity>)
    @Upsert suspend fun upsertMonth(m: CachedMonthEntity)

    @Transaction
    suspend fun writeMonth(days: List<PrayerDayEntity>, m: CachedMonthEntity) {
        upsertDays(days); upsertMonth(m)
    }

    @Query("DELETE FROM prayer_day WHERE date < :before") suspend fun deleteDaysBefore(before: String)
    @Query("DELETE FROM cached_month WHERE (year * 100 + month) < :yyyymm") suspend fun deleteMonthsBefore(yyyymm: Int)
    @Query("DELETE FROM prayer_day WHERE location_key != :keep") suspend fun deleteOtherLocations(keep: String)

    /** Keys other than [keep] whose newest fetch is older than [olderThan] (epoch ms). */
    @Query("SELECT location_key FROM prayer_day WHERE location_key != :keep GROUP BY location_key HAVING MAX(fetched_at) < :olderThan")
    suspend fun staleKeys(keep: String, olderThan: Long): List<String>

    @Query("DELETE FROM prayer_day WHERE location_key IN (:keys)") suspend fun deleteDaysForKeys(keys: List<String>)
    @Query("DELETE FROM cached_month WHERE location_key IN (:keys)") suspend fun deleteMonthsForKeys(keys: List<String>)

    @Query("DELETE FROM prayer_day") suspend fun deleteAllDays()
    @Query("DELETE FROM cached_month") suspend fun deleteAllMonths()

    @Transaction
    suspend fun deleteAll() { deleteAllDays(); deleteAllMonths() }

    @Query("SELECT COUNT(*) FROM prayer_day") suspend fun dayCount(): Int
    @Query("SELECT COUNT(DISTINCT location_key) FROM prayer_day") suspend fun locationCount(): Int
}

@Database(
    entities = [PrayerDayEntity::class, CachedMonthEntity::class, TranslationVerseEntity::class, BookmarkEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AdhanDatabase : RoomDatabase() {
    abstract fun prayerDao(): PrayerDao
    abstract fun quranDao(): QuranDao

    companion object { const val NAME = "adhan.db" }
}

fun PrayerDay.toEntity(fetchedAt: Long) = PrayerDayEntity(
    locationKey = locationKey,
    date = date.toString(),
    zone = zone.id,
    fajr = times.getValue(PrayerName.FAJR).toEpochMilli(),
    sunrise = times.getValue(PrayerName.SUNRISE).toEpochMilli(),
    zuhr = times.getValue(PrayerName.ZUHR).toEpochMilli(),
    asr = times.getValue(PrayerName.ASR).toEpochMilli(),
    sunset = times[PrayerName.SUNSET]?.toEpochMilli(),
    maghrib = times.getValue(PrayerName.MAGHRIB).toEpochMilli(),
    isha = times.getValue(PrayerName.ISHA).toEpochMilli(),
    fetchedAt = fetchedAt,
)

fun PrayerDayEntity.toDomain() = PrayerDay(
    locationKey = locationKey,
    date = LocalDate.parse(date),
    zone = ZoneId.of(zone),
    times = buildMap {
        put(PrayerName.FAJR, Instant.ofEpochMilli(fajr))
        put(PrayerName.SUNRISE, Instant.ofEpochMilli(sunrise))
        put(PrayerName.ZUHR, Instant.ofEpochMilli(zuhr))
        put(PrayerName.ASR, Instant.ofEpochMilli(asr))
        sunset?.let { put(PrayerName.SUNSET, Instant.ofEpochMilli(it)) }
        put(PrayerName.MAGHRIB, Instant.ofEpochMilli(maghrib))
        put(PrayerName.ISHA, Instant.ofEpochMilli(isha))
    },
)
