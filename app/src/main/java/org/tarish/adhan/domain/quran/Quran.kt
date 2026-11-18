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

/**
 * Ahmadiyya translations offered by alislam.org's Quran reader; [key] is the API's edition key.
 * [translator] is blank where alislam names no translator. Malayalam isn't offered by that source.
 */
enum class TranslationEdition(val key: String, val language: String, val translator: String, val rtl: Boolean = false) {
    ZAFRULLA_KHAN("zk", "English", "Muhammad Zafrulla Khan"),
    SHER_ALI("en", "English", "Maulawi Sher Ali"),
    FIVE_VOLUME("v5", "English", "Five Volume Commentary"),
    SHORT_COMMENTARY("sc", "English", "Short Commentary"),
    URDU("ur", "Urdu", "Hazrat Mirza Tahir Ahmad", rtl = true),
    TAFSEER_SAGHEER("ts", "Urdu", "Tafseer-e-Sagheer", rtl = true),
    BANGLA("bn", "Bangla", ""),
    CHINESE("cn", "Chinese", "Short Commentary"),
    GERMAN("de", "German", ""),
    SPANISH("es", "Spanish", ""),
    FRENCH("fr", "French", ""),
    HEBREW("he", "Hebrew", "", rtl = true),
    ITALIAN("it", "Italian", ""),
    MYANMAR("my", "Myanmar (Burmese)", ""),
    NORWEGIAN("nw", "Norwegian", "Short Commentary"),
    SWEDISH("sv", "Swedish", "");

    /** "English · Maulawi Sher Ali", or just the language when there is no translator name. */
    val label: String get() = if (translator.isBlank()) language else "$language · $translator"

    companion object {
        val DEFAULT = ZAFRULLA_KHAN
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: DEFAULT
    }
}

/**
 * Verse-by-verse recitations on alislam.org's Quran reader, in our recommended order: Alafasy (alislam's
 * own default, the most widely acclaimed), then the Jamaat's Qari Muhammad Ashiq and Maulana Feroz Alam
 * in the Indo-Pak style the Mushaf text follows, then Aiman Almaliki and Idir Iken. [dir] is the
 * folder under [QuranAudio.BASE_URL] and doubles as the stored key.
 */
enum class Reciter(val dir: String, val displayName: String) {
    ALAFASY("rashid", "Mishary Rashid Alafasy"),
    ASHIQ("ashiq", "Qari Muhammad Ashiq"),
    FEROZ_ALAM("feroz", "Maulana Feroz Alam"),
    ALMALIKI("aiman", "Aiman Almaliki"),
    IKEN("idir-iken", "Idir Iken");

    companion object {
        val DEFAULT = ALAFASY
        fun fromKey(k: String?) = entries.firstOrNull { it.dir == k } ?: DEFAULT
    }
}

/** Spoken translation or commentary played after each verse's recitation; [OFF] by default. */
enum class TranslationAudio(val key: String, val dir: String?, val suffix: String?, val language: String, val displayName: String) {
    OFF("off", null, null, "", ""),
    SHORT_COMMENTARY("sc", "short-commentary-verse-by-verse", "EN", "English", "Short Commentary · Mansoor Clarke"),
    URDU("ur", "urdu-translation-naseer-qamar-verse-by-verse", "UR", "Urdu", "Urdu translation · Naseer Qamar");

    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: OFF }
}

/** File names on alislam's audio server, which uses Ahmadiyya numbering like the rest of the app. */
object QuranAudio {
    const val BASE_URL = "https://files.alislam.cloud/audio/tilawat/"

    private fun name(surah: Int, verse: Int) = "%03d-%03d".format(surah, verse)

    fun recitation(r: Reciter, surah: Int, verse: Int) = "$BASE_URL${r.dir}/${name(surah, verse)}-AR.mp3"

    fun translation(t: TranslationAudio, surah: Int, verse: Int): String? =
        t.dir?.let { "$BASE_URL$it/${name(surah, verse)}-${t.suffix}.mp3" }
}

/** [text] may contain `[ref]` markers that point at [notes]; note bodies may contain HTML. */
data class Translation(val text: String, val notes: List<Footnote> = emptyList())

data class Footnote(val ref: String, val body: String)

data class Verse(
    val surah: Int,
    val number: Int,
    val arabic: String,
    val translation: Translation?,
    val transliteration: String? = null,
)

data class VerseRef(val surah: Int, val verse: Int)

/** [text] may wrap the matched words in `<mark>` tags. */
data class SearchHit(val surah: Int, val verse: Int, val text: String)

interface TranslationSource {
    /** Verses [from]..[to] of [surah], keyed by verse number. */
    suspend fun verses(edition: TranslationEdition, surah: Int, from: Int, to: Int): Map<Int, Translation>
    suspend fun search(edition: TranslationEdition, query: String): List<SearchHit>
}

/** Rukūʿ (sections) as the Indo-Pak text marks them: a verse whose end ornament holds ࣖ closes one. */
object Ruku {
    const val MARK = '\u08D6'

    /** [verses] in order, split after each verse that ends a rukūʿ; a trailing unmarked run is kept. */
    fun group(verses: List<Verse>): List<List<Verse>> {
        val out = mutableListOf<List<Verse>>()
        var current = mutableListOf<Verse>()
        for (v in verses) {
            current += v
            if (MARK in v.arabic) { out += current; current = mutableListOf() }
        }
        if (current.isNotEmpty()) out += current
        return out
    }
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
