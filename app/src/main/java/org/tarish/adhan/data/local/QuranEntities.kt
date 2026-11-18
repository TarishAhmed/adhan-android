package org.tarish.adhan.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A cached translation verse. [notes] is a JSON array of {ref, body}; empty for plain editions. */
@Entity(tableName = "translation_verse", primaryKeys = ["edition", "surah", "verse"])
data class TranslationVerseEntity(
    val edition: String,
    val surah: Int,
    val verse: Int,
    val text: String,
    val notes: String,
)

@Entity(tableName = "bookmark", primaryKeys = ["surah", "verse"])
data class BookmarkEntity(
    val surah: Int,
    val verse: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface QuranDao {
    @Query("SELECT * FROM translation_verse WHERE edition = :edition AND surah = :surah ORDER BY verse")
    fun translations(edition: String, surah: Int): Flow<List<TranslationVerseEntity>>

    @Query("SELECT verse FROM translation_verse WHERE edition = :edition AND surah = :surah")
    suspend fun cachedVerses(edition: String, surah: Int): List<Int>

    /** Cached verses per surah; callers compare with the surah's length to know if it's complete. */
    @Query("SELECT surah, COUNT(*) AS count FROM translation_verse WHERE edition = :edition GROUP BY surah")
    fun cachedCounts(edition: String): Flow<List<SurahCount>>

    @Upsert suspend fun upsert(rows: List<TranslationVerseEntity>)

    @Query("SELECT * FROM translation_verse WHERE edition = :edition AND text LIKE '%' || :query || '%' ORDER BY surah, verse LIMIT :limit")
    suspend fun search(edition: String, query: String, limit: Int): List<TranslationVerseEntity>

    @Query("DELETE FROM translation_verse") suspend fun clearTranslations()

    @Query("SELECT * FROM bookmark ORDER BY created_at DESC")
    fun bookmarks(): Flow<List<BookmarkEntity>>

    @Upsert suspend fun addBookmark(b: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE surah = :surah AND verse = :verse")
    suspend fun removeBookmark(surah: Int, verse: Int)
}

data class SurahCount(val surah: Int, val count: Int)
