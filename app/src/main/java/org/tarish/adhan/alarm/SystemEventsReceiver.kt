package org.tarish.adhan.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import org.tarish.adhan.core.di.ApplicationScope
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.widget.WidgetUpdater
import org.tarish.adhan.work.WorkScheduler

/** §10.7 — boot, package replaced, time/timezone changes, exact-alarm permission. */
@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: AlarmCoordinator
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject lateinit var settings: SettingsStore
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ALLOWED) return
        goAsync(scope, "systemEvent:$action") {
            if (action == Intent.ACTION_TIMEZONE_CHANGED && settings.autoLocation.first()) {
                settings.setLastGpsRefreshAt(0)
            }
            coordinator.rescheduleNow(action.substringAfterLast('.'))
            workScheduler.ensurePeriodic()
            widgetUpdater.updateAll()
        }
    }

    companion object {
        // Constant string is inlined; older releases simply never send it.
        @SuppressLint("InlinedApi")
        val ALLOWED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
