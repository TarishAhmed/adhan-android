package org.tarish.adhan.ui.home

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.tarish.adhan.R
import org.tarish.adhan.core.format.countdownSeconds
import org.tarish.adhan.core.format.countdownText
import org.tarish.adhan.core.result.AppError
import org.tarish.adhan.domain.Greeting
import org.tarish.adhan.domain.HijriFormatter
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.Urgency
import org.tarish.adhan.ui.components.EyebrowLabel
import org.tarish.adhan.ui.components.LocalAppSnackbar
import org.tarish.adhan.ui.components.PrayerProgressBar
import org.tarish.adhan.ui.components.Skeleton
import org.tarish.adhan.ui.components.StateView
import org.tarish.adhan.ui.components.geometricPattern
import org.tarish.adhan.ui.notifications.NotificationsViewModel
import org.tarish.adhan.ui.notifications.PrayerEditorSheet
import org.tarish.adhan.ui.theme.Inter
import org.tarish.adhan.ui.theme.NotoSerif
import org.tarish.adhan.ui.theme.Radius
import org.tarish.adhan.ui.theme.Spacing
import org.tarish.adhan.ui.theme.arabicLabel
import org.tarish.adhan.ui.theme.extendedColors
import org.tarish.adhan.ui.theme.tabular
import org.tarish.adhan.ui.util.appDetailsIntent
import org.tarish.adhan.ui.util.clockText
import org.tarish.adhan.ui.util.isLocationError
import org.tarish.adhan.ui.util.messageRes
import org.tarish.adhan.ui.util.prayerName
import org.tarish.adhan.ui.util.rememberLocationPermissionRequester
import org.tarish.adhan.ui.util.tryStart
import org.tarish.adhan.ui.util.zoneCity
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenLocation: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
    notifVm: NotificationsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val permissions by notifVm.permissions.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val ctx = LocalContext.current
    var editing by rememberSaveable { mutableStateOf<PrayerName?>(null) }

    LaunchedEffect(Unit) {
        vm.eventFlow.collect { e ->
            snackbar.show(
                when (e) {
                    HomeEvent.RefreshOk -> ctx.getString(R.string.home_refresh_success)
                    HomeEvent.RefreshFailed -> ctx.getString(R.string.home_refresh_failure)
                    is HomeEvent.NotificationToggled -> ctx.getString(
                        if (e.on) R.string.today_adhan_on else R.string.today_adhan_off,
                        ctx.getString(org.tarish.adhan.alarm.NotificationChannels.prayerNameRes(e.prayer)),
                    )
                }
            )
        }
    }
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        notifVm.refreshPermissions()
        onPauseOrDispose { }
    }

    val permission = rememberLocationPermissionRequester(onChooseCity = onOpenLocation) { granted, permanently ->
        if (granted) vm.onResume() else vm.onPermissionDenied(permanently)
    }

    val refreshLabel = stringResource(R.string.home_refresh)
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(greetingRes(state.greeting)), fontFamily = NotoSerif) },
                actions = { LocationChip(state, onOpenLocation) },
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Pull-to-refresh has no TalkBack equivalent; expose it as a custom action.
                .semantics { customActions = listOf(CustomAccessibilityAction(refreshLabel) { vm.refresh(); true }) },
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                when (val st = state.status) {
                    HomeStatus.Loading -> {
                        item { HeroSkeleton() }
                        item { TimelineSkeleton() }
                    }
                    HomeStatus.NoLocation -> item {
                        StateView(
                            icon = Icons.Outlined.LocationOff,
                            title = stringResource(R.string.home_set_location_title),
                            body = stringResource(R.string.home_set_location_body),
                            primary = stringResource(R.string.action_allow_location),
                            onPrimary = permission.request,
                            secondary = stringResource(R.string.action_choose_city),
                            onSecondary = onOpenLocation,
                        )
                    }
                    is HomeStatus.Error -> item {
                        val e = st.error
                        val permanent = e is AppError.LocationPermissionPermanentlyDenied
                        StateView(
                            icon = if (e.isLocationError) Icons.Outlined.LocationOff else Icons.Outlined.CloudOff,
                            title = if (e.isLocationError) stringResource(R.string.error_location_generic) else stringResource(R.string.home_no_data_title),
                            body = stringResource(e.messageRes()),
                            primary = stringResource(if (permanent) R.string.action_open_settings else R.string.action_try_again),
                            onPrimary = {
                                if (permanent) ctx.tryStart(ctx.appDetailsIntent())
                                else if (e is AppError.LocationPermissionDenied) permission.request()
                                else vm.retry()
                            },
                            secondary = if (e.isLocationError) stringResource(R.string.action_choose_city) else null,
                            onSecondary = onOpenLocation,
                        )
                    }
                    HomeStatus.Ready -> {
                        val summary: @Composable () -> Unit = {
                            HeroCard(state)
                            if (state.stale) Note(stringResource(R.string.home_stale_banner))
                            state.zoneHint?.let { z -> Note(stringResource(R.string.home_zone_hint, zoneCity(z))) }
                        }
                        val day: @Composable () -> Unit = {
                            DaySwitcher(state, onShift = vm::shiftDay, onToday = vm::goToToday)
                            Timeline(state = state, onShift = vm::shiftDay, onToggle = vm::toggleNotification, onEdit = { editing = it })
                        }
                        // Landscape / tablet: hero beside the timeline instead of above it.
                        if (wide) item {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) { summary() }
                                Column(Modifier.weight(1f).padding(top = Spacing.sm)) { day() }
                            }
                        } else {
                            item { Column { summary() } }
                            item { Column { day() } }
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.xxxl)) }
            }
        }
    }

    editing?.let { p ->
        PrayerEditorSheet(
            prayer = p,
            vm = notifVm,
            notificationsEnabled = permissions.notificationsEnabled,
            onDismiss = { editing = null },
        )
    }
}

private fun greetingRes(g: Greeting) = when (g) {
    Greeting.MORNING -> R.string.greeting_morning
    Greeting.AFTERNOON -> R.string.greeting_afternoon
    Greeting.EVENING -> R.string.greeting_evening
    Greeting.NIGHT -> R.string.greeting_night
}

/** The single entry point to Location from Today. */
@Composable
private fun LocationChip(state: HomeUiState, onClick: () -> Unit) {
    val label = when {
        state.locationLabel != null -> state.locationLabel
        state.locationLoading -> stringResource(R.string.location_loading)
        else -> stringResource(R.string.location_chip_placeholder)
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) },
        leadingIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp)) },
        modifier = Modifier
            .padding(end = Spacing.sm)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
    )
}

/** Arabic prayer names; shown beside the English name, not translated. */
private fun arabicName(p: PrayerName) = when (p) {
    PrayerName.FAJR -> "الفجر"
    PrayerName.SUNRISE -> "الشروق"
    PrayerName.ZUHR -> "الظهر"
    PrayerName.ASR -> "العصر"
    PrayerName.SUNSET -> "الغروب"
    PrayerName.MAGHRIB -> "المغرب"
    PrayerName.ISHA -> "العشاء"
}

/** 1-second ticker that runs only while the screen is resumed (§8.3 Ticking). */
@Composable
private fun rememberSecondTicker(): Instant {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(Instant.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = Instant.now()
                delay(1000 - (System.currentTimeMillis() % 1000))
            }
        }
    }
    return now
}

@Composable
private fun HeroCard(state: HomeUiState) {
    val cs = MaterialTheme.colorScheme
    val next = state.next
    val zone = state.zone
    val nameText = next?.let { prayerName(it.prayer) } ?: "—"
    val timeText = next?.let { clockText(it.time, zone) } ?: ""
    // Merged description is updated per minute (the minute tick drives state), not per second (§15.4).
    val minuteNow = remember(state) { Instant.now() }
    val secs = next?.let { countdownSeconds(it.time, minuteNow) } ?: 0
    val hours = (secs / 3600).toInt()
    val minutes = ((secs % 3600) / 60).toInt()
    val durationCd = stringResource(
        R.string.home_duration_cd,
        pluralStringResource(R.plurals.duration_hours, hours, hours),
        pluralStringResource(R.plurals.duration_minutes, minutes, minutes),
    )
    val cd = if (next != null) stringResource(R.string.home_next_prayer_cd, nameText, timeText, durationCd) else ""
    val onCard = cs.onPrimaryContainer

    Column(
        Modifier
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.md)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(cs.primaryContainer)
            .geometricPattern(extendedColors.ornament, alpha = 0.14f)
            .padding(Spacing.xl)
            .semantics(mergeDescendants = true) { contentDescription = cd },
    ) {
        EyebrowLabel(stringResource(R.string.home_up_next), color = onCard.copy(alpha = 0.75f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nameText, style = MaterialTheme.typography.displaySmall, color = onCard, modifier = Modifier.weight(1f))
            if (next != null) Text(arabicName(next.prayer), style = arabicLabel(26), color = onCard.copy(alpha = 0.8f))
        }
        if (next != null) {
            Text(
                timeText + if (next.isTomorrow) " " + stringResource(R.string.home_tomorrow) else "",
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = onCard.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.Bottom) {
                Countdown(next.time)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    stringResource(R.string.home_remaining),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onCard.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        val w = state.window
        if (w != null) {
            Spacer(Modifier.height(Spacing.lg))
            val startName = prayerName(w.start)
            val urgencyWord = when (w.urgency) {
                Urgency.GREEN -> stringResource(R.string.urgency_green)
                Urgency.YELLOW -> stringResource(R.string.urgency_yellow)
                Urgency.RED -> stringResource(R.string.urgency_red)
                Urgency.NEUTRAL -> ""
            }
            Text(
                stringResource(
                    if (w.urgency == Urgency.NEUTRAL) R.string.today_window_between else R.string.today_window_label,
                    startName, prayerName(w.end),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = onCard.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(Spacing.xs))
            PrayerProgressBar(
                progress = w.progress,
                segments = w.segments,
                stateDescription = stringResource(R.string.window_cd, startName, urgencyWord),
            )
            if (w.urgency != Urgency.NEUTRAL) {
                Spacer(Modifier.height(Spacing.sm))
                UrgencyLegend(onCard)
            }
        }
    }
}

/** Visible key for the bar colours, which were previously explained only to screen readers. */
@Composable
private fun UrgencyLegend(textColor: Color) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.clearAndSetSemantics { }) {
        LegendItem(cs.primary, stringResource(R.string.today_legend_best), textColor)
        LegendItem(extendedColors.warning, stringResource(R.string.today_legend_late), textColor)
        LegendItem(cs.error, stringResource(R.string.today_legend_ending), textColor)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
    }
}

/** Per-character crossfade so only changed digits animate. */
@Composable
private fun Countdown(target: Instant) {
    val now = rememberSecondTicker()
    val text = countdownText(target, now)
    val style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.W600).tabular()
    Row(Modifier.clearAndSetSemantics { }) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "digit$i",
            ) { c ->
                Text(c.toString(), style = style, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** ‹ Today › with the Gregorian and Hijri dates; tapping the date returns to today. */
@Composable
private fun DaySwitcher(state: HomeUiState, onShift: (Int) -> Unit, onToday: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val offset = state.selectedDate.toEpochDay() - state.date.toEpochDay()
    val months = stringArrayResource(R.array.hijri_months).toList()
    val greg = remember(state.selectedDate) {
        val locale = Locale.getDefault()
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(state.selectedDate)
    }
    val hijri = remember(state.selectedDate, months) { HijriFormatter.format(state.selectedDate, months) }
    val title = relativeDay(offset) ?: greg
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShift(-1) }, enabled = offset > -HomeViewModel.MAX_DAYS_BACK) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.today_prev_day))
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Radius.md))
                .clickable(enabled = offset != 0L, onClickLabel = stringResource(R.string.today_back_to_today), onClick = onToday)
                .padding(vertical = Spacing.xs)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                listOfNotNull(if (title != greg) greg else null, hijri?.let { stringResource(R.string.home_hijri_approx, it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(onClick = { onShift(1) }, enabled = offset < HomeViewModel.MAX_DAYS_AHEAD) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.today_next_day))
        }
    }
}

@Composable
private fun relativeDay(offset: Long): String? = when (offset) {
    0L -> stringResource(R.string.today_label)
    1L -> stringResource(R.string.today_tomorrow)
    -1L -> stringResource(R.string.today_yesterday)
    else -> null
}

@Composable
private fun Timeline(
    state: HomeUiState,
    onShift: (Int) -> Unit,
    onToggle: (PrayerName) -> Unit,
    onEdit: (PrayerName) -> Unit,
) {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        Modifier
            .padding(horizontal = Spacing.lg)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = Spacing.sm)
            // Horizontal swipe changes day; buttons above do the same for non-gesture users.
            .pointerInput(rtl) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = {
                        if (abs(dx) > threshold) onShift(if ((dx < 0) != rtl) 1 else -1)
                    },
                ) { _, amount -> dx += amount }
            },
    ) {
        if (state.timeline.isEmpty()) {
            repeat(6) { Skeleton(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm).fillMaxWidth().height(48.dp)) }
        } else {
            state.timeline.forEachIndexed { i, item ->
                TimelineRow(
                    item = item,
                    zone = state.zone,
                    first = i == 0,
                    last = i == state.timeline.lastIndex,
                    onToggle = { onToggle(item.prayer) },
                    onEdit = { onEdit(item.prayer) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineRow(
    item: TimelineItem,
    zone: java.time.ZoneId,
    first: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val name = prayerName(item.prayer)
    val time = clockText(item.time, zone)
    val status = when {
        item.isCurrent -> stringResource(R.string.today_now)
        item.isPast -> stringResource(R.string.today_passed)
        else -> null
    }
    val contentAlpha = if (item.isPast) 0.6f else 1f
    val rowMod = if (item.notifOn != null) {
        Modifier.clickable(onClickLabel = stringResource(R.string.today_adhan_settings), onClick = onEdit)
    } else Modifier

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.sm)
            .clip(RoundedCornerShape(Radius.lg))
            .background(if (item.isCurrent) cs.primaryContainer else Color.Transparent)
            .then(rowMod)
            .padding(start = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rail(current = item.isCurrent, past = item.isPast, first = first, last = last)
        Spacer(Modifier.width(Spacing.md))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = Spacing.sm)
                .alpha(contentAlpha)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(name, time, status).joinToString(", ")
                },
        ) {
            if (item.isCurrent) EyebrowLabel(stringResource(R.string.today_now))
            // Flows so that at large text sizes the Arabic drops below instead of splitting the English name.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (item.isCurrent) FontWeight.W700 else FontWeight.W600),
                    color = if (item.isCurrent) cs.onPrimaryContainer else cs.onSurface,
                    softWrap = false,
                )
                Text(arabicName(item.prayer), style = arabicLabel(16), color = cs.onSurfaceVariant, softWrap = false)
            }
        }
        Text(
            time,
            softWrap = false,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = if (item.isCurrent) cs.onPrimaryContainer else cs.onSurface,
            modifier = Modifier.alpha(contentAlpha).clearAndSetSemantics { },
        )
        if (item.notifOn != null) {
            val on = item.notifOn
            val desc = stringResource(R.string.today_adhan_bell_cd, name)
            val state = stringResource(if (on) R.string.today_state_on else R.string.today_state_off)
            IconButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle() },
                modifier = Modifier.semantics { contentDescription = desc; stateDescription = state },
            ) {
                Icon(
                    if (on) Icons.Rounded.NotificationsActive else Icons.Outlined.NotificationsOff,
                    contentDescription = null,
                    tint = if (on) cs.primary else cs.outline,
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

/** Vertical rail joining the rows; the dot is filled for the current prayer, hollow for upcoming. */
@Composable
private fun Rail(current: Boolean, past: Boolean, first: Boolean, last: Boolean) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val dot = if (past) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
    val ornament = extendedColors.ornament
    Canvas(Modifier.width(16.dp).fillMaxHeight().clearAndSetSemantics { }) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = if (current) 6.dp.toPx() else 4.dp.toPx()
        val w = 2.dp.toPx()
        if (!first) drawLine(line, Offset(cx, 0f), Offset(cx, cy - r), strokeWidth = w)
        if (!last) drawLine(line, Offset(cx, cy + r), Offset(cx, size.height), strokeWidth = w)
        when {
            current -> {
                drawCircle(ornament.copy(alpha = 0.35f), r + 3.dp.toPx(), Offset(cx, cy))
                drawCircle(dot, r, Offset(cx, cy))
            }
            past -> drawCircle(dot, r, Offset(cx, cy))
            else -> drawCircle(dot, r, Offset(cx, cy), style = Stroke(w))
        }
    }
}

@Composable
private fun HeroSkeleton() {
    Skeleton(
        Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg).fillMaxWidth().height(220.dp),
        corner = Radius.xl,
    )
}

@Composable
private fun TimelineSkeleton() {
    Skeleton(Modifier.padding(horizontal = Spacing.lg).fillMaxWidth().height(380.dp), corner = Radius.xl)
}
