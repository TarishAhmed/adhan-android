package org.tarish.adhan.ui.quran

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuranTextTest {
    @Test fun plainTranslation_dropsFootnoteMarkersAndTags() {
        assertThat(plainTranslation("Alif Lam Mim[✛].")).isEqualTo("Alif Lam Mim.")
        assertThat(plainTranslation("[b]Alif Lām Mīm.[8A]")).isEqualTo("Alif Lām Mīm.")
        assertThat(plainTranslation("Seek <mark>help</mark>")).isEqualTo("Seek help")
    }

    @Test fun plainTranslation_keepsOrdinaryBrackets() {
        assertThat(plainTranslation("[the believers] are those")).isEqualTo("[the believers] are those")
    }
}
