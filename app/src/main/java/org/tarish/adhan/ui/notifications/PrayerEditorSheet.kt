package org.tarish.adhan.ui.notifications

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt
import org.tarish.adhan.R
import org.tarish.adhan.data.DownloadState
import org.tarish.adhan.domain.model.AdhanSound
import org.tarish.adhan.domain.model.PrayerName
import org.tarish.adhan.domain.model.PrayerNotificationPrefs
import org.tarish.adhan.ui.components.SectionHeader
import org.tarish.adhan.ui.util.prayerName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerEditorSheet(
    prayer: PrayerName,
    vm: NotificationsViewModel,
    notificationsEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val prefsMap by vm.prefs.collectAsStateWithLifecycle()
    val pref = prefsMap[prayer] ?: PrayerNotificationPrefs(prayer)
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val previewer = remember { SoundPreviewer(ctx.applicationContext) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var pendingPreview by remember { mutableStateOf<String?>(null) }
    val name = prayerName(prayer)

    DisposableEffect(Unit) { onDispose { previewer.stop() } }
    LifecycleResumeEffect(Unit) { onPauseOrDispose { previewer.stop() } }

    // Start a preview once a just-selected sound finishes downloading.
    LaunchedEffect(downloads, pendingPreview) {
        val id = pendingPreview ?: return@LaunchedEffect
        if (downloads[id] is DownloadState.Done || vm.isDownloaded(id)) {
            previewer.play(id, vm.resolve(id)); pendingPreview = null
        } else if (downloads[id] is DownloadState.Failed) pendingPreview = null
    }

    ModalBottomSheet(
        onDismissRequest = { previewer.stop(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                slideInHorizontally(tween(300)) { it * dir } togetherWith slideOutHorizontally(tween(300)) { -it * dir }
            },
            label = "editorPage",
        ) { p ->
            if (p == 0) {
                MainPage(pref, name, notificationsEnabled, vm, onClose = { previewer.stop(); onDismiss() }, onSound = { page = 1 })
            } else {
                SoundPage(
                    pref, name, vm, downloads, previewer,
                    onBack = { page = 0 },
                    onClose = { previewer.stop(); onDismiss() },
                    onPick = { id ->
                        if (id == pref.soundId && previewer.playingId == id) {
                            previewer.stop()
                        } else {
                            vm.selectSound(prayer, id)
                            if (vm.isDownloaded(id)) previewer.play(id, vm.resolve(id)) else { previewer.stop(); pendingPreview = id }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SheetBar(title: String, onBack: (() -> Unit)?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
        else Spacer(Modifier.width(48.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.editor_close)) }
    }
}

@Composable
private fun MainPage(
    pref: PrayerNotificationPrefs, name: String, notificationsEnabled: Boolean,
    vm: NotificationsViewModel, onClose: () -> Unit, onSound: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        SheetBar(name, null, onClose)
        ListItem(
            headlineContent = { Text(stringResource(R.string.editor_enable, name)) },
            trailingContent = { Switch(checked = pref.enabled, onCheckedChange = { vm.setEnabled(pref.prayer, it) }) },
        )
        SectionHeader(stringResource(R.string.editor_days))
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val week = (0 until 7).map { first.plus(it.toLong()) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            week.forEach { d -> DayChip(d, d in pref.days, pref.enabled) { vm.toggleDay(pref.prayer, d) } }
        }
        SectionHeader(stringResource(R.string.editor_sound))
        ListItem(
            headlineContent = { Text(AdhanSound.byId(pref.soundId)?.displayName ?: "") },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.outline) },
            modifier = Modifier.clickable(onClick = onSound),
        )
        SectionHeader(stringResource(R.string.editor_reminder))
        var slider by remember(pref.advanceMinutes) { mutableFloatStateOf(pref.advanceMinutes.toFloat()) }
        val label = if (slider.roundToInt() == 0) stringResource(R.string.editor_reminder_off)
        else stringResource(R.string.editor_reminder_value, slider.roundToInt())
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = slider,
                onValueChange = { slider = (it / 5).roundToInt() * 5f },
                onValueChangeFinished = { vm.setAdvance(pref.prayer, slider.roundToInt()) },  // BUG-37
                valueRange = 0f..30f,
                steps = 5,
                modifier = Modifier.weight(1f),
            )
            Text(label, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
        }
        if (slider.roundToInt() > 0) {
            Text(
                slider.roundToInt().let { n -> pluralStringResource(R.plurals.editor_reminder_help, n, n, name) },
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(
            onClick = { vm.testAdhan(pref.prayer) },
            enabled = notificationsEnabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Outlined.NotificationsActive, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.editor_test))
        }
        if (!notificationsEnabled) {
            Text(
                stringResource(R.string.editor_test_blocked),
                style = MaterialTheme.typography.bodySmall, color = cs.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DayChip(day: DayOfWeek, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.5f
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) cs.primary.copy(alpha = alpha) else cs.surface,
        border = if (selected) null else BorderStroke(1.dp, cs.outline.copy(alpha = alpha)),
        modifier = Modifier.size(width = 40.dp, height = 48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                style = MaterialTheme.typography.labelMedium,
                color = (if (selected) cs.onPrimary else cs.onSurface).copy(alpha = alpha),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SoundPage(
    pref: PrayerNotificationPrefs, name: String, vm: NotificationsViewModel,
    downloads: Map<String, DownloadState>, previewer: SoundPreviewer,
    onBack: () -> Unit, onClose: () -> Unit, onPick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        SheetBar(stringResource(R.string.editor_sound_title, name), onBack, onClose)
        FlowRow(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            vm.catalog.forEach { s ->
                val selected = s.id == pref.soundId
                val dl = downloads[s.id]
                val downloading = dl is DownloadState.Progress
                FilterChip(
                    selected = selected,
                    onClick = { onPick(s.id) },
                    label = { Text(s.displayName) },
                    leadingIcon = if (selected) {{ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }} else null,
                    trailingIcon = when {
                        downloading -> {{
                            val f = (dl as DownloadState.Progress).fraction
                            if (f != null) CircularProgressIndicator(progress = { f }, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            else CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        }}
                        s.kind != AdhanSound.Kind.ADHAN -> null
                        vm.isDownloaded(s.id) || dl is DownloadState.Done -> {{
                            Icon(Icons.Outlined.DownloadDone, stringResource(R.string.sound_downloaded), Modifier.size(16.dp))
                        }}
                        else -> {{ Icon(Icons.Outlined.CloudDownload, stringResource(R.string.sound_not_downloaded), Modifier.size(16.dp)) }}
                    },
                )
            }
        }
        val playing = previewer.playingId
        if (playing != null) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.sound_playing, AdhanSound.byId(playing)?.displayName ?: ""),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { previewer.stop() }) { Text(stringResource(R.string.action_stop)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
