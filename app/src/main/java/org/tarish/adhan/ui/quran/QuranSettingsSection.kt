package org.tarish.adhan.ui.quran

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tarish.adhan.R
import org.tarish.adhan.data.quran.QuranRepository
import org.tarish.adhan.data.quran.QuranSettings
import org.tarish.adhan.domain.quran.TranslationEdition
import org.tarish.adhan.ui.components.SectionHeader
import org.tarish.adhan.ui.components.SettingRow
import org.tarish.adhan.work.QuranDownloadWorker
import org.tarish.adhan.work.WorkScheduler
import javax.inject.Inject

/** [running] carries "done of total" while the offline download is in progress. */
data class QuranSettingsUi(
    val showTranslation: Boolean = true,
    val edition: TranslationEdition = TranslationEdition.DEFAULT,
    val downloaded: Int = 0,
    val running: Pair<Int, Int>? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QuranSettingsViewModel @Inject constructor(
    @ApplicationContext context: android.content.Context,
    private val settings: QuranSettings,
    private val workScheduler: WorkScheduler,
    repo: QuranRepository,
) : ViewModel() {
    private val work = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WorkScheduler.QURAN_DOWNLOAD).map { infos ->
        infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }?.let {
            it.progress.getInt(QuranDownloadWorker.KEY_DONE, 0) to it.progress.getInt(QuranDownloadWorker.KEY_TOTAL, SURAH_COUNT)
        }
    }

    val ui = combine(settings.showTranslation, settings.edition, settings.edition.flatMapLatest(repo::completeSurahs), work, ::QuranSettingsUi)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuranSettingsUi())

    fun setEdition(e: TranslationEdition) { viewModelScope.launch { settings.setEdition(e) } }
    fun setShowTranslation(v: Boolean) { viewModelScope.launch { settings.setShowTranslation(v) } }
    fun download() { workScheduler.downloadQuran(ui.value.edition) }

    companion object { const val SURAH_COUNT = 114 }
}

@Composable
fun QuranSettingsSection(vm: QuranSettingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    val complete = ui.downloaded >= QuranSettingsViewModel.SURAH_COUNT

    Column {
        SectionHeader(stringResource(R.string.quran_title))
        SettingRow(
            title = stringResource(R.string.quran_show_translation),
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            subtitle = if (ui.showTranslation) ui.edition.label else stringResource(R.string.quran_arabic_only),
            checked = ui.showTranslation,
            onCheckedChange = vm::setShowTranslation,
        )
        if (!ui.showTranslation) return@Column
        SettingRow(
            title = stringResource(R.string.quran_translation),
            icon = Icons.Outlined.Translate,
            subtitle = ui.edition.label,
            onClick = { picking = true },
        )
        val running = ui.running
        SettingRow(
            title = stringResource(R.string.quran_offline),
            icon = Icons.Outlined.CloudDownload,
            subtitle = when {
                running != null -> stringResource(R.string.quran_offline_progress, running.first, running.second)
                complete -> stringResource(R.string.quran_offline_done)
                else -> stringResource(R.string.quran_offline_partial, ui.downloaded, QuranSettingsViewModel.SURAH_COUNT)
            },
            onClick = if (running == null && !complete) vm::download else null,
            trailing = when {
                running != null -> ({ CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) })
                complete -> ({ Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) })
                else -> null
            },
        )
    }

    if (picking) TranslationPickerSheet(ui.edition, onPick = vm::setEdition, onDismiss = { picking = false })
}
