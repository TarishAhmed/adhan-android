package org.tarish.adhan.data.quran

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.tarish.adhan.core.di.IoDispatcher
import org.tarish.adhan.core.time.Clock
import org.tarish.adhan.data.local.BookmarkEntity
import org.tarish.adhan.data.local.QuranDao
import org.tarish.adhan.data.local.TranslationVerseEntity
import org.tarish.adhan.data.remote.ReadQuranApi
import org.tarish.adhan.domain.quran.Footnote
import org.tarish.adhan.domain.quran.SearchHit
import org.tarish.adhan.domain.quran.Surah
import org.tarish.adhan.domain.quran.Translation
import org.tarish.adhan.domain.quran.TranslationEdition
import org.tarish.adhan.domain.quran.TranslationSource
import org.tarish.adhan.domain.quran.Verse
import org.tarish.adhan.domain.quran.VerseRef
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface QuranRepository {
    suspend fun surahs(): List<Surah>
    /** Arabic is always present; each verse's translation fills in as it is cached. */
    fun observeSurah(surah: Int, edition: TranslationEdition, transliteration: Boolean = false): Flow<List<Verse>>
    /** Downloads any uncached verses of [surah], a page at a time, so the reader fills progressively. */
    suspend fun ensureTranslation(surah: Int, edition: TranslationEdition): Result<Unit>
    /** Number of surahs whose translation is fully cached. */
    fun completeSurahs(edition: TranslationEdition): Flow<Int>
    /** Online search, falling back to the cached translation when offline. */
    suspend fun search(edition: TranslationEdition, query: String): SearchResult
    val bookmarks: Flow<List<VerseRef>>
    suspend fun setBookmarked(ref: VerseRef, bookmarked: Boolean)
}

data class SearchResult(val hits: List<SearchHit>, val offline: Boolean)

@Serializable
private data class NoteJson(val ref: String, val body: String)

@Singleton
class QuranRepositoryImpl @Inject constructor(
    private val assets: QuranAssets,
    private val source: TranslationSource,
    private val dao: QuranDao,
    private val json: Json,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : QuranRepository {

    private val notesSerializer = ListSerializer(NoteJson.serializer())
    /** One download per surah+edition at a time; a second caller waits and then finds it cached. */
    private val locks = ConcurrentHashMap<Pair<Int, TranslationEdition>, Mutex>()

    override suspend fun surahs(): List<Surah> = assets.surahs()

    override fun observeSurah(surah: Int, edition: TranslationEdition, transliteration: Boolean): Flow<List<Verse>> =
        combine(
            flow { emit(assets.arabic(surah) to if (transliteration) assets.transliteration(surah) else null) },
            dao.translations(edition.key, surah),
        ) { (arabic, latin), rows ->
            val byVerse = rows.associateBy { it.verse }
            arabic.mapIndexed { i, ar -> Verse(surah, i + 1, ar, byVerse[i + 1]?.toDomain(), latin?.getOrNull(i)) }
        }

    override suspend fun ensureTranslation(surah: Int, edition: TranslationEdition): Result<Unit> = withContext(io) {
        locks.getOrPut(surah to edition) { Mutex() }.withLock {
            try {
                val count = assets.arabic(surah).size
                val have = dao.cachedVerses(edition.key, surah).toSet()
                // Fetch page-aligned ranges that contain a missing verse, in reading order.
                for (start in 1..count step ReadQuranApi.PAGE) {
                    val end = minOf(start + ReadQuranApi.PAGE - 1, count)
                    if ((start..end).all { it in have }) continue
                    val page = source.verses(edition, surah, start, end)
                    dao.upsert(page.map { (v, t) -> t.toEntity(edition, surah, v) })
                }
                Result.success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("Translation %d/%s failed: %s", surah, edition.key, e.javaClass.simpleName)
                Result.failure(e)
            }
        }
    }

    override fun completeSurahs(edition: TranslationEdition): Flow<Int> = dao.cachedCounts(edition.key).map { counts ->
        val lengths = assets.surahs().associate { it.number to it.verseCount }
        counts.count { it.count >= (lengths[it.surah] ?: Int.MAX_VALUE) }
    }

    override suspend fun search(edition: TranslationEdition, query: String): SearchResult = withContext(io) {
        try {
            SearchResult(source.search(edition, query), offline = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val local = dao.search(edition.key, query, LOCAL_LIMIT).map { SearchHit(it.surah, it.verse, it.text) }
            SearchResult(local, offline = true)
        }
    }

    override val bookmarks: Flow<List<VerseRef>> = dao.bookmarks().map { list -> list.map { VerseRef(it.surah, it.verse) } }

    override suspend fun setBookmarked(ref: VerseRef, bookmarked: Boolean) = withContext(io) {
        if (bookmarked) dao.addBookmark(BookmarkEntity(ref.surah, ref.verse, clock.now().toEpochMilli()))
        else dao.removeBookmark(ref.surah, ref.verse)
    }

    private fun Translation.toEntity(edition: TranslationEdition, surah: Int, verse: Int) = TranslationVerseEntity(
        edition = edition.key,
        surah = surah,
        verse = verse,
        text = text,
        notes = if (notes.isEmpty()) "" else json.encodeToString(notesSerializer, notes.map { NoteJson(it.ref, it.body) }),
    )

    private fun TranslationVerseEntity.toDomain() = Translation(
        text = text,
        notes = if (notes.isEmpty()) emptyList() else json.decodeFromString(notesSerializer, notes).map { Footnote(it.ref, it.body) },
    )

    companion object { const val LOCAL_LIMIT = 100 }
}
