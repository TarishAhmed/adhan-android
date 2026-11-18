package org.tarish.adhan.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tarish.adhan.Fixtures
import org.tarish.adhan.core.result.appError
import org.tarish.adhan.core.result.AppError
import org.tarish.adhan.core.time.FixedClock
import org.tarish.adhan.data.local.AdhanDatabase
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.data.remote.AlislamApi
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerNotificationPrefs
import org.tarish.adhan.domain.model.StoredLocation
import org.tarish.adhan.domain.model.ThemeMode
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrayerTimesRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var db: AdhanDatabase
    private lateinit var repo: PrayerTimesRepositoryImpl
    private val fixture = javaClass.classLoader!!.getResource("fixtures/london_2026_11.json")!!.readText()
    private val clock = FixedClock(Instant.ofEpochMilli(1793563200000), ZoneId.of("Europe/London"))  // Nov 1 2026 20:00
    private val loc = StoredLocation(51.5074, -0.1278, "London", null, "UK", ZoneId.of("Europe/London"),
        StoredLocation.Source.GPS, Instant.EPOCH)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AdhanDatabase::class.java)
            .allowMainThreadQueries().build()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(AlislamApi::class.java)
        repo = PrayerTimesRepositoryImpl(api, db.prayerDao(), FakeSettings(), clock, Dispatchers.IO)
    }

    @After fun tearDown() { db.close(); server.shutdown() }

    private fun ok() = MockResponse().setResponseCode(200).setBody(fixture).setHeader("Content-Type", "application/json")

    @Test fun cacheFirst_thenNoSecondRequest() = runBlocking {
        server.enqueue(ok())
        assertThat(repo.ensureMonths(loc, listOf(YearMonth.of(2026, 11))).isSuccess).isTrue()
        assertThat(repo.ensureMonths(loc, listOf(YearMonth.of(2026, 11))).isSuccess).isTrue()
        assertThat(server.requestCount).isEqualTo(1)
        val req = server.takeRequest()
        assertThat(req.requestUrl!!.queryParameter("lat")).isEqualTo("51.51")
        assertThat(req.requestUrl!!.queryParameter("lng")).isEqualTo("-0.13")
        val day = repo.getDay(loc, LocalDate.of(2026, 11, 2), allowNetwork = false).getOrThrow()
        assertThat(day[PrayerName.FAJR]!!.toEpochMilli()).isEqualTo(Fixtures.NOV2[PrayerName.FAJR])
    }

    @Test fun forceRefresh_failure_keepsCache() = runBlocking {
        server.enqueue(ok())
        repo.ensureMonths(loc, listOf(YearMonth.of(2026, 11)))
        server.enqueue(MockResponse().setResponseCode(500))
        val r = repo.ensureMonths(loc, listOf(YearMonth.of(2026, 11)), force = true)
        assertThat(r.appError).isEqualTo(AppError.Http(500))
        assertThat(repo.stats().first).isEqualTo(30)
    }

    @Test fun perMonthMutex_concurrentCallsMakeOneRequest() = runBlocking {
        server.enqueue(ok().setBodyDelay(200, java.util.concurrent.TimeUnit.MILLISECONDS))
        val a = async(Dispatchers.IO) { repo.ensureMonths(loc, listOf(YearMonth.of(2026, 11))) }
        val b = async(Dispatchers.IO) { repo.ensureMonths(loc, listOf(YearMonth.of(2026, 11))) }
        assertThat(a.await().isSuccess && b.await().isSuccess).isTrue()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun requiredMonths_includesNextWhenSevenOrFewerDaysLeft() {
        assertThat(PrayerTimesRepositoryImpl.requiredMonths(LocalDate.of(2026, 11, 16)))
            .containsExactly(YearMonth.of(2026, 11))
        assertThat(PrayerTimesRepositoryImpl.requiredMonths(LocalDate.of(2026, 11, 23)))
            .containsExactly(YearMonth.of(2026, 11), YearMonth.of(2026, 12))
        assertThat(PrayerTimesRepositoryImpl.requiredMonths(LocalDate.of(2026, 12, 1)))
            .containsExactly(YearMonth.of(2026, 11), YearMonth.of(2026, 12))
        assertThat(PrayerTimesRepositoryImpl.requiredMonths(LocalDate.of(2026, 12, 31)))
            .containsExactly(YearMonth.of(2026, 12), YearMonth.of(2027, 1))
    }

    private class FakeSettings : SettingsStore {
        override val onboardingDone: Flow<Boolean> = flowOf(true)
        override suspend fun setOnboardingDone() {}
        override val themeMode: Flow<ThemeMode> = flowOf(ThemeMode.SYSTEM)
        override suspend fun setThemeMode(m: ThemeMode) {}
        override val autoLocation: Flow<Boolean> = flowOf(true)
        override suspend fun setAutoLocation(v: Boolean) {}
        override val location = MutableStateFlow<StoredLocation?>(null)
        override suspend fun setLocation(l: StoredLocation?) { location.value = l }
        override suspend fun setLocationZone(key: String, zone: ZoneId) {}
        override val prayerPrefs: Flow<Map<PrayerName, PrayerNotificationPrefs>> = emptyFlow()
        override suspend fun updatePrayerPrefs(p: PrayerName, transform: (PrayerNotificationPrefs) -> PrayerNotificationPrefs) {}
        override val batteryPromptShown: Flow<Boolean> = flowOf(false)
        override suspend fun setBatteryPromptShown() {}
        override val exactAlarmPromptShown: Flow<Boolean> = flowOf(false)
        override suspend fun setExactAlarmPromptShown() {}
        override val lastSyncAt: Flow<Long> = flowOf(0)
        override suspend fun setLastSyncAt(ms: Long) {}
        override suspend fun lastGpsRefreshAt() = 0L
        override suspend fun setLastGpsRefreshAt(ms: Long) {}
        override suspend fun scheduledAlarmKeys() = emptySet<Int>()
        override suspend fun setScheduledAlarmKeys(keys: Set<Int>) {}
        override suspend fun flutterMigrated() = true
        override suspend fun applyMigration(values: Map<String, Any>) {}
    }
}
