package org.tarish.adhan.domain.quran

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.tarish.adhan.data.quran.QuranPrefs
import org.tarish.adhan.data.quran.QuranSettings

class QuranAudioTest {
    @Test fun recitationUrl_usesAhmadiNumberAndReciterFolder() {
        // 2:1 is the Bismillah in Ahmadiyya numbering; alislam's files are numbered the same way.
        assertThat(QuranAudio.recitation(Reciter.ALAFASY, 2, 1))
            .isEqualTo("https://files.alislam.cloud/audio/tilawat/rashid/002-001-AR.mp3")
        assertThat(QuranAudio.recitation(Reciter.ASHIQ, 114, 7))
            .isEqualTo("https://files.alislam.cloud/audio/tilawat/ashiq/114-007-AR.mp3")
    }

    @Test fun translationUrl_isNullWhenOff() {
        assertThat(QuranAudio.translation(TranslationAudio.OFF, 1, 1)).isNull()
        assertThat(QuranAudio.translation(TranslationAudio.SHORT_COMMENTARY, 1, 1))
            .isEqualTo("https://files.alislam.cloud/audio/tilawat/short-commentary-verse-by-verse/001-001-EN.mp3")
        assertThat(QuranAudio.translation(TranslationAudio.URDU, 9, 129))
            .isEqualTo("https://files.alislam.cloud/audio/tilawat/urdu-translation-naseer-qamar-verse-by-verse/009-129-UR.mp3")
    }

    @Test fun keys_roundTripAndFallBack() {
        Reciter.entries.forEach { assertThat(Reciter.fromKey(it.dir)).isEqualTo(it) }
        TranslationAudio.entries.forEach { assertThat(TranslationAudio.fromKey(it.key)).isEqualTo(it) }
        assertThat(Reciter.fromKey("gone")).isEqualTo(Reciter.DEFAULT)
        assertThat(TranslationAudio.fromKey(null)).isEqualTo(TranslationAudio.OFF)
    }

    @Test fun defaults_haveEveryExtraOff() {
        val p = QuranPrefs()
        assertThat(p.nazira).isFalse()
        assertThat(p.transliteration).isFalse()
        assertThat(p.audio).isFalse()
        assertThat(p.translationAudio).isEqualTo(TranslationAudio.OFF)
    }

    @Test fun nazira_hidesTranslationAndTransliteration_andEnlargesArabic() {
        val p = QuranPrefs(showTranslation = true, transliteration = true, nazira = true, arabicSp = 28)
        assertThat(p.showsTranslation).isFalse()
        assertThat(p.showsTransliteration).isFalse()
        assertThat(p.readingArabicSp).isEqualTo(28 + QuranSettings.NAZIRA_BOOST)
        assertThat(p.copy(nazira = false).showsTransliteration).isTrue()
        val spoken = p.copy(translationAudio = TranslationAudio.URDU)
        assertThat(spoken.playedTranslationAudio).isEqualTo(TranslationAudio.OFF)
        assertThat(spoken.copy(nazira = false).playedTranslationAudio).isEqualTo(TranslationAudio.URDU)
    }

    @Test fun ruku_splitsAfterMarkedVerses() {
        fun v(n: Int, end: Boolean) = Verse(2, n, if (end) "x ﴿\u08D6$n﴾" else "x ﴿$n﴾", null)
        val groups = Ruku.group(listOf(v(1, false), v(2, true), v(3, false), v(4, true), v(5, false)))
        assertThat(groups.map { g -> g.map { it.number } }).containsExactly(listOf(1, 2), listOf(3, 4), listOf(5)).inOrder()
        assertThat(Ruku.group(emptyList())).isEmpty()
    }
}
