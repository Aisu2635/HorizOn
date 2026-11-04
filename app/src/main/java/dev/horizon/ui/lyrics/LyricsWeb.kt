package dev.horizon.ui.lyrics

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import dev.horizon.lyrics.Lyrics
import dev.horizon.lyrics.TimedWord
import dev.horizon.lyrics.countAt
import dev.horizon.lyrics.timedWords
import dev.horizon.media.NowPlaying
import dev.horizon.ui.theme.Muted
import dev.horizon.ui.theme.Paper
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** How long the line to a new word takes to draw. */
private const val EDGE_DRAW_MS = 380L

/** A new word fades in over this long. */
private const val APPEAR_MS = 300L

/** Earlier lines start at half brightness and are gone this long after their line ends. */
private const val FADE_MS = 9_000f

/** A jump of more than this many words (a seek forward) rebuilds the web from the current line. */
private const val MAX_CATCH_UP = 12

/** Share of words that also link to a nearby older word, turning the chain into a web. */
private const val EXTRA_LINK_CHANCE = 0.45f

/**
 * Time-synced lyrics drawn as a growing web (Lyrics_plan.md, section 4): each word appears near
 * the last one and a line draws to it; the newest word glows in [accent]; earlier lines fade
 * into a trail and disappear. Follows [nowPlaying]'s position, so pause, seek and skip just work.
 */
@Composable
fun LyricsWeb(lyrics: Lyrics, nowPlaying: NowPlaying, accent: Color, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val engine = remember(lyrics) { WebEngine(lyrics.timedWords()) }
    val track by rememberUpdatedState(nowPlaying)
    val measurer = rememberTextMeasurer(cacheSize = 64)
    // Only the draw phase reads the frame clock, so each frame redraws without recomposing.
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(engine) {
        while (true) withFrameMillis { frame = it }
    }
    Canvas(modifier) {
        if (frame < 0L) return@Canvas // Reading the frame clock makes this redraw every frame.
        val now = SystemClock.elapsedRealtime()
        engine.update(track.positionAt(now), now, size.width, size.height, density)
        engine.draw(this, now, accent, measurer, reduceMotion)
    }
}

private class WebNode(
    val word: TimedWord,
    val x: Float,
    val y: Float,
    val born: Long,
    var prev: WebNode?,
    val phase: Float,
) {
    var alpha = 1f
    val layouts = HashMap<Int, TextLayoutResult>(3)
}

private class WebLink(val a: WebNode, val b: WebNode, val born: Long)

private class WebEngine(private val words: List<TimedWord>) {
    private val nodes = ArrayList<WebNode>()
    private val links = ArrayList<WebLink>()
    private var spawned = 0
    private val random = Random(words.size)

    /** When each line ends: the first word of the next line with words, for fading. */
    private val lineEnds: Map<Int, Long> = run {
        val firstOfLine = LinkedHashMap<Int, Long>()
        words.forEach { firstOfLine.putIfAbsent(it.line, it.timeMs) }
        val starts = firstOfLine.entries.toList()
        starts.mapIndexed { i, entry -> entry.key to (starts.getOrNull(i + 1)?.value ?: (entry.value + 6_000)) }.toMap()
    }

    fun update(positionMs: Long, now: Long, width: Float, height: Float, density: Float) {
        val target = words.countAt(positionMs)
        if (target < spawned || target - spawned > MAX_CATCH_UP) {
            // Seek or new position: start again from the current line.
            nodes.clear()
            links.clear()
            spawned = if (target == 0) 0 else words.indexOfFirst { it.line == words[target - 1].line }
        }
        while (spawned < target) spawn(spawned++, now, width, height, density)

        val current = nodes.lastOrNull()
        for (n in nodes) {
            n.alpha = if (n.word.line == current?.word?.line) {
                1f
            } else {
                val ended = lineEnds[n.word.line] ?: n.word.timeMs
                (0.5f - (positionMs - ended) / FADE_MS).coerceIn(0f, 0.5f)
            }
        }
        nodes.removeAll { it.alpha <= 0f && it !== current }
        for (n in nodes) if (n.prev?.let { it.alpha <= 0f || it !in nodes } == true) n.prev = null
        links.removeAll { it.a.alpha <= 0f || it.b.alpha <= 0f || it.a !in nodes }
    }

    private fun spawn(index: Int, now: Long, width: Float, height: Float, density: Float) {
        val prev = nodes.lastOrNull()
        // Words sit between the header and the bottom edge, with room for text to their right.
        val left = width * 0.07f
        val right = width * 0.80f
        val top = height * 0.24f
        val bottom = height * 0.88f
        var best = Offset(width / 2, height * 0.55f)
        var bestScore = -1f
        repeat(18) {
            val p = if (prev == null) {
                Offset(width * (0.3f + random.nextFloat() * 0.35f), height * (0.4f + random.nextFloat() * 0.3f))
            } else {
                val angle = random.nextFloat() * 2f * PI.toFloat()
                val r = (95f + random.nextFloat() * 85f) * density
                Offset(prev.x + cos(angle) * r, prev.y + sin(angle) * r * 0.75f)
            }
            val c = Offset(p.x.coerceIn(left, right), p.y.coerceIn(top, bottom))
            // Farthest from the words still showing, so text doesn't pile up.
            var d = Float.MAX_VALUE
            for (n in nodes) if (n.alpha > 0.05f) d = min(d, hypot((n.x - c.x) * 0.8f, n.y - c.y))
            if (d > bestScore) {
                bestScore = d
                best = c
            }
        }
        val node = WebNode(words[index], best.x, best.y, now, prev, random.nextFloat() * 6.28f)
        nodes += node
        if (random.nextFloat() < EXTRA_LINK_CHANCE) {
            val reach = 230f * density
            val near = nodes.filter { it !== node && it !== prev && it.alpha > 0.25f && hypot(it.x - node.x, it.y - node.y) < reach }
            if (near.isNotEmpty()) links += WebLink(near[random.nextInt(near.size)], node, now)
        }
    }

    fun draw(scope: DrawScope, now: Long, accent: Color, measurer: TextMeasurer, reduceMotion: Boolean) {
        val current = nodes.lastOrNull() ?: return
        with(scope) { drawWeb(current, now, accent, measurer, reduceMotion) }
    }

    private fun DrawScope.drawWeb(current: WebNode, now: Long, accent: Color, measurer: TextMeasurer, reduceMotion: Boolean) {
        val drift = if (reduceMotion) 0f else density
        fun pos(n: WebNode) = Offset(
            n.x + sin(now / 1900f + n.phase) * 3f * drift,
            n.y + cos(now / 2300f + n.phase) * 2.4f * drift,
        )

        fun edge(a: WebNode, b: WebNode, born: Long, emphasis: Boolean) {
            val alpha = min(a.alpha, b.alpha) * if (emphasis) 0.8f else 0.25f
            if (alpha <= 0f) return
            val pa = pos(a)
            val pb = pos(b)
            val t = ((now - born).toFloat() / EDGE_DRAW_MS).coerceIn(0f, 1f)
            drawLine(
                color = if (emphasis) accent.copy(alpha = alpha) else Paper.copy(alpha = alpha),
                start = pa,
                end = pa + (pb - pa) * t,
                strokeWidth = (if (emphasis) 1.6f else 1f) * density,
                cap = StrokeCap.Round,
            )
        }
        for (n in nodes) n.prev?.let { edge(it, n, n.born, n.word.line == current.word.line) }
        for (l in links) edge(l.a, l.b, l.born, false)

        for (n in nodes) {
            val appear = ((now - n.born).toFloat() / APPEAR_MS).coerceIn(0f, 1f)
            val alpha = n.alpha * appear
            if (alpha <= 0f) continue
            val p = pos(n)
            val isCurrent = n === current
            val sameLine = n.word.line == current.word.line
            if (isCurrent) {
                val r = 26f * density
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f * alpha), Color.Transparent), p, r), r, p)
            }
            drawCircle(
                color = if (isCurrent) accent.copy(alpha = alpha) else Paper.copy(alpha = alpha * if (sameLine) 0.9f else 0.5f),
                radius = (if (isCurrent) 4f else 2.6f) * density,
                center = p,
            )
            val textSize = when {
                isCurrent -> 28
                sameLine -> 20
                else -> 16
            }
            val layout = n.layouts.getOrPut(textSize) {
                measurer.measure(
                    n.word.text,
                    TextStyle(fontSize = textSize.sp, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium),
                    density = Density(density, fontScale),
                )
            }
            val gap = 12f * density
            val textX = if (p.x + gap + layout.size.width < size.width) p.x + gap else p.x - gap - layout.size.width
            val color = when {
                isCurrent -> Paper
                sameLine -> Paper.copy(alpha = 0.85f)
                else -> Muted
            }
            drawText(layout, color = color, topLeft = Offset(textX, p.y - layout.size.height / 2f), alpha = alpha)
        }
    }
}
