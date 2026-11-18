package org.tarish.adhan.ui.qibla

import android.hardware.SensorManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.ExploreOff
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.tarish.adhan.R
import org.tarish.adhan.domain.QiblaMath
import org.tarish.adhan.ui.components.StateView
import org.tarish.adhan.ui.theme.extendedColors
import org.tarish.adhan.ui.util.rememberLocationPermissionRequester
import org.tarish.adhan.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(onChooseCity: () -> Unit, vm: QiblaViewModel = hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val permission = rememberLocationPermissionRequester(onChooseCity = onChooseCity) { granted, _ -> if (granted) vm.refresh() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qibla_title)) },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !refreshing) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.qibla_refresh))
                    }
                },
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        val i = info
        if (i == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                StateView(
                    icon = Icons.Outlined.LocationOff,
                    title = stringResource(R.string.home_set_location_title),
                    body = stringResource(R.string.home_set_location_body),
                    primary = stringResource(R.string.action_allow_location), onPrimary = permission.request,
                    secondary = stringResource(R.string.action_choose_city), onSecondary = onChooseCity,
                )
            }
            return@Scaffold
        }
        QiblaContent(i, Modifier.padding(padding))
    }
}

@Composable
private fun QiblaContent(info: QiblaInfo, modifier: Modifier) {
    val ctx = LocalContext.current
    val sensor = remember { CompassSensor(ctx.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reading by remember { mutableStateOf<CompassReading?>(null) }
    var lastTiltAt by remember { mutableLongStateOf(0L) }
    var tilt by remember { mutableIntStateOf(0) }

    // Register only while visible and resumed.
    LaunchedEffect(info.lat, info.lng, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            sensor.readings(info.lat, info.lng).collect { r ->
                reading = r
                val t = System.currentTimeMillis()
                if (t - lastTiltAt >= 250) { tilt = r.tiltDeg; lastTiltAt = t }
            }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val available = sensor.isAvailable
        if (available) {
            CalibrationCard(reading?.accuracy, tilt)
            Spacer(Modifier.height(8.dp))
            Compass(heading = reading?.headingTrue ?: 0.0, qibla = info.bearing)
            Spacer(Modifier.height(8.dp))
        } else {
            StateView(
                icon = Icons.Outlined.ExploreOff,
                title = stringResource(R.string.qibla_no_compass_title),
                body = stringResource(R.string.qibla_no_compass_body),
            )
        }

        val nf = remember { NumberFormat.getIntegerInstance() }
        if (available) {
            InfoCard(Icons.Outlined.Explore, stringResource(R.string.qibla_heading),
                stringResource(R.string.qibla_heading_value, String.format(Locale.getDefault(), "%.1f", reading?.headingTrue ?: 0.0)))
            InfoCard(Icons.Outlined.Sensors, stringResource(R.string.qibla_accuracy), stringResource(accuracyRes(reading?.accuracy)))
        }
        InfoCard(Icons.Outlined.Navigation, stringResource(R.string.qibla_direction),
            stringResource(R.string.qibla_direction_value, String.format(Locale.getDefault(), "%.1f", info.bearing), QiblaMath.directionName(info.bearing)))
        InfoCard(Icons.Outlined.Straighten, stringResource(R.string.qibla_distance),
            stringResource(R.string.qibla_distance_value, nf.format(info.distanceKm.roundToInt())))
        QiblaMap(info.lat, info.lng)
    }
}

private fun accuracyRes(acc: Int?) = when (acc) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> R.string.qibla_accuracy_excellent
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM, null -> R.string.qibla_accuracy_good
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> R.string.qibla_accuracy_fair
    else -> R.string.qibla_accuracy_poor
}

@Composable
private fun CalibrationCard(accuracy: Int?, tilt: Int) {
    val bad = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
    val cs = MaterialTheme.colorScheme
    Surface(color = if (bad) cs.errorContainer else cs.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            Icon(Icons.Outlined.TipsAndUpdates, null, tint = if (bad) cs.onErrorContainer else cs.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(if (bad) R.string.qibla_calibration_needed else R.string.qibla_calibration_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (bad) cs.onErrorContainer else cs.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.qibla_calibration_body, tilt),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bad) cs.onErrorContainer else cs.onPrimaryContainer,
                )
            }
        }
    }
}

/** Unwraps successive angles so animation takes the shortest path across north. */
@Composable
private fun rememberUnwrapped(target: Float): Float {
    var acc by remember { mutableFloatStateOf(target) }
    var last by remember { mutableFloatStateOf(target) }
    if (target != last) {
        var d = target - last
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        acc += d
        last = target
    }
    return acc
}

@Composable
private fun Compass(heading: Double, qibla: Double) {
    val cs = MaterialTheme.colorScheme
    val ext = extendedColors
    val haptic = LocalHapticFeedback.current

    val dialTarget = rememberUnwrapped((-heading).toFloat())
    val dial by animateFloatAsState(dialTarget, spring(stiffness = Spring.StiffnessLow), label = "dial")

    val rel = QiblaMath.relativeAngle(qibla, heading)
    val d = abs(rel)
    val needle = when {
        d <= 15 -> ext.qiblaAligned
        d <= 60 -> lerp(ext.qiblaAligned, ext.warning, ((d - 15) / 45).toFloat())
        else -> lerp(ext.warning, ext.qiblaFar, ((d - 60) / 120).toFloat().coerceIn(0f, 1f))
    }

    // Haptic once when aligned ≤ 3° for ≥ 300 ms; re-arm after > 8°.
    var armed by remember { mutableStateOf(true) }
    LaunchedEffect(d <= 3) {
        if (d <= 3 && armed) {
            kotlinx.coroutines.delay(300)
            if (armed) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); armed = false }
        }
    }
    if (d > 8 && !armed) armed = true

    // Live-region announcement: at most every 3 s and only on ≥ 5° change (§15.4).
    val desired = if (d <= 3) stringResource(R.string.qibla_aligned_cd)
    else d.roundToInt().let { n -> pluralStringResource(R.plurals.qibla_turn_cd, n, stringResource(if (rel < 0) R.string.qibla_left else R.string.qibla_right), n) }
    var announced by remember { mutableStateOf("") }
    var announcedAngle by remember { mutableFloatStateOf(999f) }
    var announcedAt by remember { mutableLongStateOf(0L) }
    val tNow = System.currentTimeMillis()
    if (tNow - announcedAt >= 3000 && abs(rel.toFloat() - announcedAngle) >= 5f) {
        announced = desired
        announcedAngle = rel.toFloat()
        announcedAt = tNow
    }

    Box(
        Modifier
            .size(280.dp)
            .shadow(8.dp, CircleShape)
            .background(cs.surface, CircleShape)
            .border(BorderStroke(3.dp, cs.primary), CircleShape)
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announced },
        contentAlignment = Alignment.Center,
    ) {
        // Rotating dial. absoluteOffset: a physical compass must not mirror under RTL.
        Box(Modifier.size(280.dp).rotate(dial), contentAlignment = Alignment.Center) {
            listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW").forEachIndexed { idx, label ->
                val a = Math.toRadians(idx * 45.0)
                Text(
                    label,
                    color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.absoluteOffset(x = (120 * sin(a)).dp, y = (-120 * cos(a)).dp).rotate(-dial),
                )
            }
            val q = Math.toRadians(qibla)
            Box(
                Modifier
                    .absoluteOffset(x = (95 * sin(q)).dp, y = (-95 * cos(q)).dp)
                    .rotate(-dial)
                    .size(30.dp)
                    .background(extendedColors.kaaba, CircleShape)
                    .border(2.dp, extendedColors.onKaaba, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("🕋", fontSize = 16.sp) }
        }
        // Fixed needle pointing to the top of the phone
        Canvas(Modifier.size(4.dp, 120.dp).absoluteOffset(y = (-60).dp)) {
            drawRoundRect(needle, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
        }
        Box(Modifier.size(20.dp).background(cs.primary, CircleShape).border(3.dp, cs.surface, CircleShape))
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium.tabular())
        }
    }
}
