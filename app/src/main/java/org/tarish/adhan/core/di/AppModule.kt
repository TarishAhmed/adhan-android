package org.tarish.adhan.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.tarish.adhan.BuildConfig
import org.tarish.adhan.alarm.AlarmCoordinator
import org.tarish.adhan.alarm.DefaultAlarmCoordinator
import org.tarish.adhan.core.time.Clock
import org.tarish.adhan.core.time.SystemClock
import org.tarish.adhan.data.GitHubUpdateRepository
import org.tarish.adhan.data.LocationRepository
import org.tarish.adhan.data.LocationRepositoryImpl
import org.tarish.adhan.data.PrayerTimesRepository
import org.tarish.adhan.data.PrayerTimesRepositoryImpl
import org.tarish.adhan.data.SoundRepository
import org.tarish.adhan.data.SoundRepositoryImpl
import org.tarish.adhan.data.UpdateRepository
import org.tarish.adhan.data.local.AdhanDatabase
import org.tarish.adhan.data.local.DataStoreSettingsStore
import org.tarish.adhan.data.local.PrayerDao
import org.tarish.adhan.data.local.QuranDao
import org.tarish.adhan.data.quran.QuranRepository
import org.tarish.adhan.data.quran.QuranRepositoryImpl
import org.tarish.adhan.data.quran.ReadQuranTranslationSource
import org.tarish.adhan.data.remote.ReadQuranApi
import org.tarish.adhan.domain.quran.TranslationSource
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.data.remote.AlislamApi
import org.tarish.adhan.widget.GlanceWidgetUpdater
import org.tarish.adhan.widget.WidgetUpdater
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton fun clock(): Clock = SystemClock

    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun dataStore(@ApplicationContext c: Context): DataStore<Preferences> = c.settingsDataStore

    @Provides @Singleton
    fun db(@ApplicationContext c: Context): AdhanDatabase =
        Room.databaseBuilder(c, AdhanDatabase::class.java, AdhanDatabase.NAME).build()

    @Provides fun dao(db: AdhanDatabase): PrayerDao = db.prayerDao()

    @Provides fun quranDao(db: AdhanDatabase): QuranDao = db.quranDao()

    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Provides @Singleton
    fun okHttp(): OkHttpClient {
        val ua = Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Adhan-Android/${BuildConfig.VERSION_NAME}").build())
        }
        // One automatic retry on IOException with 2 s backoff; no retry on HTTP errors (§5.1.1).
        val retry = Interceptor { chain ->
            try {
                chain.proceed(chain.request())
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                Thread.sleep(2_000)
                chain.proceed(chain.request())
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(ua)
            .addInterceptor(retry)
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
            .build()
    }

    @Provides @Singleton
    fun api(client: OkHttpClient, json: Json): AlislamApi = Retrofit.Builder()
        .baseUrl(AlislamApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AlislamApi::class.java)

    @Provides @Singleton
    fun readQuranApi(client: OkHttpClient, json: Json): ReadQuranApi = Retrofit.Builder()
        .baseUrl(ReadQuranApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ReadQuranApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun settings(impl: DataStoreSettingsStore): SettingsStore
    @Binds abstract fun prayerRepo(impl: PrayerTimesRepositoryImpl): PrayerTimesRepository
    @Binds abstract fun locationRepo(impl: LocationRepositoryImpl): LocationRepository
    @Binds abstract fun soundRepo(impl: SoundRepositoryImpl): SoundRepository
    @Binds abstract fun updateRepo(impl: GitHubUpdateRepository): UpdateRepository
    @Binds abstract fun coordinator(impl: DefaultAlarmCoordinator): AlarmCoordinator
    @Binds abstract fun widgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater
    @Binds abstract fun quranRepo(impl: QuranRepositoryImpl): QuranRepository
    @Binds abstract fun translationSource(impl: ReadQuranTranslationSource): TranslationSource
}
