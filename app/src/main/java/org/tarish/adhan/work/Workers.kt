package org.tarish.adhan.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import org.tarish.adhan.alarm.AlarmCoordinator
import org.tarish.adhan.core.time.Clock
import org.tarish.adhan.data.DownloadState
import org.tarish.adhan.data.PrayerTimesRepository
import org.tarish.adhan.data.SoundRepository
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.data.quran.QuranRepository
import org.tarish.adhan.domain.quran.TranslationEdition
import org.tarish.adhan.widget.WidgetUpdater
import timber.log.Timber

/** Daily timetable sync + self-healing reschedule (§11). */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsStore,
    private val prayerRepo: PrayerTimesRepository,
    private val coordinator: AlarmCoordinator,
    private val widgetUpdater: WidgetUpdater,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val loc = settings.location.first()
        var ok = true
        if (loc != null) {
            val r = prayerRepo.ensureCoverage(loc)
            ok = r.isSuccess
            if (ok) settings.setLastSyncAt(clock.now().toEpochMilli())
        }
        coordinator.rescheduleNow("sync")
        widgetUpdater.updateAll()
        when {
            ok -> Result.success()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    } catch (e: Exception) {
        Timber.e(e, "SyncWorker failed")
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

/** Weekly cleanup (§6.3 rule 6). */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsStore,
    private val prayerRepo: PrayerTimesRepository,
    private val sounds: SoundRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        prayerRepo.cleanup(settings.location.first()?.key)
        sounds.deleteOrphanParts()
        WorkManager.getInstance(applicationContext).pruneWork()
        Result.success()
    } catch (e: Exception) {
        Timber.e(e, "MaintenanceWorker failed"); Result.success()
    }
}

/** Widget refresh only — no network constraint (BUG-46). */
@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val widgetUpdater: WidgetUpdater,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { widgetUpdater.updateAll() }.onFailure { Timber.e(it, "Widget refresh failed") }
        return Result.success()
    }
}

@HiltWorker
class SoundDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sounds: SoundRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        return when (sounds.download(id).last()) {
            DownloadState.Done -> Result.success()
            else -> if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object { const val KEY_ID = "id" }
}

/** Caches a whole translation for offline reading, surah by surah; resumable since cached pages are skipped. */
@HiltWorker
class QuranDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val quran: QuranRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val edition = TranslationEdition.fromKey(inputData.getString(KEY_EDITION))
        val surahs = quran.surahs()
        surahs.forEachIndexed { i, s ->
            setProgress(workDataOf(KEY_DONE to i, KEY_TOTAL to surahs.size))
            if (quran.ensureTranslation(s.number, edition).isFailure) {
                return if (runAttemptCount < 5) Result.retry() else Result.failure()
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_EDITION = "edition"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
    }
}
