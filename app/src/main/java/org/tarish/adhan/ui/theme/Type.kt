package org.tarish.adhan.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.tarish.adhan.R

/** Each weight maps to its own variation (BUG-47). */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int) = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

val Inter = variable(R.font.inter_variable)
val NotoSerif = variable(R.font.noto_serif_variable)

/** Arabic labels (prayer names, surah names). */
val Amiri = FontFamily(Font(R.font.amiri_regular))

/** Quran text; Scheherazade New carries the Indo-Pak marks (waqf signs, small high letters). */
val QuranFont = FontFamily(Font(R.font.scheherazade_new_regular))

/** Arabic needs taller lines than Latin so diacritics don't collide. */
fun arabicLabel(size: Int) = TextStyle(fontFamily = Amiri, fontSize = size.sp, lineHeight = (size * 1.6f).sp)

fun quranText(size: Int) = TextStyle(fontFamily = QuranFont, fontSize = size.sp, lineHeight = (size * 2.0f).sp)

private fun s(f: FontFamily, size: Int, lh: Int, w: Int) =
    TextStyle(fontFamily = f, fontSize = size.sp, lineHeight = lh.sp, fontWeight = FontWeight(w))

val AdhanTypography = Typography(
    displayLarge = s(NotoSerif, 57, 64, 400),
    displayMedium = s(NotoSerif, 45, 52, 400),
    displaySmall = s(NotoSerif, 36, 44, 400),
    headlineLarge = s(NotoSerif, 32, 40, 400),
    headlineMedium = s(NotoSerif, 28, 36, 400),
    headlineSmall = s(NotoSerif, 24, 32, 400),
    titleLarge = s(Inter, 22, 28, 400),
    titleMedium = s(Inter, 16, 24, 500),
    titleSmall = s(Inter, 14, 20, 500),
    bodyLarge = s(Inter, 16, 24, 400),
    bodyMedium = s(Inter, 14, 20, 400),
    bodySmall = s(Inter, 12, 16, 400),
    labelLarge = s(Inter, 14, 20, 500),
    labelMedium = s(Inter, 12, 16, 500),
    labelSmall = s(Inter, 11, 16, 500),
)

/** Tabular figures for times, countdown and coordinates. */
fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")
