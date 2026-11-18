package org.tarish.adhan.ui.quran

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tarish.adhan.data.quran.QuranRepository
import org.tarish.adhan.data.quran.QuranSettings
import org.tarish.adhan.domain.quran.AhmadiNumbering
import org.tarish.adhan.domain.quran.SearchHit
import org.tarish.adhan.domain.quran.Surah
import org.tarish.adhan.domain.quran.TranslationEdition
import org.tarish.adhan.domain.quran.Verse
import org.tarish.adhan.domain.quran.VerseRef
import org.tarish.adhan.ui.navigation.QuranSearch
import org.tarish.adhan.ui.navigation.SurahReader
import javax.inject.Inject

private fun <T> kotlinx.coroutines.flow.Flow<T>.state(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

// ---- Quran home ----

data class BookmarkItem(val ref: VerseRef, val surah: Surah)

@HiltViewModel
class QuranHomeViewModel @Inject constructor(
    repo: QuranRepository,
    settings: QuranSettings,
) : ViewModel() {
    private val surahList = flow { emit(repo.surahs()) }

    val query = MutableStateFlow("")

    val surahs: StateFlow<List<Surah>?> = combine(surahList, query) { all, q ->
        val t = q.trim()
        if (t.isEmpty()) all
        else all.filter { s ->
            s.number.toString() == t || s.name.contains(t, ignoreCase = true) ||
                s.meaning.contains(t, ignoreCase = true) || s.arabicName.contains(t)
        }
    }.state(this, null)

    /** A typed "2:256" that names a real verse. */
    val reference: StateFlow<VerseRef?> = combine(surahList, query) { all, q ->
        AhmadiNumbering.parseReference(q) { n -> all.getOrNull(n - 1)?.verseCount }
    }.state(this, null)

    val lastRead: StateFlow<Pair<VerseRef, Surah>?> = combine(settings.lastRead, surahList) { r, all ->
        r?.let { ref -> all.getOrNull(ref.surah - 1)?.let { ref to it } }
    }.state(this, null)

    val bookmarks: StateFlow<List<BookmarkItem>> = combine(repo.bookmarks, surahList) { refs, all ->
        refs.mapNotNull { r -> all.getOrNull(r.surah - 1)?.let { BookmarkItem(r, it) } }
    }.state(this, emptyList())
}

// ---- Reader ----

enum class TranslationStatus { LOADING, READY, FAILED }

data class ReaderPrefs(
    val edition: TranslationEdition = TranslationEdition.DEFAULT,
    val arabicSp: Int = QuranSettings.DEFAULT_ARABIC,
    val translationSp: Int = QuranSettings.DEFAULT_TRANSLATION,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SurahReaderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: QuranRepository,
    private val settings: QuranSettings,
) : ViewModel() {
    private val route = savedState.toRoute<SurahReader>()
    val surahNumber = route.surah
    val initialVerse = route.verse

    val surah: StateFlow<Surah?> = flow { emit(repo.surahs()[surahNumber - 1]) }.state(this, null)

    val prefs: StateFlow<ReaderPrefs> =
        combine(settings.edition, settings.arabicSize, settings.translationSize, ::ReaderPrefs).state(this, ReaderPrefs())

    val verses: StateFlow<List<Verse>?> = settings.edition.distinctUntilChanged()
        .flatMapLatest { repo.observeSurah(surahNumber, it) }
        .state(this, null)

    val bookmarked: StateFlow<Set<Int>> = repo.bookmarks
        .map { list -> list.filter { it.surah == surahNumber }.map { it.verse }.toSet() }
        .state(this, emptySet())

    private val _status = MutableStateFlow(TranslationStatus.LOADING)
    val status = _status.asStateFlow()
    private var load: Job? = null

    init {
        viewModelScope.launch { settings.edition.distinctUntilChanged().collect { loadTranslation(it) } }
    }

    private fun loadTranslation(edition: TranslationEdition) {
        load?.cancel()
        load = viewModelScope.launch {
            _status.value = TranslationStatus.LOADING
            val r = repo.ensureTranslation(surahNumber, edition)
            _status.value = if (r.isSuccess) TranslationStatus.READY else TranslationStatus.FAILED
        }
    }

    fun retry() { viewModelScope.launch { loadTranslation(settings.edition.first()) } }

    fun onVerseVisible(verse: Int) { viewModelScope.launch { settings.setLastRead(VerseRef(surahNumber, verse)) } }

    fun setBookmarked(verse: Int, on: Boolean) { viewModelScope.launch { repo.setBookmarked(VerseRef(surahNumber, verse), on) } }

    fun setEdition(e: TranslationEdition) { viewModelScope.launch { settings.setEdition(e) } }
    fun setArabicSize(sp: Int) { viewModelScope.launch { settings.setArabicSize(sp) } }
    fun setTranslationSize(sp: Int) { viewModelScope.launch { settings.setTranslationSize(sp) } }
}

// ---- Search ----

data class SearchUi(
    val loading: Boolean = false,
    val hits: List<Pair<SearchHit, Surah>> = emptyList(),
    val offline: Boolean = false,
    val searched: Boolean = false,
)

@HiltViewModel
class QuranSearchViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: QuranRepository,
    private val settings: QuranSettings,
) : ViewModel() {
    val query = MutableStateFlow(savedState.toRoute<QuranSearch>().query)
    private val _ui = MutableStateFlow(SearchUi())
    val ui = _ui.asStateFlow()
    private var job: Job? = null

    init { if (query.value.isNotBlank()) search() }

    fun search() {
        val q = query.value.trim()
        if (q.length < 2) return
        job?.cancel()
        job = viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val surahs = repo.surahs()
            val r = repo.search(settings.edition.first(), q)
            _ui.value = SearchUi(
                hits = r.hits.mapNotNull { h -> surahs.getOrNull(h.surah - 1)?.let { h to it } },
                offline = r.offline,
                searched = true,
            )
        }
    }
}
