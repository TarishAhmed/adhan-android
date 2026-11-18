package org.tarish.adhan

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.tarish.adhan.alarm.NotificationChannels
import org.tarish.adhan.core.di.ApplicationScope
import org.tarish.adhan.data.migration.FlutterMigration
import org.tarish.adhan.work.WorkScheduler
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class AdhanApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var migration: FlutterMigration
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject lateinit var orchestrator: AppOrchestrator
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        } else {
            Timber.plant(ReleaseTree())
        }
        NotificationChannels.createAll(this)
        org.osmdroid.config.Configuration.getInstance().userAgentValue = "org.tarish.adhan/${BuildConfig.VERSION_NAME}"

        scope.launch {
            // Migration MUST complete before the first reschedule (§6.4).
            runCatching { migration.runIfNeeded() }.onFailure { Timber.e(it, "Migration failed") }
            runCatching { workScheduler.ensurePeriodic() }.onFailure { Timber.e(it, "ensurePeriodic failed") }
            orchestrator.start()
        }
    }

    /** Warnings and errors only; never coordinates or response bodies (§15.5). */
    private class ReleaseTree : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int) = priority >= android.util.Log.WARN
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            android.util.Log.println(priority, tag ?: "Adhan", message)
        }
    }
}
