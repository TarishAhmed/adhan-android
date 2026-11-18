package org.tarish.adhan.ui.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import org.tarish.adhan.R
import timber.log.Timber

/** Fire-and-catch: never pre-check with resolveActivity (BUG-32). */
fun Context.tryStart(vararg intents: Intent): Boolean {
    for (i in intents) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
        } catch (e: ActivityNotFoundException) {
            Timber.w("No activity for %s", i)
        } catch (e: SecurityException) {
            Timber.w("Not allowed: %s", i)
        }
    }
    return false
}

fun Context.packageUri(): Uri = "package:$packageName".toUri()

fun Context.appDetailsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

fun Context.openUpdateUrl(url: String) {
    val intents = buildList {
        if (url.isNotBlank()) add(Intent(Intent.ACTION_VIEW, url.toUri()))
        add(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()))
        add(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
    }
    tryStart(*intents.toTypedArray())
}

fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: true

fun Context.openNotificationSettings() {
    tryStart(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        appDetailsIntent(),
    )
}

fun Context.openChannelSettings(channelId: String) {
    tryStart(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId),
        appDetailsIntent(),
    )
}

fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT >= 31) {
        tryStart(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri()), appDetailsIntent())
    }
}

/** OEM shortcut for the battery sheet (§8.10.3). */
data class OemShortcut(val labelRes: Int, val intents: List<Intent>)

object BatteryIntents {
    @android.annotation.SuppressLint("BatteryLife")
    fun requestIgnore(ctx: Context) = ctx.tryStart(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, ctx.packageUri()),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        ctx.appDetailsIntent(),
    )

    fun openSettings(ctx: Context) = ctx.tryStart(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        ctx.appDetailsIntent(),
    )

    private fun comp(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    fun oemShortcuts(ctx: Context): List<OemShortcut> {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        val details = ctx.appDetailsIntent()
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in m } -> listOf(
                OemShortcut(R.string.battery_oem_autostart, listOf(comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"), details)),
                OemShortcut(R.string.battery_oem_saver, listOf(
                    comp("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                        .putExtra("package_name", ctx.packageName)
                        .putExtra("package_label", ctx.applicationInfo.loadLabel(ctx.packageManager).toString()),
                    details,
                )),
            )
            listOf("huawei", "honor").any { it in m } -> listOf(
                OemShortcut(R.string.battery_oem_launch, listOf(
                    comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    comp("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                    details,
                )),
            )
            "samsung" in m -> listOf(
                OemShortcut(R.string.battery_oem_sleeping, listOf(
                    Intent("android.settings.APP_BATTERY_SETTINGS"),
                    comp("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    details,
                )),
            )
            listOf("oneplus", "oppo", "realme").any { it in m } -> listOf(OemShortcut(R.string.battery_oem_autolaunch, listOf(details)))
            listOf("vivo", "iqoo").any { it in m } -> listOf(
                OemShortcut(R.string.battery_oem_background, listOf(
                    comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    details,
                )),
            )
            else -> emptyList()
        }
    }
}
