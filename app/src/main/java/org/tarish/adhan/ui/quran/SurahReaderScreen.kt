package org.tarish.adhan.ui.quran

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.tarish.adhan.R
import org.tarish.adhan.audio.QuranPlayback
import org.tarish.adhan.data.quran.QuranPrefs
import org.tarish.adhan.data.quran.QuranSettings
import org.tarish.adhan.domain.quran.AhmadiNumbering
import org.tarish.adhan.domain.quran.Ruku
import org.tarish.adhan.domain.quran.Surah
import org.tarish.adhan.domain.quran.TranslationEdition
import org.tarish.adhan.domain.quran.Verse
import org.tarish.adhan.ui.components.AdhanCard
import org.tarish.adhan.ui.components.LocalAppSnackbar
import org.tarish.adhan.ui.components.SettingRow
import org.tarish.adhan.ui.components.Skeleton
import org.tarish.adhan.ui.components.StarBadge
import org.tarish.adhan.ui.components.geometricPattern
import org.tarish.adhan.ui.theme.NotoSerif
import org.tarish.adhan.ui.theme.Radius
import org.tarish.adhan.ui.theme.Spacing
import org.tarish.adhan.ui.theme.arabicLabel
import org.tarish.adhan.ui.theme.extendedColors
import org.tarish.adhan.ui.theme.quranText
import org.tarish.adhan.ui.theme.tabular

/** Readable line length on tablets and in landscape. */
private val MAX_READING_WIDTH = 720.dp

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun SurahReaderScreen(onBack: () -> Unit, vm: SurahReaderViewModel = hiltViewModel()) {
    val surah by vm.surah.collectAsStateWithLifecycle()
    val verses by vm.verses.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val bookmarked by vm.bookmarked.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = if (vm.initialVerse > 1) vm.initialVerse else 0)
    val scope = rememberCoroutineScope()
    var actionsFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var showJump by rememberSaveable { mutableStateOf(false) }
    var showText by rememberSaveable { mutableStateOf(false) }
    val layout = remember(verses, prefs.nazira) { ReaderLayout(if (prefs.nazira) Ruku.group(verses.orEmpty()) else null) }
    val currentLayout by rememberUpdatedState(layout)
    /** The verse at the top of the screen, kept across switching Nazira mode on and off. */
    var anchor by rememberSaveable { mutableIntStateOf(vm.initialVerse) }

    LaunchedEffect(list) {
        snapshotFlow { currentLayout.verseAt(list.firstVisibleItemIndex) }
            .debounce(600)
            .collect { anchor = it; vm.onVerseVisible(it) }
    }
    LaunchedEffect(prefs.nazira, verses != null) {
        if (verses != null) list.scrollToItem(if (anchor <= 1) 0 else layout.indexOf(anchor))
    }
    // Keep the verse being recited on screen.
    val playingVerse = playback?.takeIf { it.playing }?.verse
    LaunchedEffect(playingVerse) {
        val n = playingVerse ?: return@LaunchedEffect
        val index = layout.indexOf(n)
        val info = list.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        // Scroll unless the verse already starts in the upper two thirds of the screen; a rukūʿ is
        // left alone while any of it is showing, since its verses follow on within the card.
        val above = item != null && item.offset < 0 && !layout.nazira
        if (item == null || above || item.offset > info.viewportEndOffset * 2 / 3) list.animateScrollToItem(index)
    }
    val snackbar = LocalAppSnackbar.current
    val audioFailed = stringResource(R.string.quran_audio_failed)
    LaunchedEffect(playback?.failed) { if (playback?.failed == true) snackbar.show(audioFailed) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(surah?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(
                                R.string.quran_reader_subtitle, vm.surahNumber,
                                when {
                                    prefs.nazira -> stringResource(R.string.quran_nazira)
                                    prefs.showsTranslation -> prefs.edition.label
                                    else -> stringResource(R.string.quran_arabic_only)
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { showJump = true }) { Icon(Icons.Outlined.FormatListNumbered, stringResource(R.string.quran_jump)) }
                    IconButton(onClick = { showText = true }) { Icon(Icons.Outlined.TextFields, stringResource(R.string.quran_reading_settings)) }
                },
            )
        },
        bottomBar = {
            if (prefs.audio) AudioBar(
                playback = playback,
                repeat = prefs.repeat,
                onPlay = { vm.play(layout.verseAt(list.firstVisibleItemIndex)) },
                onToggle = vm::togglePlayback,
                onSkip = vm::skip,
                onRepeat = vm::cycleRepeat,
                onStop = vm::stopPlayback,
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        val s = surah
        val vs = verses
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") {
                Column(Modifier.widthIn(max = MAX_READING_WIDTH)) {
                    if (s != null) SurahHeader(s)
                    if (prefs.showsTranslation && status == TranslationStatus.FAILED && vs?.any { it.translation == null } == true) {
                        OfflineBanner(vm::retry)
                    }
                    if (status == TranslationStatus.LOADING) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg))
                }
            }
            if (vs == null) items(4) { Skeleton(Modifier.padding(Spacing.lg).widthIn(max = MAX_READING_WIDTH).fillMaxWidth().height(160.dp)) }
            else if (layout.rukus != null) itemsIndexed(layout.rukus, key = { _, r -> "r${r.first().number}" }) { i, r ->
                RukuCard(
                    number = i + 1,
                    verses = r,
                    prefs = prefs,
                    reciting = playback?.verse,
                    onPlay = if (prefs.audio) vm::play else null,
                    onVerse = { actionsFor = it },
                    modifier = Modifier.widthIn(max = MAX_READING_WIDTH),
                )
            }
            else items(vs, key = { it.number }) { v ->
                AyahCard(
                    verse = v,
                    prefs = prefs,
                    translationLoading = status == TranslationStatus.LOADING,
                    bookmarked = v.number in bookmarked,
                    reciting = playback?.verse == v.number,
                    onPlay = if (prefs.audio) ({ vm.play(v.number) }) else null,
                    onBookmark = { vm.setBookmarked(v.number, v.number !in bookmarked) },
                    onMore = { actionsFor = v.number },
                    modifier = Modifier.widthIn(max = MAX_READING_WIDTH),
                )
            }
            item { Spacer(Modifier.height(Spacing.huge)) }
        }
    }

    val s = surah
    actionsFor?.let { n ->
        val v = verses?.getOrNull(n - 1)
        if (v != null && s != null) VerseActionsSheet(
            surah = s,
            verse = v,
            edition = prefs.edition,
            bookmarked = n in bookmarked,
            onBookmark = { vm.setBookmarked(n, n !in bookmarked) },
            onPlay = if (prefs.audio) ({ vm.play(n) }) else null,
            onDismiss = { actionsFor = null },
        )
    }
    if (showJump && s != null) JumpDialog(s, onDismiss = { showJump = false }) { n ->
        showJump = false
        scope.launch { list.animateScrollToItem(layout.indexOf(n)) }
    }
    if (showText) {
        ReadingSettingsSheet(prefs, vm, vm::setArabicSize, vm::setTranslationSize) { showText = false }
    }
}

/**
 * Which list item shows which verse. Item 0 is the surah header; then one item per verse, or in
 * Nazira mode one per rukūʿ ([rukus]).
 */
private class ReaderLayout(val rukus: List<List<Verse>>?) {
    val nazira get() = rukus != null

    fun indexOf(verse: Int): Int =
        rukus?.let { r -> 1 + r.indexOfFirst { verse <= it.last().number }.coerceAtLeast(0) } ?: verse

    fun verseAt(index: Int): Int =
        rukus?.let { it.getOrNull(index - 1)?.first()?.number ?: 1 } ?: index.coerceAtLeast(1)
}

/**
 * Nazira mode: one rukūʿ as continuous Arabic, the way a printed Mushaf sets it, with each verse's
 * own end ornament between verses. Tapping a verse opens its actions; the one being recited is
 * highlighted. A Bismillah verse sits centred above the passage.
 */
@Composable
private fun RukuCard(
    number: Int,
    verses: List<Verse>,
    prefs: QuranPrefs,
    reciting: Int?,
    onPlay: ((Int) -> Unit)?,
    onVerse: (Int) -> Unit,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val first = verses.first()
    val bismillah = first.takeIf { AhmadiNumbering.isBismillah(it.surah, it.number) }
    val body = if (bismillah != null) verses.drop(1) else verses
    val style = quranText(prefs.readingArabicSp).copy(textDirection = TextDirection.Rtl, color = cs.onSurface)

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .clip(RoundedCornerShape(Radius.xl))
            .background(cs.surfaceContainerLow)
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarBadge(number.toString(), MaterialTheme.typography.labelMedium.tabular(), size = 36.dp)
            Text(
                stringResource(R.string.quran_ruku, number, first.number, verses.last().number),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = Spacing.sm),
            )
            if (onPlay != null) IconButton(onClick = { onPlay(first.number) }) {
                Icon(Icons.Outlined.PlayCircle, stringResource(R.string.quran_play_from, first.number), tint = cs.primary)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        if (bismillah != null) Text(
            passage(listOf(bismillah), reciting, cs.secondaryContainer, onVerse),
            style = style.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth(),
        )
        if (body.isNotEmpty()) Text(
            passage(body, reciting, cs.secondaryContainer, onVerse),
            // Start, not Justify: Compose justifies by widening spaces only (no kashida), which leaves gaps.
            style = style.copy(textAlign = TextAlign.Start),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val PLAIN_LINK = TextLinkStyles(SpanStyle())

private fun passage(verses: List<Verse>, reciting: Int?, highlight: Color, onVerse: (Int) -> Unit) = buildAnnotatedString {
    verses.forEachIndexed { i, v ->
        if (i > 0) append(' ')
        // Plain styles: the verses read as Mushaf text, not as links.
        withLink(LinkAnnotation.Clickable("v${v.number}", PLAIN_LINK) { onVerse(v.number) }) {
            if (v.number == reciting) withStyle(SpanStyle(background = highlight)) { append(v.arabic) } else append(v.arabic)
        }
    }
}

@Composable
private fun SurahHeader(s: Surah) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(Spacing.lg)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(cs.primaryContainer)
            .geometricPattern(extendedColors.ornament, alpha = 0.16f)
            .padding(vertical = Spacing.xxl, horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(s.arabicName, style = arabicLabel(40), color = cs.onPrimaryContainer, modifier = Modifier.clearAndSetSemantics { })
        Text(s.name, style = MaterialTheme.typography.headlineSmall, fontFamily = NotoSerif, color = cs.onPrimaryContainer)
        Text(
            stringResource(
                R.string.quran_header_detail,
                s.meaning,
                stringResource(if (s.makki) R.string.quran_makki else R.string.quran_madani),
                pluralStringResource(R.plurals.quran_verses, s.verseCount, s.verseCount),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onPrimaryContainer.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AdhanCard(color = cs.tertiaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, null, tint = cs.onTertiaryContainer)
            Text(
                stringResource(R.string.quran_translation_offline),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onTertiaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.md),
            )
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
        }
    }
}

@Composable
private fun AyahCard(
    verse: Verse,
    prefs: QuranPrefs,
    translationLoading: Boolean,
    bookmarked: Boolean,
    reciting: Boolean,
    onPlay: (() -> Unit)?,
    onBookmark: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val bismillah = AhmadiNumbering.isBismillah(verse.surah, verse.number)
    var notesOpen by rememberSaveable(verse.surah, verse.number) { mutableStateOf(false) }
    val t = verse.translation
    val actionsLabel = stringResource(R.string.quran_verse_actions)

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .clip(RoundedCornerShape(Radius.xl))
            .background(
                when {
                    reciting -> cs.secondaryContainer
                    bismillah -> cs.surfaceContainer
                    else -> cs.surfaceContainerLow
                },
            )
            // Long-press is a shortcut only; the ⋮ button is the accessible path to the same actions.
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMore() })
            }
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarBadge(verse.number.toString(), MaterialTheme.typography.labelMedium.tabular(), size = 36.dp)
            Text(
                stringResource(R.string.quran_verse_ref, verse.surah, verse.number),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = Spacing.sm),
            )
            if (onPlay != null) IconButton(onClick = onPlay) {
                Icon(Icons.Outlined.PlayCircle, stringResource(R.string.quran_play_from, verse.number), tint = cs.primary)
            }
            IconButton(onClick = onBookmark) {
                Icon(
                    if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    stringResource(if (bookmarked) R.string.quran_remove_bookmark else R.string.quran_add_bookmark),
                    tint = if (bookmarked) cs.tertiary else cs.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, actionsLabel) }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            // The text carries its own end ornament as alislam prints it, e.g. ﴿۵﴾ or ﴿٪۷﴾ (rukūʿ).
            verse.arabic,
            style = quranText(prefs.readingArabicSp).copy(textDirection = TextDirection.Rtl, textAlign = if (bismillah) TextAlign.Center else TextAlign.Start),
            color = cs.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        if (prefs.showsTransliteration && verse.transliteration != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                verse.transliteration,
                style = TextStyle(
                    fontSize = (prefs.translationSp - 1).sp,
                    lineHeight = (prefs.translationSp * 1.45f).sp,
                    fontStyle = FontStyle.Italic,
                    textDirection = TextDirection.Ltr,
                ),
                color = cs.primary,
                textAlign = if (bismillah) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (prefs.showsTranslation) Spacer(Modifier.height(Spacing.md))
        when {
            !prefs.showsTranslation -> Unit
            t != null -> {
                Text(
                    footnoteText(t.text, cs.tertiary),
                    style = TextStyle(
                        fontSize = prefs.translationSp.sp,
                        lineHeight = (prefs.translationSp * 1.5f).sp,
                        textDirection = if (prefs.edition.rtl) TextDirection.Rtl else TextDirection.Content,
                    ),
                    color = cs.onSurface,
                    textAlign = if (bismillah) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (t.notes.isNotEmpty()) {
                    TextButton(onClick = { notesOpen = !notesOpen }, modifier = Modifier.padding(top = Spacing.xs)) {
                        Text(pluralStringResource(
                            if (notesOpen) R.plurals.quran_hide_notes else R.plurals.quran_show_notes, t.notes.size, t.notes.size,
                        ))
                    }
                    AnimatedVisibility(notesOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            t.notes.forEach { n ->
                                Row {
                                    Text(n.ref, style = MaterialTheme.typography.labelMedium, color = cs.tertiary, modifier = Modifier.padding(end = Spacing.sm))
                                    Text(
                                        AnnotatedString.fromHtml(n.body),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = (prefs.translationSp - 2).sp),
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            translationLoading -> Skeleton(Modifier.fillMaxWidth().height(48.dp), corner = Radius.sm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerseActionsSheet(
    surah: Surah,
    verse: Verse,
    edition: TranslationEdition,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onPlay: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.quran_copied)
    val share = shareText(surah, verse, edition)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.quran_verse_title, surah.name, verse.surah, verse.number),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.sm),
        )
        if (onPlay != null) SheetRow(Icons.Outlined.PlayCircle, stringResource(R.string.quran_play_from_here)) { onPlay(); onDismiss() }
        SheetRow(
            if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            stringResource(if (bookmarked) R.string.quran_remove_bookmark else R.string.quran_add_bookmark),
        ) { onBookmark(); onDismiss() }
        SheetRow(Icons.Outlined.ContentCopy, stringResource(R.string.quran_copy)) {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(surah.name, share))) }
            snackbar.show(copied)
            onDismiss()
        }
        SheetRow(Icons.Outlined.Share, stringResource(R.string.quran_share)) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, share)
            ctx.startActivity(Intent.createChooser(send, null))
            onDismiss()
        }
        Spacer(Modifier.height(Spacing.xxl).navigationBarsPadding())
    }
}

@Composable
private fun shareText(surah: Surah, verse: Verse, edition: TranslationEdition): String {
    val ref = stringResource(R.string.quran_share_ref, surah.name, verse.surah, verse.number, edition.label)
    return listOfNotNull(verse.arabic, verse.translation?.let { plainTranslation(it.text) }, ref).joinToString("\n\n")
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun JumpDialog(surah: Surah, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val n = text.toIntOrNull()?.takeIf { it in 1..surah.verseCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quran_jump)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(R.string.quran_jump_label, surah.verseCount)) },
                singleLine = true,
                isError = text.isNotEmpty() && n == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { n?.let(onJump) }),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        confirmButton = { TextButton(onClick = { n?.let(onJump) }, enabled = n != null) { Text(stringResource(R.string.quran_go)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingSettingsSheet(
    prefs: QuranPrefs,
    actions: QuranOptionActions,
    onArabic: (Int) -> Unit,
    onTranslation: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Fully expanded so the size previews aren't left under the gesture bar.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxl).navigationBarsPadding()) {
            Text(
                stringResource(R.string.quran_reading_settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.xxl),
            )
            Spacer(Modifier.height(Spacing.sm))
            QuranOptions(prefs, actions)
            Column(Modifier.padding(horizontal = Spacing.xxl)) {
                Spacer(Modifier.height(Spacing.lg))
                SizeSlider(stringResource(R.string.quran_arabic_size), prefs.arabicSp, QuranSettings.ARABIC_RANGE, onArabic)
                Text(
                    "بِسۡمِ اللّٰہِ الرَّحۡمٰنِ الرَّحِیۡمِ",
                    style = quranText(prefs.readingArabicSp).copy(textDirection = TextDirection.Rtl),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (prefs.showsTranslation || prefs.showsTransliteration) {
                    SizeSlider(stringResource(R.string.quran_translation_size), prefs.translationSp, QuranSettings.TRANSLATION_RANGE, onTranslation)
                    Text(
                        stringResource(R.string.quran_size_sample),
                        style = TextStyle(fontSize = prefs.translationSp.sp, lineHeight = (prefs.translationSp * 1.5f).sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Compact player shown while audio is on: before playback a single "Play" button (from the verse
 * at the top of the screen); during playback the verse, previous/next, repeat count and stop.
 */
@Composable
private fun AudioBar(
    playback: QuranPlayback?,
    repeat: Int,
    onPlay: () -> Unit,
    onToggle: () -> Unit,
    onSkip: (Int) -> Unit,
    onRepeat: () -> Unit,
    onStop: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (playback == null) {
                FilledTonalButton(onClick = onPlay, modifier = Modifier.padding(start = Spacing.sm)) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.quran_play))
                }
                Spacer(Modifier.weight(1f))
            } else {
                IconButton(onClick = { onSkip(-1) }) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.quran_previous_verse)) }
                IconButton(onClick = onToggle) {
                    if (playback.loading && playback.playing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Icon(
                        if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (playback.playing) R.string.quran_pause else R.string.quran_play),
                    )
                }
                IconButton(onClick = { onSkip(1) }) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.quran_next_verse)) }
                Text(
                    stringResource(
                        if (playback.translation) R.string.quran_now_translation else R.string.quran_now_verse,
                        playback.surah, playback.verse,
                    ),
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs),
                )
            }
            val repeatLabel = pluralStringResource(R.plurals.quran_repeat_a11y, repeat, repeat)
            TextButton(onClick = onRepeat, modifier = Modifier.clearAndSetSemantics { contentDescription = repeatLabel; role = Role.Button }) {
                Icon(Icons.Rounded.Repeat, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.quran_repeat_times, repeat))
            }
            if (playback != null) IconButton(onClick = onStop) { Icon(Icons.Rounded.Close, stringResource(R.string.quran_stop)) }
        }
    }
}

@Composable
private fun SizeSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text("$value", style = MaterialTheme.typography.labelLarge.tabular())
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = range.last - range.first - 1,
    )
}
