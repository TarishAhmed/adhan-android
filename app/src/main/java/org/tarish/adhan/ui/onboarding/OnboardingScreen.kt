package org.tarish.adhan.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.tarish.adhan.R
import org.tarish.adhan.ui.util.BatteryIntents
import org.tarish.adhan.ui.components.AdhanCard
import org.tarish.adhan.ui.components.geometricPattern
import org.tarish.adhan.ui.theme.NotoSerif
import org.tarish.adhan.ui.theme.Radius
import org.tarish.adhan.ui.theme.Spacing
import org.tarish.adhan.ui.theme.arabicLabel
import org.tarish.adhan.ui.theme.extendedColors
import org.tarish.adhan.ui.util.LOCATION_PERMISSIONS
import org.tarish.adhan.ui.util.isIgnoringBatteryOptimizations
import org.tarish.adhan.ui.util.openExactAlarmSettings

private enum class Step { WELCOME, LOCATION, NOTIFICATIONS, RELIABILITY }

private fun Context.notificationsGranted() = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Context.exactAlarmsAllowed() = Build.VERSION.SDK_INT < 31 ||
    getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

/**
 * Each step has one clear action and can't be swiped past. Steps whose permission is already
 * granted are left out. [onFinish] reports whether the user chose to pick a city, and whether the
 * reliability step was shown (so Today doesn't ask again).
 */
@Composable
fun OnboardingScreen(onFinish: (openLocation: Boolean, reliabilityShown: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val steps = remember {
        buildList {
            add(Step.WELCOME)
            add(Step.LOCATION)
            if (!ctx.notificationsGranted()) add(Step.NOTIFICATIONS)
            if (!ctx.exactAlarmsAllowed() || !ctx.isIgnoringBatteryOptimizations()) add(Step.RELIABILITY)
        }
    }
    val pager = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()
    var chooseCity by rememberSaveable { mutableStateOf(false) }
    var locationDenied by rememberSaveable { mutableStateOf(false) }

    fun next() {
        if (pager.currentPage == steps.lastIndex) onFinish(chooseCity, Step.RELIABILITY in steps)
        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
    }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) next() else locationDenied = true
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { next() }

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .geometricPattern(extendedColors.ornament, alpha = 0.07f, cell = 56.dp)
                .systemBarsPadding(),
        ) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = false) { page ->
                when (steps[page]) {
                    Step.WELCOME -> Page(
                        header = { Logo() },
                        title = R.string.onb_welcome_title,
                        body = R.string.onb_welcome_body,
                        cta = R.string.onb_get_started,
                        onCta = ::next,
                    )
                    Step.LOCATION -> Page(
                        header = { Badge(Icons.Outlined.LocationOn) },
                        title = R.string.onb_location_title,
                        body = if (locationDenied) R.string.onb_location_denied_body else R.string.onb_location_body,
                        cta = if (locationDenied) R.string.onb_choose_city_cta else R.string.onb_location_cta,
                        onCta = {
                            if (locationDenied) { chooseCity = true; next() } else locLauncher.launch(LOCATION_PERMISSIONS)
                        },
                        secondary = if (locationDenied) R.string.onb_try_again else R.string.onb_choose_city,
                        onSecondary = {
                            if (locationDenied) { locationDenied = false; locLauncher.launch(LOCATION_PERMISSIONS) }
                            else { chooseCity = true; next() }
                        },
                    )
                    Step.NOTIFICATIONS -> Page(
                        header = { Badge(Icons.Outlined.NotificationsActive) },
                        title = R.string.onb_notif_title,
                        body = R.string.onb_notif_body,
                        cta = R.string.onb_notif_cta,
                        onCta = {
                            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else next()
                        },
                        secondary = R.string.action_not_now,
                        onSecondary = ::next,
                    )
                    Step.RELIABILITY -> ReliabilityPage(onDone = ::next)
                }
            }
            StepDots(steps.size, pager.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xxl))
        }
    }
}

@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier) {
    val label = stringResource(R.string.onb_step_cd, current + 1, count)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(count) { i ->
            val selected = current == i
            val w by animateDpAsState(if (selected) 24.dp else 8.dp, tween(250), label = "dot")
            Box(
                Modifier
                    .size(width = w, height = 8.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(Radius.xs),
                    )
                    .clearAndSetSemantics { }
            )
        }
    }
}

@Composable
private fun Logo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(144.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(88.dp))
        }
        Spacer(Modifier.height(Spacing.lg))
        // "Assalamu alaikum": decorative, the English title carries the meaning.
        Text("السلام عليكم", style = arabicLabel(28), color = MaterialTheme.colorScheme.primary, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun Badge(icon: ImageVector) {
    Box(
        Modifier.size(144.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Page(
    header: @Composable () -> Unit,
    title: Int,
    body: Int,
    cta: Int,
    onCta: () -> Unit,
    secondary: Int? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xxxl, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        header()
        Spacer(Modifier.height(Spacing.xxxl))
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, fontFamily = NotoSerif, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xxxl))
        Button(onClick = onCta, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 56.dp)) {
            Text(stringResource(cta), style = MaterialTheme.typography.titleMedium)
        }
        if (secondary != null) {
            Spacer(Modifier.height(Spacing.sm))
            TextButton(onClick = onSecondary, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(secondary)) }
        }
    }
}

/** Exact alarms + battery in one place, each with its live status. */
@Composable
private fun ReliabilityPage(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val exactOk = remember(tick) { ctx.exactAlarmsAllowed() }
    val batteryOk = remember(tick) { ctx.isIgnoringBatteryOptimizations() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xxl, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Badge(Icons.Outlined.Alarm)
        Spacer(Modifier.height(Spacing.xxxl))
        Text(stringResource(R.string.onb_reliability_title), style = MaterialTheme.typography.headlineMedium, fontFamily = NotoSerif, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.lg))
        Text(
            stringResource(R.string.onb_reliability_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxl))
        if (Build.VERSION.SDK_INT >= 31) {
            Fix(Icons.Outlined.Alarm, R.string.onb_exact_title, exactOk, R.string.action_allow) { ctx.openExactAlarmSettings() }
            Spacer(Modifier.height(Spacing.md))
        }
        Fix(Icons.Outlined.BatteryChargingFull, R.string.onb_battery_title, batteryOk, R.string.action_fix) { BatteryIntents.requestIgnore(ctx) }
        Spacer(Modifier.height(Spacing.xxl))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 56.dp)) {
            Text(stringResource(if (exactOk && batteryOk) R.string.onb_finish else R.string.onb_finish_later), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Fix(icon: ImageVector, title: Int, ok: Boolean, action: Int, onFix: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AdhanCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Rounded.CheckCircle else icon, null, tint = if (ok) cs.primary else cs.onSurfaceVariant)
            Spacer(Modifier.size(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(if (ok) R.string.onb_fix_done else R.string.onb_fix_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) cs.primary else cs.error,
                )
            }
            if (!ok) FilledTonalButton(onClick = onFix) { Text(stringResource(action)) }
        }
    }
}
