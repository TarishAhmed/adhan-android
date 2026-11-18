package org.tarish.adhan.data.migration

import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tarish.adhan.data.local.SettingsKeys
import org.tarish.adhan.data.local.SettingsStore
import org.tarish.adhan.domain.model.AdhanSound
import org.tarish.adhan.domain.model.PrayerName
import timber.log.Timber
import java.io.File
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** §6.4 — one-time in-place migration from the Flutter 1.0.x app. */
@Singleton
class FlutterMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {

    suspend fun runIfNeeded() {
        if (settings.flutterMigrated()) return
        val values = step("prefs") {
            val all = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).all
            mapPrefs(all)
        } ?: emptyMap()
        step("databases") {
            context.deleteDatabase("prayer_timings.db")
            context.deleteDatabase("riverpod.db")
        }
        step("sounds") { migrateSounds(File(context.filesDir, "adhan")) }
        step("work") {
            val wm = WorkManager.getInstance(context)
            wm.cancelAllWork()
            wm.pruneWork()
        }
        step("channels") { deleteOldChannels() }
        settings.applyMigration(values)
    }

    private inline fun <T> step(name: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Timber.w(e, "Migration step %s failed", name); null
    }

    private fun deleteOldChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.notificationChannels.filter { it.id.endsWith("_prayer_channel") }.forEach { nm.deleteNotificationChannel(it.id) }
        nm.notificationChannelGroups.filter { it.id.endsWith("_prayer_channel_group") }
            .forEach { nm.deleteNotificationChannelGroup(it.id) }
    }

    companion object {
        private val DAY_NAMES = mapOf("Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6, "Sun" to 7)

        /** Pure mapping of FlutterSharedPreferences → §6.1 key/values. */
        fun mapPrefs(all: Map<String, Any?>): Map<String, Any> {
            val out = LinkedHashMap<String, Any>()
            fun get(k: String) = all["flutter.$k"]

            (get("onboarding_done_v1") as? Boolean)?.let { out["onboarding_done"] = it }
            (get("battery_opt_prompted_v1") as? Boolean)?.let { out["battery_prompt_shown"] = it }
            val auto = get("settings_auto_location") as? Boolean
            auto?.let { out["auto_location"] = it }

            val lat = get("stored_lat")?.toString()?.toDoubleOrNull()
            val lng = get("stored_lng")?.toString()?.toDoubleOrNull()
            if (lat != null && lng != null) {
                out["loc_lat"] = lat
                out["loc_lng"] = lng
                (get("stored_city") as? String)?.takeIf { it.isNotBlank() }?.let { out["loc_city"] = it }
                (get("stored_timezone") as? String)?.takeIf { it.isNotBlank() }?.let { out["loc_zone"] = it }
                out["loc_source"] = if (auto != false) "GPS" else "MANUAL"
                out["loc_updated_at"] = 0L
            }

            for (p in PrayerName.SALAH) {
                val k = p.key
                (get("notification_pref_$k") as? Boolean)?.let { out[SettingsKeys.enabled(p).name] = it }
                (get("notification_sound_$k") as? String)?.let { out[SettingsKeys.sound(p).name] = AdhanSound.idFromFlutter(it) }
                (get("notification_advance_$k") as? Number)?.let {
                    val v = ((it.toDouble().coerceIn(0.0, 30.0)) / 5.0).roundToInt() * 5
                    out[SettingsKeys.advance(p).name] = v
                }
                (get("notification_days_$k") as? String)?.let { s ->
                    val days = s.split(',').mapNotNull { DAY_NAMES[it.trim()] }.toSet()
                    out[SettingsKeys.days(p).name] = SettingsKeys.encodeDays(
                        (days.ifEmpty { (1..7).toSet() }).map { DayOfWeek.of(it) }.toSet()
                    )
                }
            }
            return out
        }

        fun migrateSounds(dir: File) {
            if (!dir.isDirectory) return
            listOf("fajr", "sunrise", "zuhr", "asr", "maghrib", "isha").forEach { File(dir, "$it.mp3").delete() }
            dir.listFiles { f -> f.name.endsWith(".mp3") }.orEmpty().forEach { f ->
                val id = AdhanSound.idFromFileName(f.name) ?: return@forEach
                val target = File(dir, "$id.mp3")
                if (!target.exists()) f.renameTo(target)
            }
        }
    }
}
