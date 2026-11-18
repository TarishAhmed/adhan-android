package org.tarish.adhan.ui.quran

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import org.tarish.adhan.data.quran.QuranPrefs
import org.tarish.adhan.data.quran.QuranSettings
import org.tarish.adhan.audio.QuranPlayer
import org.tarish.adhan.domain.quran.Reciter
import org.tarish.adhan.domain.quran.TranslationAudio
import org.tarish.adhan.domain.quran.TranslationEdition
import org.tarish.adhan.ui.components.SectionHeader
import org.tarish.adhan.ui.components.SettingRow
import org.tarish.adhan.work.QuranDownloadWorker
import org.tarish.adhan.work.WorkScheduler
import javax.inject.Inject

/** [running] carries "done of total" while the offline download is in progress. */
data class QuranSettingsUi(
    val prefs: QuranPrefs = QuranPrefs(),
    val downloaded: Int = 0,
    val running: Pair<Int, Int>? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QuranSettingsViewModel @Inject constructor(
    @ApplicationContext context: android.content.Context,
    private val settings: QuranSettings,
    private val workScheduler: WorkScheduler,
    private val player: QuranPlayer,
    repo: QuranRepository,
) : ViewModel(), QuranOptionActions {
    private val work = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WorkScheduler.QURAN_DOWNLOAD).map { infos ->
        infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }?.let {
            it.progress.getInt(QuranDownloadWorker.KEY_DONE, 0) to it.progress.getInt(QuranDownloadWorker.KEY_TOTAL, SURAH_COUNT)
        }
    }

    val ui = combine(settings.prefs, settings.edition.flatMapLatest(repo::completeSurahs), work, ::QuranSettingsUi)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuranSettingsUi())

    private fun set(block: suspend QuranSettings.() -> Unit) { viewModelScope.launch { settings.block() } }
    override fun setNazira(v: Boolean) = set { setNazira(v) }
    override fun setShowTranslation(v: Boolean) = set { setShowTranslation(v) }
    override fun setEdition(e: TranslationEdition) = set { setEdition(e) }
    override fun setTransliteration(v: Boolean) = set { setTransliteration(v) }
    override fun setAudio(v: Boolean) { if (!v) player.stop(); set { setAudio(v) } }
    override fun setReciter(r: Reciter) = set { setReciter(r) }
    override fun setTranslationAudio(t: TranslationAudio) = set { setTranslationAudio(t) }
    fun download() { workScheduler.downloadQuran(ui.value.prefs.edition) }

    companion object { const val SURAH_COUNT = 114 }
}

@Composable
fun QuranSettingsSection(vm: QuranSettingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val complete = ui.downloaded >= QuranSettingsViewModel.SURAH_COUNT

    Column {
        SectionHeader(stringResource(R.string.quran_title))
        QuranOptions(ui.prefs, vm)
        if (!ui.prefs.showsTranslation) return@Column
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
}
