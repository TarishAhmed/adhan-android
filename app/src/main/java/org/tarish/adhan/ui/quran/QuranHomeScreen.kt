package org.tarish.adhan.ui.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tarish.adhan.R
import org.tarish.adhan.domain.quran.Surah
import org.tarish.adhan.domain.quran.VerseRef
import org.tarish.adhan.ui.components.EyebrowLabel
import org.tarish.adhan.ui.components.Skeleton
import org.tarish.adhan.ui.components.StarBadge
import org.tarish.adhan.ui.components.StateView
import org.tarish.adhan.ui.components.geometricPattern
import org.tarish.adhan.ui.theme.NotoSerif
import org.tarish.adhan.ui.theme.Radius
import org.tarish.adhan.ui.theme.Spacing
import org.tarish.adhan.ui.theme.arabicLabel
import org.tarish.adhan.ui.theme.extendedColors
import org.tarish.adhan.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranHomeScreen(
    onOpen: (VerseRef) -> Unit,
    onSearch: (String) -> Unit,
    vm: QuranHomeViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val surahs by vm.surahs.collectAsStateWithLifecycle()
    val reference by vm.reference.collectAsStateWithLifecycle()
    val lastRead by vm.lastRead.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.quran_title), fontFamily = NotoSerif) }) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (query.isBlank()) lastRead?.let { (ref, surah) ->
                item { ContinueCard(ref, surah) { onOpen(ref) } }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text(stringResource(R.string.quran_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) {
                            Icon(Icons.Rounded.Clear, stringResource(R.string.action_clear))
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.xl),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); if (query.isNotBlank()) onSearch(query.trim()) }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
            if (query.isNotBlank()) {
                reference?.let { ref ->
                    item {
                        ActionRow(stringResource(R.string.quran_go_to, ref.surah, ref.verse), Icons.AutoMirrored.Rounded.MenuBook) { onOpen(ref) }
                    }
                }
                if (query.trim().length >= 2) item {
                    ActionRow(stringResource(R.string.quran_search_translation, query.trim()), Icons.Rounded.Search) { onSearch(query.trim()) }
                }
            } else {
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                        listOf(R.string.quran_tab_surahs, R.string.quran_tab_bookmarks).forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = tab == i,
                                onClick = { tab = i },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(stringResource(label)) }
                        }
                    }
                }
            }

            if (tab == 1 && query.isBlank()) {
                if (bookmarks.isEmpty()) item {
                    StateView(
                        icon = Icons.Outlined.BookmarkBorder,
                        title = stringResource(R.string.quran_bookmarks_empty_title),
                        body = stringResource(R.string.quran_bookmarks_empty_body),
                    )
                }
                items(bookmarks, key = { "${it.ref.surah}:${it.ref.verse}" }) { b ->
                    SurahRow(b.surah, subtitle = stringResource(R.string.quran_verse_n, b.ref.verse)) { onOpen(b.ref) }
                }
            } else {
                val list = surahs
                if (list == null) items(8) { Skeleton(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs).fillMaxWidth().heightIn(min = 64.dp)) }
                else items(list, key = { it.number }) { s ->
                    SurahRow(s, subtitle = surahSubtitle(s)) { onOpen(VerseRef(s.number, 1)) }
                }
            }
            item { Spacer(Modifier.padding(bottom = Spacing.xxxl)) }
        }
    }
}

@Composable
private fun surahSubtitle(s: Surah): String =
    stringResource(R.string.quran_surah_subtitle, s.meaning, pluralStringResource(R.plurals.quran_verses, s.verseCount, s.verseCount))

@Composable
private fun ContinueCard(ref: VerseRef, surah: Surah, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val label = stringResource(R.string.quran_continue)
    val detail = stringResource(R.string.quran_continue_detail, surah.name, ref.verse)
    Column(
        Modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(cs.primaryContainer)
            .geometricPattern(extendedColors.ornament, alpha = 0.14f)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(Spacing.xl)
            .semantics(mergeDescendants = true) { contentDescription = "$label. $detail" },
    ) {
        EyebrowLabel(label, color = cs.onPrimaryContainer.copy(alpha = 0.75f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(surah.name, style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer)
                Text(stringResource(R.string.quran_verse_n, ref.verse), style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
            }
            Text(surah.arabicName, style = arabicLabel(30), color = cs.onPrimaryContainer)
        }
    }
}

@Composable
private fun SurahRow(s: Surah, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(s.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle) },
        leadingContent = { StarBadge(s.number.toString(), MaterialTheme.typography.labelMedium.tabular(), size = 44.dp) },
        trailingContent = { Text(s.arabicName, style = arabicLabel(22), color = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ActionRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
