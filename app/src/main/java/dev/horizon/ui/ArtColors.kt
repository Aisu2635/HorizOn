package dev.horizon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import dev.horizon.media.AlbumColorRule
import dev.horizon.ui.theme.Amber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Colours pulled from the album art: a dark tint for the card and an accent that reads on black. */
data class ArtColors(val tint: Color, val accent: Color)

/** Before any art has loaded (or when an app sends none). */
val DefaultArtColors = ArtColors(Color(0xFF2A1A10), Amber)

/** Colours for [art], worked out off the main thread with the rule in [AlbumColorRule]. */
@Composable
fun rememberArtColors(art: Bitmap?): ArtColors {
    var colors by remember { mutableStateOf(DefaultArtColors) }
    LaunchedEffect(art) {
        colors = if (art == null) {
            DefaultArtColors
        } else {
            withContext(Dispatchers.Default) {
                val swatches = Palette.from(art).maximumColorCount(16).generate().swatches
                AlbumColorRule.pick(swatches.map { it.rgb to it.population })
                    ?.let { ArtColors(Color(it.tint), Color(it.accent)) }
                    ?: DefaultArtColors
            }
        }
    }
    return colors
}

/**
 * "Edge light": a thin glass rim, bright where light catches the top-left corner, fading along the
 * sides, with a touch of [accent] at the bottom-right. Gives a card its shape on a pure black screen.
 */
fun Modifier.glassRim(accent: Color, shape: Shape): Modifier = border(
    width = 1.2.dp,
    brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.55f),
        0.28f to Color.White.copy(alpha = 0.10f),
        0.7f to Color.White.copy(alpha = 0.06f),
        1f to accent.copy(alpha = 0.45f),
    ),
    shape = shape,
)

/** A hairline that keeps a near-black album cover from disappearing into the black screen. */
fun Modifier.artOutline(shape: Shape): Modifier = border(0.75.dp, Color.White.copy(alpha = 0.15f), shape)
