package dev.horizon.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import dev.horizon.media.NowPlaying
import dev.horizon.media.formatTrackTime
import dev.horizon.ui.theme.Amber
import dev.horizon.ui.theme.Ink
import dev.horizon.ui.theme.Muted
import dev.horizon.ui.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

private val CardBase = Color(0xFF141012)
private val NeutralTint = Color(0xFF2A1A10)
private val SecondaryText = Color(0xFFB9B2A9)
private val TrackColor = Color.White.copy(alpha = 0.14f)

/** Duration of the slide between tracks. */
private const val TRACK_SLIDE_MS = 380

/** How long a Previous/Next press decides the slide direction. */
private const val DIRECTION_TTL_MS = 2_000L

/** Colors pulled from the album art: a dark tint for the card and a bright accent. */
private data class ArtColors(val tint: Color, val accent: Color)

private val DefaultArtColors = ArtColors(NeutralTint, Amber)

/** Card background: the tint in the top-left corner fading into the base, like the mockup. */
fun Modifier.cardBackground(tint: Color): Modifier = clip(RoundedCornerShape(28.dp)).drawBehind {
    drawRect(
        Brush.linearGradient(
            0f to tint,
            0.7f to CardBase,
            start = Offset.Zero,
            end = Offset(size.width * 0.6f, size.height),
        ),
    )
}

/**
 * The Now Playing card: art, title, artist, progress and transport controls, tinted from the art.
 * Double-tap the card to open the music app; a single tap behaves like a tap anywhere else.
 */
@Composable
fun PlayerCard(
    nowPlaying: NowPlaying,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTap: () -> Unit,
    onOpenApp: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberArtColors(nowPlaying.art)
    val tint by animateColorAsState(colors.tint, tween(600), label = "tint")
    val accent by animateColorAsState(colors.accent, tween(600), label = "accent")
    val appName = nowPlaying.appLabel ?: "music app"

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .cardBackground(tint)
            .pointerInput(onTap, onOpenApp) {
                detectTapGestures(onTap = { onTap() }, onDoubleTap = { onOpenApp() })
            }
            .semantics {
                customActions = listOf(CustomAccessibilityAction("Open $appName") { onOpenApp(); true })
            }
            .padding(22.dp),
    ) {
        val artSize = min(116.dp, maxHeight * 0.38f)
        // +1 slides the new track in from the right (Next, or a change from the app), -1 from the left (Previous).
        // Set by our Previous/Next buttons; ignored after a couple of seconds, since Previous often just
        // restarts the song and changes from the app itself (track ended, skipped there) should go forward.
        var direction by remember { mutableIntStateOf(1) }
        var directionSetAt by remember { mutableLongStateOf(0L) }
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = nowPlaying,
                // Same track (e.g. art arriving late, play/pause) updates in place; a new track animates.
                contentKey = { "${it.packageName}|${it.title}|${it.artist}" },
                transitionSpec = {
                    val d = if (SystemClock.elapsedRealtime() - directionSetAt < DIRECTION_TTL_MS) direction else 1
                    val enter = slideInHorizontally(tween(TRACK_SLIDE_MS, easing = FastOutSlowInEasing)) { d * it / 3 } +
                        fadeIn(tween(TRACK_SLIDE_MS - 80, delayMillis = 80)) +
                        scaleIn(tween(TRACK_SLIDE_MS, easing = FastOutSlowInEasing), initialScale = 0.94f)
                    val exit = slideOutHorizontally(tween(TRACK_SLIDE_MS, easing = FastOutSlowInEasing)) { -d * it / 3 } +
                        fadeOut(tween(TRACK_SLIDE_MS / 2))
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "track",
            ) { track ->
                TrackHeader(track, artSize, accent)
            }
            Spacer(Modifier.weight(1f))
            if (nowPlaying.durationMs > 0) {
                Progress(nowPlaying, accent, onSeek)
                Spacer(Modifier.weight(1f))
            }
            TransportRow(
                nowPlaying = nowPlaying,
                onPlayPause = onPlayPause,
                onPrevious = { direction = -1; directionSetAt = SystemClock.elapsedRealtime(); onPrevious() },
                onNext = { direction = 1; directionSetAt = SystemClock.elapsedRealtime(); onNext() },
            )
        }
    }
}

/** Album art, app name, title and artist. */
@Composable
private fun TrackHeader(track: NowPlaying, artSize: Dp, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Art often arrives a moment after the title; fade it in rather than popping.
        Crossfade(track.art, animationSpec = tween(300), label = "art") { art ->
            AlbumArt(art, artSize, accent)
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            // The app name alone ("YOUTUBE MUSIC") fits the narrow column better than "NOW PLAYING · …".
            val source = track.appLabel?.uppercase() ?: "NOW PLAYING"
            Text(
                text = source,
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = track.title,
                color = Paper,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.artist != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = track.artist,
                    color = SecondaryText,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun rememberArtColors(art: Bitmap?): ArtColors {
    var colors by remember { mutableStateOf(DefaultArtColors) }
    LaunchedEffect(art) {
        colors = if (art == null) {
            DefaultArtColors
        } else {
            withContext(Dispatchers.Default) { Palette.from(art).maximumColorCount(16).generate().toArtColors() }
        }
    }
    return colors
}

private fun Palette.toArtColors(): ArtColors {
    val tintSwatch = darkVibrantSwatch ?: darkMutedSwatch ?: dominantSwatch ?: return DefaultArtColors
    // Keep the tint dark enough that white text stays readable on it.
    val tint = lerp(Color(tintSwatch.rgb), Color.Black, 0.35f)
    val accentSwatch = vibrantSwatch ?: lightVibrantSwatch ?: lightMutedSwatch
    var accent = accentSwatch?.let { Color(it.rgb) } ?: Amber
    if (accent.luminance() < 0.25f) accent = lerp(accent, Color.White, 0.45f)
    return ArtColors(tint, accent)
}

@Composable
private fun AlbumArt(art: Bitmap?, size: Dp, accent: Color) {
    val shape = RoundedCornerShape(16.dp)
    if (art != null) {
        val image = remember(art) { art.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = "Album art",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        // No art from the app: a soft gradient tile with a note.
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(Brush.linearGradient(listOf(accent, Color(0xFF3B1A12)))),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", color = Paper.copy(alpha = 0.85f), fontSize = (size.value * 0.4f).sp)
        }
    }
}

@Composable
private fun Progress(nowPlaying: NowPlaying, accent: Color, onSeek: (Long) -> Unit) {
    // Ticks only while playing and only while the screen is visible.
    val position by remember(nowPlaying) {
        flow {
            while (true) {
                emit(nowPlaying.positionAt(SystemClock.elapsedRealtime()))
                if (!nowPlaying.isPlaying) break
                delay(500)
            }
        }
    }.collectAsStateWithLifecycle(initialValue = nowPlaying.positionAt(SystemClock.elapsedRealtime()))
    val duration = nowPlaying.durationMs
    val playedFraction = (position.toFloat() / duration).coerceIn(0f, 1f)

    // While dragging, the bar follows the finger. After a seek, it holds the target until the
    // app reports its new position, so it doesn't jump back for a moment.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var pendingFraction by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(nowPlaying.positionMs, nowPlaying.positionUpdatedAt) { pendingFraction = null }
    val shownFraction = dragFraction ?: pendingFraction ?: playedFraction
    val shownPosition = (dragFraction ?: pendingFraction)?.let { (it * duration).toLong() } ?: position

    val currentOnSeek by rememberUpdatedState(onSeek)
    fun seek(fraction: Float) {
        pendingFraction = fraction
        currentOnSeek((fraction * duration).toLong())
    }
    val seekInput = if (nowPlaying.canSeek) {
        Modifier
            .pointerInput(duration) {
                detectTapGestures { offset -> seek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(duration) {
                fun fractionAt(x: Float) = (x / size.width).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = fractionAt(it.x) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = fractionAt(change.position.x)
                    },
                    onDragEnd = {
                        dragFraction?.let(::seek)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                )
            }
    } else {
        Modifier
    }
    val dragging = dragFraction != null

    Column(
        Modifier.semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(shownFraction, 0f..1f)
            if (nowPlaying.canSeek) {
                setProgress(label = "Seek") { target ->
                    seek(target.coerceIn(0f, 1f))
                    true
                }
            }
        },
    ) {
        // A tall touch area around the thin bar, so it's easy to hit.
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .then(seekInput),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(if (dragging) 6.dp else 4.dp)) {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(TrackColor, cornerRadius = radius)
                drawRoundRect(accent, size = size.copy(width = size.width * shownFraction), cornerRadius = radius)
                if (dragging) {
                    drawCircle(Paper, radius = 8.dp.toPx(), center = Offset(size.width * shownFraction, size.height / 2))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTrackTime(shownPosition), color = if (dragging) Paper else Muted, fontSize = 12.sp)
            Text(formatTrackTime(duration), color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TransportRow(
    nowPlaying: NowPlaying,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(TransportIcon.Previous, "Previous track", 48.dp, nowPlaying.canSkipPrevious, onPrevious)
        TransportButton(
            icon = if (nowPlaying.isPlaying) TransportIcon.Pause else TransportIcon.Play,
            label = if (nowPlaying.isPlaying) "Pause" else "Play",
            diameter = 60.dp,
            enabled = true,
            onClick = onPlayPause,
            filled = true,
        )
        TransportButton(TransportIcon.Next, "Next track", 48.dp, nowPlaying.canSkipNext, onNext)
    }
}

private enum class TransportIcon { Previous, Play, Pause, Next }

@Composable
private fun TransportButton(
    icon: TransportIcon,
    label: String,
    diameter: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (filled) Paper else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        val color = if (filled) CardBase else Paper
        Canvas(Modifier.size(diameter * if (filled) 0.36f else 0.42f)) {
            val w = size.width
            val h = size.height
            when (icon) {
                TransportIcon.Play -> drawPath(
                    Path().apply { moveTo(w * 0.15f, 0f); lineTo(w, h / 2); lineTo(w * 0.15f, h); close() },
                    color,
                )
                TransportIcon.Pause -> {
                    drawRect(color, topLeft = Offset(w * 0.12f, 0f), size = size.copy(width = w * 0.26f))
                    drawRect(color, topLeft = Offset(w * 0.62f, 0f), size = size.copy(width = w * 0.26f))
                }
                TransportIcon.Previous -> {
                    drawRect(color, topLeft = Offset(0f, 0f), size = size.copy(width = w * 0.14f))
                    drawPath(
                        Path().apply { moveTo(w, 0f); lineTo(w * 0.22f, h / 2); lineTo(w, h); close() },
                        color,
                    )
                }
                TransportIcon.Next -> {
                    drawRect(color, topLeft = Offset(w * 0.86f, 0f), size = size.copy(width = w * 0.14f))
                    drawPath(
                        Path().apply { moveTo(0f, 0f); lineTo(w * 0.78f, h / 2); lineTo(0f, h); close() },
                        color,
                    )
                }
            }
        }
    }
}

/** Compact pill shown on the full clock while music is available; tap to return to the player. */
@Composable
fun NowPlayingPill(nowPlaying: NowPlaying, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(50)
    Row(
        modifier
            .clip(pill)
            .background(Color(0xFF1A1715))
            .clickable(onClickLabel = "Show player", role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(nowPlaying.art, 32.dp, Amber)
        Text(
            nowPlaying.title,
            color = Paper,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (nowPlaying.artist != null) {
            Text(nowPlaying.artist, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Explains and requests notification access, which is needed to see other apps' music. */
@Composable
fun MusicAccessCard(onAllow: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    // Centered when it fits; scrolls on short screens or with large text instead of clipping the buttons.
    Box(
        modifier
            .fillMaxSize()
            .cardBackground(NeutralTint),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Text("SHOW WHAT'S PLAYING", color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em)
            Spacer(Modifier.height(8.dp))
            Text("See your music here", color = Paper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Allow notification access to show and control what Spotify, YouTube Music " +
                    "and other apps are playing. Notifications are never read or stored.",
                color = SecondaryText,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "If Android says the setting is restricted: App info → ⋮ → Allow restricted settings.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Paper)
                        .clickable(role = Role.Button, onClick = onAllow)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text("Allow access", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(role = Role.Button, onClick = onNotNow)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("Not now", color = Muted, fontSize = 15.sp)
                }
            }
        }
    }
}
