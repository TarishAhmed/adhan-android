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
import org.tarish.adhan.domain.quran.TranslationEdition
import org.tarish.adhan.domain.quran.VerseRef
import javax.inject.Inject
import javax.inject.Singleton

/** Reader preferences, kept in the app's settings DataStore under `quran_*` keys. */
@Singleton
class QuranSettings @Inject constructor(private val ds: DataStore<Preferences>) {

    private fun <T> pref(map: (Preferences) -> T): Flow<T> = ds.data.map(map).distinctUntilChanged()

    val edition: Flow<TranslationEdition> = pref { TranslationEdition.fromKey(it[EDITION]) }
    suspend fun setEdition(e: TranslationEdition) { ds.edit { it[EDITION] = e.key } }

    /** Off: the reader shows the Arabic only and fetches no translation. */
    val showTranslation: Flow<Boolean> = pref { it[SHOW_TRANSLATION] ?: true }
    suspend fun setShowTranslation(v: Boolean) { ds.edit { it[SHOW_TRANSLATION] = v } }

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
        private val LAST_SURAH = intPreferencesKey("quran_last_surah")
        private val LAST_VERSE = intPreferencesKey("quran_last_verse")
        private val ARABIC_SIZE = intPreferencesKey("quran_arabic_sp")
        private val TRANSLATION_SIZE = intPreferencesKey("quran_translation_sp")

        const val DEFAULT_ARABIC = 28
        const val DEFAULT_TRANSLATION = 17
        val ARABIC_RANGE = 20..48
        val TRANSLATION_RANGE = 14..28
    }
}
