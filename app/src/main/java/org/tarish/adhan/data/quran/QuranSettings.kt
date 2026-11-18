package org.tarish.adhan.data.quran

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.tarish.adhan.domain.quran.Reciter
import org.tarish.adhan.domain.quran.TranslationAudio
import org.tarish.adhan.domain.quran.TranslationEdition
import org.tarish.adhan.domain.quran.VerseRef
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the reader shows and plays. The optional extras are all off by default. */
data class QuranPrefs(
    val edition: TranslationEdition = TranslationEdition.DEFAULT,
    val showTranslation: Boolean = true,
    /** Nazira (learning to read): the Arabic alone, larger, with no translation or transliteration. */
    val nazira: Boolean = false,
    val transliteration: Boolean = false,
    val audio: Boolean = false,
    val reciter: Reciter = Reciter.DEFAULT,
    val translationAudio: TranslationAudio = TranslationAudio.OFF,
    /** Times each verse's recitation plays. */
    val repeat: Int = 1,
    val arabicSp: Int = QuranSettings.DEFAULT_ARABIC,
    val translationSp: Int = QuranSettings.DEFAULT_TRANSLATION,
) {
    val showsTranslation get() = showTranslation && !nazira
    val showsTransliteration get() = transliteration && !nazira
    /** Nazira is Arabic only, so it plays the recitation without the spoken translation. */
    val playedTranslationAudio get() = if (nazira) TranslationAudio.OFF else translationAudio
    val readingArabicSp get() = if (nazira) arabicSp + QuranSettings.NAZIRA_BOOST else arabicSp
}

/** Reader preferences, kept in the app's settings DataStore under `quran_*` keys. */
@Singleton
class QuranSettings @Inject constructor(private val ds: DataStore<Preferences>) {

    private fun <T> pref(map: (Preferences) -> T): Flow<T> = ds.data.map(map).distinctUntilChanged()

    val edition: Flow<TranslationEdition> = pref { TranslationEdition.fromKey(it[EDITION]) }
    suspend fun setEdition(e: TranslationEdition) { ds.edit { it[EDITION] = e.key } }

    /** Off: the reader shows the Arabic only and fetches no translation. */
    val showTranslation: Flow<Boolean> = pref { it[SHOW_TRANSLATION] ?: true }
    suspend fun setShowTranslation(v: Boolean) { ds.edit { it[SHOW_TRANSLATION] = v } }

    suspend fun setNazira(v: Boolean) { ds.edit { it[NAZIRA] = v } }

    suspend fun setTransliteration(v: Boolean) { ds.edit { it[TRANSLITERATION] = v } }

    suspend fun setAudio(v: Boolean) { ds.edit { it[AUDIO] = v } }
    suspend fun setReciter(r: Reciter) { ds.edit { it[RECITER] = r.dir } }
    suspend fun setTranslationAudio(t: TranslationAudio) { ds.edit { it[TRANSLATION_AUDIO] = t.key } }
    suspend fun setRepeat(n: Int) { ds.edit { it[REPEAT] = n.coerceIn(REPEAT_RANGE) } }

    val prefs: Flow<QuranPrefs> = pref { p ->
        QuranPrefs(
            edition = TranslationEdition.fromKey(p[EDITION]),
            showTranslation = p[SHOW_TRANSLATION] ?: true,
            nazira = p[NAZIRA] ?: false,
            transliteration = p[TRANSLITERATION] ?: false,
            audio = p[AUDIO] ?: false,
            reciter = Reciter.fromKey(p[RECITER]),
            translationAudio = TranslationAudio.fromKey(p[TRANSLATION_AUDIO]),
            repeat = (p[REPEAT] ?: 1).coerceIn(REPEAT_RANGE),
            arabicSp = p[ARABIC_SIZE] ?: DEFAULT_ARABIC,
            translationSp = p[TRANSLATION_SIZE] ?: DEFAULT_TRANSLATION,
        )
    }

    val lastRead: Flow<VerseRef?> = pref { p ->
        val s = p[LAST_SURAH] ?: return@pref null
        VerseRef(s, p[LAST_VERSE] ?: 1)
    }
    suspend fun setLastRead(r: VerseRef) { ds.edit { it[LAST_SURAH] = r.surah; it[LAST_VERSE] = r.verse } }

    val arabicSize: Flow<Int> = pref { it[ARABIC_SIZE] ?: DEFAULT_ARABIC }
    suspend fun setArabicSize(sp: Int) { ds.edit { it[ARABIC_SIZE] = sp.coerceIn(ARABIC_RANGE) } }

    val translationSize: Flow<Int> = pref { it[TRANSLATION_SIZE] ?: DEFAULT_TRANSLATION }
    suspend fun setTranslationSize(sp: Int) { ds.edit { it[TRANSLATION_SIZE] = sp.coerceIn(TRANSLATION_RANGE) } }

    companion object {
        private val EDITION = stringPreferencesKey("quran_edition")
        private val SHOW_TRANSLATION = booleanPreferencesKey("quran_show_translation")
        private val NAZIRA = booleanPreferencesKey("quran_nazira")
        private val TRANSLITERATION = booleanPreferencesKey("quran_transliteration")
        private val AUDIO = booleanPreferencesKey("quran_audio")
        private val RECITER = stringPreferencesKey("quran_reciter")
        private val TRANSLATION_AUDIO = stringPreferencesKey("quran_translation_audio")
        private val REPEAT = intPreferencesKey("quran_repeat")
        private val LAST_SURAH = intPreferencesKey("quran_last_surah")
        private val LAST_VERSE = intPreferencesKey("quran_last_verse")
        private val ARABIC_SIZE = intPreferencesKey("quran_arabic_sp")
        private val TRANSLATION_SIZE = intPreferencesKey("quran_translation_sp")

        const val DEFAULT_ARABIC = 28
        const val DEFAULT_TRANSLATION = 17
        val ARABIC_RANGE = 20..48
        val TRANSLATION_RANGE = 14..28
        val REPEAT_RANGE = 1..3
        const val NAZIRA_BOOST = 6
    }
}
