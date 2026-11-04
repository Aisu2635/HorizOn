package dev.horizon.ui.lyrics

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.min
import dev.horizon.media.NowPlaying
import dev.horizon.ui.artOutline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private const val BARS = 72

/** Assumed tempo for the pulse. The real tempo isn't known without listening to the audio. */
private const val BPM = 118f

/** 0..1 pulse that peaks on each beat and decays; flat while paused. */
internal fun beatPulse(seconds: Float, playing: Boolean, bpm: Float = BPM): Float {
    if (!playing) return 0f
    val phase = (seconds * bpm / 60f).mod(1f)
    return exp(-phase * 5f)
}

/**
 * Shown when a song has no lyrics (and always in the offline build): a ring of bars in the
 * album's [accent] around the cover, pulsing to the playback. It does not listen to the audio,
 * which would need the microphone permission (Lyrics_plan.md, section 7).
 */
@Composable
fun Visualizer(nowPlaying: NowPlaying, accent: Color, modifier: Modifier = Modifier) {
    val track by rememberUpdatedState(nowPlaying)
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { frame = it }
    }
    fun pulse(): Float {
        val now = SystemClock.elapsedRealtime()
        return beatPulse(track.positionAt(now) / 1000f, track.isPlaying)
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val coverSize = min(maxWidth, maxHeight) * 0.3f
        Canvas(Modifier.matchParentSize()) {
            if (frame < 0L) return@Canvas // Redraw every frame.
            val now = SystemClock.elapsedRealtime()
            val t = track.positionAt(now) / 1000f
            val beat = pulse()
            val center = Offset(size.width / 2f, size.height / 2f)
            val base = coverSize.toPx() / 2f + 10f * density + beat * 6f * density
            val glow = base * 3f
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.16f + beat * 0.12f), Color.Transparent), center, glow),
                glow,
                center,
            )
            for (i in 0 until BARS) {
                val angle = i / BARS.toFloat() * 2f * PI.toFloat() - PI.toFloat() / 2f
                val wave = 0.5f + 0.5f * sin(i * 0.42f + t * 2.3f) * sin(i * 0.17f - t * 1.4f)
                val length = if (track.isPlaying) (8f + wave * (28f + beat * 46f)) * density else 6f * density
                val dir = Offset(cos(angle), sin(angle))
                drawLine(
                    color = accent.copy(alpha = 0.35f + wave * 0.65f),
                    start = center + dir * base,
                    end = center + dir * (base + length),
                    strokeWidth = 3.2f * density,
                    cap = StrokeCap.Round,
                )
            }
        }
        Cover(
            nowPlaying.art,
            accent,
            Modifier
                .size(coverSize)
                .graphicsLayer {
                    val s = 1f + pulse() * 0.04f
                    scaleX = s
                    scaleY = s
                },
        )
    }
}

@Composable
private fun Cover(art: Bitmap?, accent: Color, modifier: Modifier) {
    if (art != null) {
        val image = remember(art) { art.asImageBitmap() }
        Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape).artOutline(CircleShape),
        )
    } else {
        Box(
            modifier
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent, Color.Black)))
                .artOutline(CircleShape),
        )
    }
}
