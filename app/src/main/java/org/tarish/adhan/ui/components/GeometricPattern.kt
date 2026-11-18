package org.tarish.adhan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.tarish.adhan.ui.theme.extendedColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Khatam (two overlapping squares): 16 vertices alternating between the outer and inner radius. */
private fun Path.khatam(cx: Float, cy: Float, outer: Float) {
    val inner = outer * (cos(PI / 4) / cos(PI / 8)).toFloat()
    for (i in 0 until 16) {
        val r = if (i % 2 == 0) outer else inner
        val a = i * PI / 8 - PI / 2
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * Decorative lattice of 8-point stars joined tip to tip. Purely ornamental, so it is drawn behind
 * content and never takes part in layout or semantics.
 */
fun Modifier.geometricPattern(color: Color, alpha: Float = 0.08f, cell: Dp = 44.dp): Modifier = drawWithCache {
    val c = cell.toPx()
    val r = c * 0.3f
    val path = Path()
    var y = 0f
    while (y < size.height + c) {
        var x = 0f
        while (x < size.width + c) {
            path.khatam(x, y, r)
            path.moveTo(x + r, y); path.lineTo(x + c - r, y)
            path.moveTo(x, y + r); path.lineTo(x, y + c - r)
            x += c
        }
        y += c
    }
    val stroke = Stroke(width = 1.dp.toPx())
    onDrawBehind { drawPath(path, color.copy(alpha = alpha), style = stroke) }
}

/** Number set inside a khatam outline, used for surah numbers. */
@Composable
fun StarBadge(text: String, style: TextStyle, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val ornament = extendedColors.ornament
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).clearAndSetSemantics { }) {
            val p = Path().apply { khatam(this@Canvas.size.width / 2, this@Canvas.size.height / 2, this@Canvas.size.minDimension / 2 - 1.dp.toPx()) }
            drawPath(p, ornament.copy(alpha = 0.12f), style = Fill)
            drawPath(p, ornament, style = Stroke(1.2.dp.toPx()))
        }
        Text(text, style = style)
    }
}
