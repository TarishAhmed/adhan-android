package org.tarish.adhan.ui.settings

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tarish.adhan.BuildConfig
import org.tarish.adhan.R
import org.tarish.adhan.data.UpdateDownload
import org.tarish.adhan.data.UpdateInfo
import org.tarish.adhan.ui.util.openUpdateUrl
import org.tarish.adhan.domain.AlarmKind
import org.tarish.adhan.domain.model.ThemeMode
import org.tarish.adhan.ui.advanced.BatterySheet
import org.tarish.adhan.ui.components.LocalAppSnackbar
import org.tarish.adhan.ui.components.SectionHeader
import org.tarish.adhan.ui.components.SettingRow
import org.tarish.adhan.ui.theme.Spacing
import org.tarish.adhan.ui.util.openExactAlarmSettings
import org.tarish.adhan.ui.util.prayerName
import org.tarish.adhan.ui.util.rememberIs24h
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every setting in one list. Only per-prayer notifications and location push deeper; everything
 * else is changed in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNotifications: () -> Unit,
    onLocation: () -> Unit,
    quranSection: @Composable () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val summary by vm.summary.collectAsStateWithLifecycle()
    val batteryOk by vm.batteryOk.collectAsStateWithLifecycle()
    val exactOk by vm.exactOk.collectAsStateWithLifecycle()
    var battery by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { vm.refreshStatus(); onPauseOrDispose { } }
    LaunchedEffect(advanced) { if (advanced) { vm.loadCache(); vm.loadPlan() } }
    LaunchedEffect(Unit) {
        vm.eventFlow.collect {
            snackbar.show(ctx.getString(when (it) {
                SettingsEvent.REFRESHED -> R.string.home_refresh_success
                SettingsEvent.REFRESH_FAILED -> R.string.home_refresh_failure
                SettingsEvent.TIMES_CLEARED -> R.string.settings_times_cleared
                SettingsEvent.SOUNDS_DELETED -> R.string.settings_sounds_deleted
            }))
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        val cs = MaterialTheme.colorScheme
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(stringResource(R.string.more_section_prayer)) }
            item {
                SettingRow(
                    title = stringResource(R.string.more_notifications),
                    icon = Icons.Outlined.Notifications,
                    subtitle = pluralStringResource(R.plurals.settings_adhans_on, summary.adhansOn, summary.adhansOn),
                    onClick = onNotifications,
                )
            }
            item {
                SettingRow(
                    title = stringResource(R.string.more_location),
                    icon = Icons.Outlined.LocationOn,
                    subtitle = summary.locationLabel ?: stringResource(R.string.location_chip_placeholder),
                    onClick = onLocation,
                )
            }

            item { quranSection() }

            item { SectionHeader(stringResource(R.string.more_section_appearance)) }
            item { ThemePicker(themeMode, onThemeChange) }

            item { SectionHeader(stringResource(R.string.settings_section_reliability)) }
            item {
                SettingRow(
                    title = stringResource(R.string.adv_battery),
                    icon = Icons.Outlined.BatteryChargingFull,
                    subtitle = stringResource(if (batteryOk) R.string.adv_battery_ok else R.string.adv_battery_bad),
                    subtitleColor = if (batteryOk) cs.primary else cs.error,
                    onClick = { battery = true },
                )
            }
            if (Build.VERSION.SDK_INT >= 31) item {
                SettingRow(
                    title = stringResource(R.string.adv_exact),
                    icon = Icons.Outlined.Alarm,
                    subtitle = stringResource(if (exactOk) R.string.adv_exact_ok else R.string.adv_exact_bad),
                    subtitleColor = if (exactOk) cs.primary else cs.error,
                    onClick = if (exactOk) null else ({ ctx.openExactAlarmSettings() }),
                )
            }

            item { AdvancedHeader(advanced) { advanced = !advanced } }
            item {
                AnimatedVisibility(advanced) { AdvancedSection(vm) }
            }

            item { SectionHeader(stringResource(R.string.more_section_about)) }
            item { UpdatesRow(vm) }
            item { SettingRow(stringResource(R.string.more_about), icon = Icons.Outlined.Info, onClick = { about = true }) }
            item {
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxxl),
                )
            }
        }
    }

    if (battery) BatterySheet { battery = false; vm.refreshStatus() }
    if (about) AboutDialog { about = false }
}

/** Current version and update status; taps check GitHub or install the newer release. */
@Composable
private fun UpdatesRow(vm: SettingsViewModel) {
    val s by vm.updates.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val available = s.info.type != UpdateInfo.Type.NONE
    val download = s.download
    SettingRow(
        title = stringResource(R.string.settings_updates),
        icon = Icons.Outlined.SystemUpdate,
        subtitle = when {
            download is UpdateDownload.Downloading -> download.progress
                ?.let { stringResource(R.string.settings_update_downloading_pct, (it * 100).toInt()) }
                ?: stringResource(R.string.update_downloading)
            available -> stringResource(R.string.settings_update_available, s.info.version)
            s.checking -> stringResource(R.string.settings_update_checking)
            s.checked -> stringResource(R.string.settings_update_current, BuildConfig.VERSION_NAME)
            else -> stringResource(R.string.settings_update_tap, BuildConfig.VERSION_NAME)
        },
        subtitleColor = if (available) MaterialTheme.colorScheme.primary else null,
        onClick = when {
            s.checking || download is UpdateDownload.Downloading -> null
            available && s.info.apkUrl.isBlank() -> ({ ctx.openUpdateUrl(s.info.pageUrl) })
            available -> vm::startUpdate
            else -> vm::checkForUpdates
        },
        trailing = if (s.checking || download is UpdateDownload.Downloading) {
            { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        } else null,
    )
}

@Composable
private fun ThemePicker(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        val modes = ThemeMode.entries
        modes.forEachIndexed { i, m ->
            SegmentedButton(
                selected = m == mode,
                onClick = { onSelect(m) },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                icon = {
                    Icon(
                        when (m) {
                            ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                            ThemeMode.LIGHT -> Icons.Outlined.LightMode
                            ThemeMode.DARK -> Icons.Outlined.DarkMode
                        },
                        null,
                    )
                },
            ) { Text(stringResource(themeRes(m))) }
        }
    }
}

fun themeRes(m: ThemeMode) = when (m) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun AdvancedHeader(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val state = stringResource(if (expanded) R.string.settings_expanded else R.string.settings_collapsed)
    SettingRow(
        title = stringResource(R.string.more_advanced),
        icon = Icons.Outlined.Tune,
        subtitle = stringResource(R.string.more_advanced_subtitle),
        onClick = onToggle,
        trailing = { Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rotation)) },
        modifier = Modifier.padding(top = Spacing.md).semantics { stateDescription = state },
    )
}

/** Scheduled alarms and cache tools, inline rather than in stacked dialogs. */
@Composable
private fun AdvancedSection(vm: SettingsViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val cache by vm.cache.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf<Busy?>(null) }
    val is24 = rememberIs24h()
    val fmt = remember(is24) { DateTimeFormatter.ofPattern(if (is24) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault()) }

    Column {
        SettingRow(stringResource(R.string.adv_scheduled), icon = Icons.Outlined.Schedule)
        Column(Modifier.padding(start = 72.dp, end = Spacing.lg, bottom = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            val p = plan
            if (p == null || p.first.isEmpty()) {
                Text(stringResource(R.string.adv_scheduled_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else p.first.forEach { s ->
                val kind = stringResource(when (s.kind) {
                    AlarmKind.ADHAN -> R.string.adv_kind_adhan
                    AlarmKind.REMINDER -> R.string.adv_kind_reminder
                    AlarmKind.TEST -> R.string.adv_kind_test
                })
                Text(
                    stringResource(R.string.adv_scheduled_item, prayerName(s.prayer), kind, fmt.format(s.fireAt.atZone(p.second))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        cache?.let { info ->
            val last = if (info.lastSync <= 0) stringResource(R.string.adv_never)
            else DateUtils.getRelativeTimeSpanString(info.lastSync).toString()
            Text(
                stringResource(R.string.settings_cache_summary, info.days, info.sounds, info.soundsSize, last),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }
        ActionRow(stringResource(R.string.adv_refresh_now), Icons.Outlined.Refresh, busy, Busy.REFRESH) { vm.refreshNow() }
        ActionRow(stringResource(R.string.adv_clear_times), Icons.Outlined.CleaningServices, busy, Busy.CLEAR_TIMES) { confirm = Busy.CLEAR_TIMES }
        ActionRow(stringResource(R.string.adv_delete_sounds), Icons.Outlined.MusicOff, busy, Busy.DELETE_SOUNDS) { confirm = Busy.DELETE_SOUNDS }

        if (BuildConfig.DEBUG) {
            SectionHeader(stringResource(R.string.adv_debug))
            SettingRow(stringResource(R.string.adv_debug_sync), icon = Icons.Outlined.BugReport, onClick = vm::debugSync)
            SettingRow(stringResource(R.string.adv_debug_widget), icon = Icons.Outlined.BugReport, onClick = vm::debugWidget)
            SettingRow(stringResource(R.string.adv_debug_test_all), icon = Icons.Outlined.BugReport, onClick = vm::debugTestAll)
            SettingRow(stringResource(R.string.adv_debug_dump), icon = Icons.Outlined.BugReport, onClick = vm::debugDump)
        }
    }

    confirm?.let { which ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(stringResource(if (which == Busy.CLEAR_TIMES) R.string.adv_clear_times_confirm else R.string.adv_delete_sounds_confirm)) },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    if (which == Busy.CLEAR_TIMES) vm.clearTimes() else vm.deleteSounds()
                    confirm = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
        )
    }
}

@Composable
private fun ActionRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, busy: Busy?, kind: Busy, onClick: () -> Unit) {
    val running = busy == kind
    val workingLabel = stringResource(R.string.settings_working)
    SettingRow(
        title = title,
        icon = icon,
        onClick = if (busy == null) onClick else null,
        trailing = if (running) ({ CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }) else ({}),
        modifier = Modifier.semantics {
            role = Role.Button
            if (running) stateDescription = workingLabel
        },
    )
}

@Composable
private fun AboutDialog(onClose: () -> Unit) {
    var licenses by remember { mutableStateOf(false) }
    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text(stringResource(R.string.licenses_title)) },
            text = { Text(stringResource(R.string.licenses_body), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { licenses = false }) { Text(stringResource(R.string.action_close)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Image(painterResource(R.drawable.ic_logo), null, Modifier.size(48.dp)) },
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                Text(stringResource(R.string.about_source), textAlign = TextAlign.Center)
                Text(stringResource(R.string.about_quran_source), textAlign = TextAlign.Center)
                Text(stringResource(R.string.about_map), textAlign = TextAlign.Center)
                Text(stringResource(R.string.about_copyright, Year.now().value))
            }
        },
        dismissButton = { TextButton(onClick = { licenses = true }) { Text(stringResource(R.string.about_licenses)) } },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
    )
}
