package dev.zendesk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colors taken from docs/screens mockups.
private val Ink = Color(0xFF070708)
private val Paper = Color(0xFFF2EFEA)
private val Amber = Color(0xFFF2A65A)
private val Rust = Color(0xFFC2542D)

private val DeskColors = darkColorScheme(
    primary = Amber,
    secondary = Rust,
    background = Ink,
    surface = Ink,
    onBackground = Paper,
    onSurface = Paper,
)

/** Always dark: the app is a bedside/desk display. */
@Composable
fun ZendeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DeskColors, content = content)
}
