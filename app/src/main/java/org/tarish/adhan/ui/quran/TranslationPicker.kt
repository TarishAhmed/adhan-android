package org.tarish.adhan.ui.quran

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.tarish.adhan.R
import org.tarish.adhan.domain.quran.TranslationEdition
import org.tarish.adhan.ui.theme.Radius
import org.tarish.adhan.ui.theme.Spacing

/**
 * One compact row showing the current translation; tapping opens [TranslationPickerSheet]. Keeps
 * the reading-settings sheet the same size however many translations exist.
 */
@Composable
fun TranslationRow(edition: TranslationEdition, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.quran_translation)) },
        supportingContent = { Text(edition.label) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.selectable(selected = false, enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

/** Searchable list of all editions, grouped by language with sticky headers. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TranslationPickerSheet(current: TranslationEdition, onPick: (TranslationEdition) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val groups = TranslationEdition.entries
        .filter { q.isEmpty() || it.language.contains(q, ignoreCase = true) || it.translator.contains(q, ignoreCase = true) }
        .groupBy { it.language }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            stringResource(R.string.quran_choose_translation),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.xxl).semantics { heading() },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.quran_translation_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.xl),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.75f).navigationBarsPadding()) {
            groups.forEach { (language, editions) ->
                stickyHeader(key = "h-$language") {
                    Text(
                        language,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = Spacing.xxl, vertical = Spacing.sm)
                            .semantics { heading() },
                    )
                }
                items(editions, key = { it.key }) { e ->
                    val selected = e == current
                    ListItem(
                        headlineContent = { Text(e.translator.ifBlank { stringResource(R.string.quran_translation_standard) }) },
                        trailingContent = if (selected) ({ Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { onPick(e); onDismiss() },
                    )
                }
            }
            if (groups.isEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(Spacing.xxl), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.quran_translation_none, q), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
