package dev.horizon.ui.lyrics

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.horizon.lyrics.LyricsRepository
import dev.horizon.lyrics.LyricsRequest
import dev.horizon.lyrics.LyricsResult
import dev.horizon.lyrics.LyricsWait
import dev.horizon.lyrics.timedWords
import dev.horizon.lyrics.waitAt
import dev.horizon.media.NowPlaying
import dev.horizon.media.formatTrackTime
import dev.horizon.nav.NavState
import dev.horizon.ui.artOutline
import dev.horizon.ui.nav.DirectionsPill
import dev.horizon.ui.rememberArtColors
import dev.horizon.ui.theme.Ink
import dev.horizon.ui.theme.Muted
import dev.horizon.ui.theme.Paper
import dev.horizon.ui.theme.PillSurface
import kotlinx.coroutines.delay

/** How long the song position stays up after a tap. */
private const val SCRUB_VISIBLE_MS = 3_500L

/** A shorter intro than this gets no countdown; it would only flash. */
private const val INTRO_COUNTDOWN_MIN_MS = 4_000L

/**
 * The third screen (swipe left from clock + music): time-synced lyrics as a web of words, or a
 * visualizer when there are none (always, in the offline build). The song position shows only
 * after a tap. Double-tap opens the music app.
 */
@Composable
fun LyricsPage(
    nowPlaying: NowPlaying,
    time: String,
    nav: NavState?,
    lyricsEnabled: Boolean,
    showLyricsPrompt: Boolean,
    onEnableLyrics: () -> Unit,
    onDismissLyricsPrompt: () -> Unit,
    onTap: () -> Unit,
    onOpenApp: () -> Unit,
    onShowDirections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = rememberArtColors(nowPlaying.art)
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    // Look the song up once per song (and again if the Lyrics switch changes).
    val request = remember(nowPlaying.packageName, nowPlaying.title, nowPlaying.artist, nowPlaying.durationMs) {
        LyricsRequest(nowPlaying.packageName, nowPlaying.title, nowPlaying.artist, nowPlaying.album, nowPlaying.durationMs)
    }
    val result by produceState<LyricsResult?>(null, request, lyricsEnabled) {
        value = null
        value = LyricsRepository.find(request, context.packageName, lyricsEnabled)
    }
    val synced = result as? LyricsResult.Synced

    // Before the first word and in long breaks the web has nothing to draw, so the visualizer fills in.
    val words = remember(synced) { synced?.lyrics?.timedWords().orEmpty() }
    val wait by produceState<LyricsWait?>(null, words, nowPlaying) {
        while (true) {
            value = words.waitAt(nowPlaying.positionAt(SystemClock.elapsedRealtime()))
            delay(250)
        }
    }

    var taps by remember { mutableIntStateOf(0) }
    var scrubVisible by remember { mutableStateOf(false) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        scrubVisible = true
        delay(SCRUB_VISIBLE_MS)
        scrubVisible = false
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(onTap, onOpenApp) {
                detectTapGestures(onTap = { taps++; onTap() }, onDoubleTap = { onOpenApp() })
            }
            .semantics {
                customActions = listOf(CustomAccessibilityAction("Open ${nowPlaying.appLabel ?: "music app"}") { onOpenApp(); true })
            },
    ) {
        Crossfade(synced?.lyrics?.takeIf { wait == null }, label = "lyrics", modifier = Modifier.fillMaxSize()) { lyrics ->
            if (lyrics != null) {
                LyricsWeb(lyrics, nowPlaying, colors.accent, reduceMotion, Modifier.fillMaxSize())
            } else {
                Visualizer(nowPlaying, colors.accent, Modifier.fillMaxSize().padding(top = 56.dp, bottom = 24.dp))
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp)) {
            Header(nowPlaying, time, if (synced != null) "LYRICS" else null, colors.accent)
            AnimatedVisibility(scrubVisible, enter = fadeIn(), exit = fadeOut()) {
                SongPosition(nowPlaying, colors.accent, Modifier.padding(top = 14.dp))
            }
        }

        if (nav != null) {
            DirectionsPill(
                nav,
                onClick = onShowDirections,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp).widthIn(max = 320.dp),
            )
        }

        val introLeft = wait?.takeIf { it.kind == LyricsWait.Kind.Intro }?.untilNextMs?.takeIf { it > INTRO_COUNTDOWN_MIN_MS }
        val status = when {
            showLyricsPrompt -> null
            introLeft != null -> "Lyrics in ${formatTrackTime(introLeft + 999)}"
            result == LyricsResult.Instrumental -> "Instrumental"
            lyricsEnabled && result == LyricsResult.NotFound -> "No lyrics found for this song"
            lyricsEnabled && result == LyricsResult.Unavailable -> "Couldn't reach the lyrics service"
            else -> null
        }
        if (status != null) {
            Text(
                status,
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            )
        }

        AnimatedVisibility(
            showLyricsPrompt,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 24.dp),
        ) {
            LyricsPrompt(onShow = onEnableLyrics, onNotNow = onDismissLyricsPrompt)
        }
    }
}

@Composable
private fun Header(nowPlaying: NowPlaying, time: String, label: String?, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val shape = RoundedCornerShape(7.dp)
        val art = nowPlaying.art
        if (art != null) {
            val image = remember(art) { art.asImageBitmap() }
            Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.size(30.dp).clip(shape).artOutline(shape))
        } else {
            Box(Modifier.size(30.dp).clip(shape).background(Brush.linearGradient(listOf(accent, Color.Black))).artOutline(shape))
        }
        Column(Modifier.weight(1f)) {
            Text(nowPlaying.title, color = Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            nowPlaying.artist?.let {
                Text(it, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(time, color = Muted, fontSize = 13.sp)
        if (label != null) {
            Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.12.em)
        }
    }
}

/** A thin bar with elapsed and total time; shown for a few seconds after a tap. */
@Composable
private fun SongPosition(nowPlaying: NowPlaying, accent: Color, modifier: Modifier) {
    val position by produceState(nowPlaying.positionAt(SystemClock.elapsedRealtime()), nowPlaying) {
        while (true) {
            value = nowPlaying.positionAt(SystemClock.elapsedRealtime())
            delay(500)
        }
    }
    val duration = nowPlaying.durationMs
    Column(modifier.fillMaxWidth()) {
        if (duration > 0) {
            val fraction = (position.toFloat() / duration).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                val y = size.height / 2
                val w = size.height
                drawLine(Color.White.copy(alpha = 0.14f), Offset(w / 2, y), Offset(size.width - w / 2, y), w, StrokeCap.Round)
                drawLine(accent, Offset(w / 2, y), Offset(w / 2 + (size.width - w) * fraction, y), w, StrokeCap.Round)
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatTrackTime(position), color = Muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            if (duration > 0) Text(formatTrackTime(duration), color = Muted, fontSize = 12.sp)
        }
    }
}

/** HorizOn+ asks once before sending anything to the lyrics service. */
@Composable
private fun LyricsPrompt(onShow: () -> Unit, onNotNow: () -> Unit) {
    val pill = RoundedCornerShape(50)
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PillSurface)
            .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Text("Show lyrics?", color = Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "HorizOn+ will look up each song's title and artist on lrclib.net. Nothing else is sent.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(end = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(pill).clickable(role = Role.Button, onClick = onNotNow).padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Not now", color = Muted, fontSize = 14.sp)
            }
            Box(
                Modifier.clip(pill).background(Paper).clickable(role = Role.Button, onClick = onShow)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text("Show lyrics", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
