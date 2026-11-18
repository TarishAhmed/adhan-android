package org.tarish.adhan.ui.quran

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tarish.adhan.R
import org.tarish.adhan.domain.quran.VerseRef
import org.tarish.adhan.ui.components.AdhanCard
import org.tarish.adhan.ui.components.SectionHeader
import org.tarish.adhan.ui.components.StateView
import org.tarish.adhan.ui.theme.Radius
import org.tarish.adhan.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranSearchScreen(onBack: () -> Unit, onOpen: (VerseRef) -> Unit, vm: QuranSearchViewModel = hiltViewModel()) {
    val query by vm.query.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val highlight = MaterialTheme.colorScheme.tertiaryContainer

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quran_search_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text(stringResource(R.string.quran_search_words_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.xl),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
                if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg))
            }
            if (ui.searched) {
                item {
                    SectionHeader(
                        if (ui.offline) pluralStringResource(R.plurals.quran_search_offline, ui.hits.size, ui.hits.size)
                        else pluralStringResource(R.plurals.quran_search_results, ui.hits.size, ui.hits.size)
                    )
                }
                if (ui.hits.isEmpty()) item {
                    StateView(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.quran_search_none_title),
                        body = stringResource(if (ui.offline) R.string.quran_search_none_offline else R.string.quran_search_none_body),
                    )
                }
            }
            items(ui.hits, key = { (h, _) -> "${h.surah}:${h.verse}" }) { (hit, surah) ->
                AdhanCard(
                    onClick = { onOpen(VerseRef(hit.surah, hit.verse)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                ) {
                    Column {
                        Text(
                            stringResource(R.string.quran_verse_title, surah.name, hit.surah, hit.verse),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(highlighted(hit.text, highlight), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
            }
        }
    }
}
