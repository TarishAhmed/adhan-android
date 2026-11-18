package org.tarish.adhan.ui.quran

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SpeakerNotes
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.tarish.adhan.R
import org.tarish.adhan.data.quran.QuranPrefs
import org.tarish.adhan.domain.quran.Reciter
import org.tarish.adhan.domain.quran.TranslationAudio
import org.tarish.adhan.domain.quran.TranslationEdition
import org.tarish.adhan.ui.components.SettingRow
import org.tarish.adhan.ui.theme.Spacing

/** Implemented by the view models behind [QuranOptions]. */
interface QuranOptionActions {
    fun setNazira(v: Boolean)
    fun setShowTranslation(v: Boolean)
    fun setEdition(e: TranslationEdition)
    fun setTransliteration(v: Boolean)
    fun setAudio(v: Boolean)
    fun setReciter(r: Reciter)
    fun setTranslationAudio(t: TranslationAudio)
}

private enum class Picker { TRANSLATION, RECITER, TRANSLATION_AUDIO }

/**
 * The Quran's optional features as one short list: a switch each, with a feature's own choices
 * appearing under it only while it is on. Nazira mode hides the translation, transliteration and
 * translation-audio rows, since it is the Arabic alone.
 */
@Composable
fun QuranOptions(prefs: QuranPrefs, actions: QuranOptionActions) {
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }

    SettingRow(
        title = stringResource(R.string.quran_nazira),
        icon = Icons.Outlined.AutoStories,
        subtitle = stringResource(R.string.quran_nazira_detail),
        checked = prefs.nazira,
        onCheckedChange = actions::setNazira,
    )
    if (!prefs.nazira) {
        SettingRow(
            title = stringResource(R.string.quran_show_translation),
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            subtitle = if (prefs.showTranslation) null else stringResource(R.string.quran_arabic_only),
            checked = prefs.showTranslation,
            onCheckedChange = actions::setShowTranslation,
        )
        if (prefs.showTranslation) SettingRow(
            title = stringResource(R.string.quran_translation),
            icon = Icons.Outlined.Translate,
            subtitle = prefs.edition.label,
            onClick = { picker = Picker.TRANSLATION },
        )
        SettingRow(
            title = stringResource(R.string.quran_transliteration),
            icon = Icons.Outlined.TextFormat,
            subtitle = stringResource(R.string.quran_transliteration_detail),
            checked = prefs.transliteration,
            onCheckedChange = actions::setTransliteration,
        )
    }
    SettingRow(
        title = stringResource(R.string.quran_audio),
        icon = Icons.Outlined.Headphones,
        subtitle = stringResource(R.string.quran_audio_detail),
        checked = prefs.audio,
        onCheckedChange = actions::setAudio,
    )
    if (prefs.audio) {
        SettingRow(
            title = stringResource(R.string.quran_reciter),
            icon = Icons.Outlined.RecordVoiceOver,
            subtitle = prefs.reciter.displayName,
            onClick = { picker = Picker.RECITER },
        )
        if (!prefs.nazira) SettingRow(
            title = stringResource(R.string.quran_translation_audio),
            icon = Icons.Outlined.SpeakerNotes,
            subtitle = prefs.translationAudio.label(),
            onClick = { picker = Picker.TRANSLATION_AUDIO },
        )
    }

    when (picker) {
        Picker.TRANSLATION -> TranslationPickerSheet(prefs.edition, onPick = actions::setEdition, onDismiss = { picker = null })
        Picker.RECITER -> ChoiceSheet(
            title = stringResource(R.string.quran_reciter),
            options = Reciter.entries,
            selected = prefs.reciter,
            label = { it.displayName },
            detail = { if (it == Reciter.DEFAULT) stringResource(R.string.quran_reciter_recommended) else null },
            onPick = actions::setReciter,
            onDismiss = { picker = null },
        )
        Picker.TRANSLATION_AUDIO -> ChoiceSheet(
            title = stringResource(R.string.quran_translation_audio),
            options = TranslationAudio.entries,
            selected = prefs.translationAudio,
            label = { it.label() },
            detail = { if (it == TranslationAudio.OFF) stringResource(R.string.quran_translation_audio_off_detail) else it.language },
            onPick = actions::setTranslationAudio,
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

@Composable
private fun TranslationAudio.label() = if (this == TranslationAudio.OFF) stringResource(R.string.quran_off) else displayName

/** A short single-choice list in a bottom sheet; picking closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    detail: @Composable (T) -> String?,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.xxl).semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.sm))
            options.forEach { o ->
                val on = o == selected
                ListItem(
                    headlineContent = { Text(label(o)) },
                    supportingContent = detail(o)?.let { { Text(it) } },
                    trailingContent = if (on) ({ Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.selectable(selected = on, role = Role.RadioButton) { onPick(o); onDismiss() },
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
