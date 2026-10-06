package dev.horizon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colors taken from docs/screens mockups.
val Ink = Color(0xFF070708)
val Paper = Color(0xFFF4F1EC)
val Muted = Color(0xFFA8A29A)
val Amber = Color(0xFFF2A65A)
val Rust = Color(0xFFC2542D)
val ChargeGreen = Color(0xFF7FD18B)
val PillSurface = Color(0xFF1A1715)

private val DeskColors = darkColorScheme(
    primary = Amber,
    secondary = Rust,
    background = Ink,
    surface = Ink,
    surfaceContainer = PillSurface,
    onBackground = Paper,
    onSurface = Paper,
    onSurfaceVariant = Muted,
)

/** Always dark: the app is a bedside/desk display. */
@Composable
fun HorizOnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DeskColors, content = content)
}
