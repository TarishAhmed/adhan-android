package org.tarish.adhan.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.tarish.adhan.domain.model.Segment
import org.tarish.adhan.domain.model.Urgency
import org.tarish.adhan.ui.theme.extendedColors

/** §8.3.1 — single Canvas; segments clipped to the filled portion. */
@Composable
fun PrayerProgressBar(
    progress: Float,
    segments: List<Segment>,
    stateDescription: String,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(250), label = "progress")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val green = MaterialTheme.colorScheme.primary
    val yellow = extendedColors.warning
    val red = MaterialTheme.colorScheme.error
    val neutral = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                this.stateDescription = stateDescription
            }
    ) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val fillW = size.width * animated
        if (fillW <= 0f) return@Canvas
        val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, fillW, size.height, r)) }
        clipPath(clip) {
            for (s in segments) {
                val start = (s.start * size.width).toFloat()
                val end = (minOf(s.end, animated.toDouble()) * size.width).toFloat()
                if (end <= start) continue
                val c = when (s.urgency) {
                    Urgency.GREEN -> green
                    Urgency.YELLOW -> yellow
                    Urgency.RED -> red
                    Urgency.NEUTRAL -> neutral
                }
                drawRect(c, topLeft = Offset(start, 0f), size = Size(end - start, size.height))
            }
        }
    }
}
