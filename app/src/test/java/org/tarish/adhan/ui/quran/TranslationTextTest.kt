package org.tarish.adhan.ui.quran

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tarish.adhan.domain.quran.TranslationEdition

/** fromHtml needs Android's HTML parser, hence Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranslationTextTest {
    @Test fun footnoteText_rendersInlineHtmlAndRefs() {
        val t = footnoteText("This is a perfect Book; [a]there is no doubt; <i>it is</i> a guidance", Color.Red)
        assertThat(t.text).isEqualTo("This is a perfect Book; athere is no doubt; it is a guidance")
        assertThat(t.text).doesNotContain("<i>")
        assertThat(t.spanStyles).isNotEmpty()
    }

    @Test fun editions_haveUniqueKeysAndReadableLabels() {
        val keys = TranslationEdition.entries.map { it.key }
        assertThat(keys).containsNoDuplicates()
        assertThat(TranslationEdition.SHER_ALI.label).isEqualTo("English · Maulawi Sher Ali")
        assertThat(TranslationEdition.GERMAN.label).isEqualTo("German")
        assertThat(TranslationEdition.fromKey("nope")).isEqualTo(TranslationEdition.ZAFRULLA_KHAN)
        assertThat(TranslationEdition.entries.filter { it.rtl }.map { it.language }.toSet()).containsExactly("Urdu", "Hebrew")
    }
}
