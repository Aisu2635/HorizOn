package dev.horizon.ui.faces

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import dev.horizon.ui.theme.Amber
import dev.horizon.ui.theme.HorizOnTheme
import dev.horizon.ui.theme.Paper

/** Large thin digits with an amber colon, as in the full-clock mockup. */
@Composable
fun DigitalFace(time: ClockTime, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Sized like the mockup (220px type on an 844x390 screen), scaled to fit either axis.
        // The height factor is larger because this area already excludes the status row.
        val fontSize = with(LocalDensity.current) { min(maxWidth * 0.26f, maxHeight * 0.78f).toSp() }
        val text = buildAnnotatedString {
            val (hours, minutes) = time.text.split(':')
            append(hours)
            withStyle(SpanStyle(color = Amber)) { append(":") }
            append(minutes)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-0.045).em,
                color = Paper,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Preview(widthDp = 844, heightDp = 390, showBackground = true, backgroundColor = 0xFF070708)
@Composable
private fun DigitalFacePreview() {
    HorizOnTheme { DigitalFace(ClockTime(10, 42, is24Hour = false)) }
}
