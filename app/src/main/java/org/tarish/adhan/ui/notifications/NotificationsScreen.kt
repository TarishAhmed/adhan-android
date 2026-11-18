package org.tarish.adhan.ui.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tarish.adhan.R
import org.tarish.adhan.alarm.NotificationChannels
import org.tarish.adhan.domain.model.AdhanSound
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerNotificationPrefs
import org.tarish.adhan.ui.advanced.BatterySheet
import org.tarish.adhan.ui.components.AdhanCard
import org.tarish.adhan.ui.components.LocalSnackbarHost
import org.tarish.adhan.ui.theme.Spacing
import org.tarish.adhan.ui.theme.tabular
import org.tarish.adhan.ui.util.clockText
import org.tarish.adhan.ui.util.findActivity
import org.tarish.adhan.ui.util.openChannelSettings
import org.tarish.adhan.ui.util.openExactAlarmSettings
import org.tarish.adhan.ui.util.openNotificationSettings
import org.tarish.adhan.ui.util.prayerName
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, vm: NotificationsViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val perms by vm.permissions.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var editing by rememberSaveable { mutableStateOf<PrayerName?>(null) }
    var battery by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPermissions()
        if (!it) ctx.openNotificationSettings()
    }

    val keepOne = stringResource(R.string.editor_keep_one_day)
    val testMsg = stringResource(R.string.editor_test_scheduled)
    LaunchedEffect(Unit) {
        vm.eventFlow.collect { e ->
            snackbar.currentSnackbarData?.dismiss()
            when (e) {
                NotifEvent.KeepOneDay -> snackbar.showSnackbar(keepOne)
                NotifEvent.TestScheduled -> snackbar.showSnackbar(testMsg)
                is NotifEvent.DownloadFailed -> snackbar.showSnackbar(ctx.getString(R.string.sound_download_failed_wifi, e.soundName))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (!perms.notificationsEnabled) item {
                Banner(Icons.Outlined.NotificationsOff, stringResource(R.string.notif_banner_blocked), stringResource(R.string.action_enable), error = true) {
                    val activity = ctx.findActivity()
                    val canAsk = Build.VERSION.SDK_INT >= 33 && activity != null
                    if (canAsk) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else ctx.openNotificationSettings()
                }
            }
            if (!perms.exactAllowed) item {
                Banner(Icons.Outlined.Alarm, stringResource(R.string.notif_banner_exact), stringResource(R.string.action_allow), error = true) {
                    ctx.openExactAlarmSettings()
                }
            }
            if (!perms.batteryOk) item {
                Banner(Icons.Outlined.BatteryAlert, stringResource(R.string.notif_banner_battery), stringResource(R.string.action_fix), error = false) {
                    battery = true
                }
            }
            items(perms.blockedChannels) { p ->
                Banner(Icons.Outlined.NotificationsOff, stringResource(R.string.notif_banner_channel_blocked, prayerName(p)), stringResource(R.string.action_open), error = true) {
                    ctx.openChannelSettings(NotificationChannels.prayerChannelId(p))
                }
            }
            items(PrayerName.SALAH) { p ->
                val pref = prefs[p] ?: PrayerNotificationPrefs(p)
                PrayerCard(
                    pref = pref,
                    time = today?.let { (day, zone) -> day[p]?.let { clockText(it, zone) } },
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.setEnabled(p, it)
                    },
                    onClick = { editing = p },
                )
            }
            item { Spacer(Modifier.size(Spacing.xxl)) }
        }
    }

    editing?.let { p ->
        PrayerEditorSheet(
            prayer = p,
            vm = vm,
            notificationsEnabled = perms.notificationsEnabled,
            onDismiss = { editing = null },
        )
    }
    if (battery) BatterySheet(onDismiss = { battery = false; vm.refreshPermissions() })
}

@Composable
private fun Banner(icon: ImageVector, text: String, action: String, error: Boolean, onAction: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = if (error) cs.errorContainer else cs.tertiaryContainer
    val fg = if (error) cs.onErrorContainer else cs.onTertiaryContainer
    AdhanCard(color = bg, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = fg)
            Spacer(Modifier.width(Spacing.md))
            Text(text, color = fg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.sm))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun PrayerCard(pref: PrayerNotificationPrefs, time: String?, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AdhanCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        prayerName(pref.prayer),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                        color = if (pref.enabled) cs.onSurface else cs.onSurfaceVariant,
                    )
                    if (time != null) {
                        Spacer(Modifier.width(Spacing.sm))
                        Text(time, style = MaterialTheme.typography.bodyMedium.tabular(), color = cs.onSurfaceVariant)
                    }
                }
                Text(
                    summary(pref),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Switch(checked = pref.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun summary(pref: PrayerNotificationPrefs): String {
    val sound = AdhanSound.byId(pref.soundId)?.displayName ?: ""
    val reminder = if (pref.advanceMinutes > 0) stringResource(R.string.notif_reminder_suffix, pref.advanceMinutes) else ""
    val days = daysSummary(pref.days)
    return sound + reminder + (days?.let { " • $it" } ?: "")
}

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKENDS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

@Composable
private fun daysSummary(days: Set<DayOfWeek>): String? = when (days) {
    DayOfWeek.entries.toSet() -> null
    WEEKDAYS -> stringResource(R.string.days_weekdays)
    WEEKENDS -> stringResource(R.string.days_weekends)
    else -> days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

