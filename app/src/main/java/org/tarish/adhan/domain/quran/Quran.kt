package org.tarish.adhan.domain.quran

/**
 * Verse numbers throughout the app follow the Ahmadiyya convention: the Bismillah is verse 1 of
 * every surah that has one, so most surahs number one higher than other printings.
 */
data class Surah(
    val number: Int,
    val name: String,
    val arabicName: String,
    val meaning: String,
    val makki: Boolean,
    val verseCount: Int,
)

/** Ahmadiyya English translations offered by alislam.org; [key] is the API's edition key. */
enum class TranslationEdition(val key: String, val translator: String) {
    ZAFRULLA_KHAN("zk", "Muhammad Zafrulla Khan"),
    SHER_ALI("en", "Maulawi Sher Ali"),
    FIVE_VOLUME("v5", "Five Volume Commentary");

    companion object {
        val DEFAULT = ZAFRULLA_KHAN
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: DEFAULT
    }
}

/** [text] may contain `[ref]` markers that point at [notes]; note bodies may contain HTML. */
data class Translation(val text: String, val notes: List<Footnote> = emptyList())

data class Footnote(val ref: String, val body: String)

data class Verse(val surah: Int, val number: Int, val arabic: String, val translation: Translation?)

data class VerseRef(val surah: Int, val verse: Int)

/** [text] may wrap the matched words in `<mark>` tags. */
data class SearchHit(val surah: Int, val verse: Int, val text: String)

interface TranslationSource {
    /** Verses [from]..[to] of [surah], keyed by verse number. */
    suspend fun verses(edition: TranslationEdition, surah: Int, from: Int, to: Int): Map<Int, Translation>
    suspend fun search(edition: TranslationEdition, query: String): List<SearchHit>
}

object AhmadiNumbering {
    /** Al-Fatiha already counts the Bismillah as verse 1, and At-Tawbah has none. */
    fun hasBismillahVerse(surah: Int): Boolean = surah != 1 && surah != 9

    /** Whether verse 1 of [surah] is a Bismillah (shown as a header rather than a numbered verse). */
    fun isBismillah(surah: Int, verse: Int): Boolean = verse == 1 && hasBismillahVerse(surah)

    /** The verse number most other printings use; 0 for the Bismillah. */
    fun toStandard(surah: Int, verse: Int): Int = if (hasBismillahVerse(surah)) verse - 1 else verse

    /** Parses "2:256" or "2 256"; null unless both parts are valid for [verseCount]. */
    fun parseReference(text: String, verseCount: (Int) -> Int?): VerseRef? {
        val parts = text.trim().split(':', ' ', '.').filter { it.isNotBlank() }
        if (parts.size != 2) return null
        val s = parts[0].toIntOrNull() ?: return null
        val v = parts[1].toIntOrNull() ?: return null
        val count = verseCount(s) ?: return null
        return if (v in 1..count) VerseRef(s, v) else null
    }
}
