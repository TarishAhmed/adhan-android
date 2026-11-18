package org.tarish.adhan.ui.quran

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

private val FOOTNOTE_REF = Regex("""\[([^\]\s]{1,4})]""")
private val MARK = Regex("""<mark>(.*?)</mark>""", RegexOption.DOT_MATCHES_ALL)
private val TAG = Regex("""<[^>]+>""")

/** Turns "[✛]" / "[8A]" footnote markers into small superscripts. */
fun footnoteText(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in FOOTNOTE_REF.findAll(text)) {
        append(text, last, m.range.first)
        withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em, color = color, fontWeight = FontWeight.W600)) {
            append(m.groupValues[1])
        }
        last = m.range.last + 1
    }
    append(text, last, text.length)
}

/** Bold + tinted background for `<mark>` matches; footnote markers and other tags are dropped. */
fun highlighted(raw: String, highlight: Color): AnnotatedString = buildAnnotatedString {
    val text = raw.replace(FOOTNOTE_REF, "")
    var last = 0
    for (m in MARK.findAll(text)) {
        append(text.substring(last, m.range.first).replace(TAG, ""))
        withStyle(SpanStyle(fontWeight = FontWeight.W700, background = highlight)) { append(m.groupValues[1].replace(TAG, "")) }
        last = m.range.last + 1
    }
    append(text.substring(last).replace(TAG, ""))
}

/** Plain text for copy/share: footnote markers and tags removed. */
fun plainTranslation(text: String): String = text.replace(FOOTNOTE_REF, "").replace(TAG, "").trim()

private const val URDU_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

/** Verse-end ornament in the Indo-Pak style, e.g. ﴿۱۲﴾. */
fun verseEndMarker(n: Int): String = "﴿" + n.toString().map { URDU_DIGITS[it - '0'] }.joinToString("") + "﴾"
