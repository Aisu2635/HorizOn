package dev.horizon.ui.faces

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import dev.horizon.clock.handAngles
import dev.horizon.ui.theme.Amber
import dev.horizon.ui.theme.HorizOnTheme
import dev.horizon.ui.theme.Muted
import dev.horizon.ui.theme.Paper

/**
 * A minimal dial: 60 ticks, hour and minute hands, amber center. No second hand,
 * so it only redraws once a minute.
 */
@Composable
fun AnalogFace(time: ClockTime, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxHeight(0.78f)
                .aspectRatio(1f)
                .semantics { contentDescription = time.text },
        ) {
            val angles = handAngles(time.hour, time.minute)
            val r = size.minDimension / 2
            drawTicks(r)
            drawHand(angles.hour, length = r * 0.50f, width = r * 0.045f)
            drawHand(angles.minute, length = r * 0.80f, width = r * 0.028f)
            drawCircle(Amber, radius = r * 0.05f)
            drawCircle(Paper, radius = r * 0.018f)
        }
    }
}

private fun DrawScope.drawTicks(r: Float) {
    for (i in 0 until 60) {
        val isHour = i % 5 == 0
        val inner = if (isHour) r * 0.86f else r * 0.93f
        rotate(i * 6f) {
            drawLine(
                color = if (i == 0) Amber else if (isHour) Paper else Muted.copy(alpha = 0.5f),
                start = Offset(center.x, center.y - inner),
                end = Offset(center.x, center.y - r * 0.98f),
                strokeWidth = if (isHour) r * 0.022f else r * 0.008f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawHand(degrees: Float, length: Float, width: Float) {
    rotate(degrees) {
        drawLine(
            color = Paper,
            start = Offset(center.x, center.y + length * 0.12f),
            end = Offset(center.x, center.y - length),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

@Preview(widthDp = 844, heightDp = 390, showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun AnalogFacePreview() {
    HorizOnTheme { AnalogFace(ClockTime(10, 42, is24Hour = false)) }
}
