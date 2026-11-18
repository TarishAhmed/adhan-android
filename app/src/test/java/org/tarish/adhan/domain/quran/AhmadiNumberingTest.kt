package org.tarish.adhan.domain.quran

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AhmadiNumberingTest {
    private val counts = mapOf(1 to 7, 2 to 287, 9 to 129, 114 to 7)
    private fun parse(s: String) = AhmadiNumbering.parseReference(s) { counts[it] }

    @Test fun fatihaAndTawbah_haveNoExtraBismillahVerse() {
        assertThat(AhmadiNumbering.hasBismillahVerse(1)).isFalse()
        assertThat(AhmadiNumbering.hasBismillahVerse(9)).isFalse()
        assertThat(AhmadiNumbering.hasBismillahVerse(2)).isTrue()
        assertThat(AhmadiNumbering.hasBismillahVerse(114)).isTrue()
    }

    @Test fun isBismillah_onlyVerseOneOfSurahsWithIt() {
        assertThat(AhmadiNumbering.isBismillah(2, 1)).isTrue()
        assertThat(AhmadiNumbering.isBismillah(2, 2)).isFalse()
        // Al-Fatiha's verse 1 is the Bismillah but is an ordinary numbered verse in every printing.
        assertThat(AhmadiNumbering.isBismillah(1, 1)).isFalse()
        assertThat(AhmadiNumbering.isBismillah(9, 1)).isFalse()
    }

    @Test fun toStandard_shiftsByOneWhereBismillahIsCounted() {
        assertThat(AhmadiNumbering.toStandard(2, 256)).isEqualTo(255) // Ayat al-Kursi
        assertThat(AhmadiNumbering.toStandard(2, 1)).isEqualTo(0)
        assertThat(AhmadiNumbering.toStandard(1, 5)).isEqualTo(5)
        assertThat(AhmadiNumbering.toStandard(9, 3)).isEqualTo(3)
    }

    @Test fun parseReference_acceptsColonSpaceOrDot() {
        assertThat(parse("2:256")).isEqualTo(VerseRef(2, 256))
        assertThat(parse(" 2 256 ")).isEqualTo(VerseRef(2, 256))
        assertThat(parse("114.7")).isEqualTo(VerseRef(114, 7))
    }

    @Test fun parseReference_rejectsOutOfRangeOrMalformed() {
        assertThat(parse("2:288")).isNull()
        assertThat(parse("2:0")).isNull()
        assertThat(parse("115:1")).isNull()
        assertThat(parse("2")).isNull()
        assertThat(parse("baqarah")).isNull()
        assertThat(parse("2:1:3")).isNull()
    }
}
