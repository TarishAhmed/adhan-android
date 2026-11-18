package org.tarish.adhan.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tarish.adhan.domain.quran.TranslationEdition
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** §11 — enqueue helpers. */
@Singleton
class WorkScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensurePeriodic() {
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(LocalTime.of(3, 0))
        if (!at.isAfter(now)) at = at.plusDays(1)
        val initial = Duration.between(now, at).toMinutes()

        wm.enqueueUniquePeriodicWork(
            SYNC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS, 6, TimeUnit.HOURS)
                .setInitialDelay(initial, TimeUnit.MINUTES)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            MAINTENANCE, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            WIDGET, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    fun syncNow() {
        wm.enqueueUniqueWork(
            SYNC_NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    fun downloadSound(id: String) {
        wm.enqueueUniqueWork(
            "sound_download_$id", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SoundDownloadWorker>()
                .setInputData(workDataOf(SoundDownloadWorker.KEY_ID to id))
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    fun downloadQuran(edition: TranslationEdition) {
        wm.enqueueUniqueWork(
            QURAN_DOWNLOAD, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<QuranDownloadWorker>()
                .setInputData(workDataOf(QuranDownloadWorker.KEY_EDITION to edition.key))
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    companion object {
        const val QURAN_DOWNLOAD = "quran_download"
        const val SYNC = "sync_timetable"
        const val MAINTENANCE = "maintenance"
        const val WIDGET = "widget_refresh"
        const val SYNC_NOW = "sync_now"
    }
}
