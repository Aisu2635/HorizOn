package dev.horizon.lyrics

/** One word of a lyric line. [timeMs] is set only by "enhanced" LRC files that time each word. */
data class LyricWord(val text: String, val timeMs: Long? = null)

/** A line of lyrics starting at [timeMs]. An empty line is a break (an instrumental gap). */
data class LyricLine(val timeMs: Long, val words: List<LyricWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
}

/** Time-synced lyrics, lines in time order. */
data class Lyrics(val lines: List<LyricLine>)

/** A word with the time it is sung, after spreading untimed words across their line. */
data class TimedWord(val line: Int, val text: String, val timeMs: Long)

/**
 * Parses LRC text: `[mm:ss.xx]Line`, several time tags on one line, `[offset:±ms]`, and enhanced
 * `<mm:ss.xx>word` timing. Metadata tags (`[ar:…]`, `[ti:…]`) and untimed lines are ignored.
 */
object LrcParser {
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String): Lyrics {
        // A positive offset means the lyrics should show earlier.
        val offset = OFFSET_TAG.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            var rest = raw.trim()
            val times = mutableListOf<Long>()
            while (true) {
                val m = TIME_TAG.matchAt(rest, 0) ?: break
                times += millis(m) - offset
                rest = rest.substring(m.range.last + 1)
            }
            if (times.isEmpty()) continue
            val words = words(rest.trim(), offset)
            times.forEach { lines += LyricLine(it.coerceAtLeast(0), words) }
        }
        return Lyrics(lines.sortedBy { it.timeMs })
    }

    private fun words(text: String, offset: Long): List<LyricWord> {
        if (!WORD_TAG.containsMatchIn(text)) {
            return text.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { LyricWord(it) }
        }
        val out = mutableListOf<LyricWord>()
        var time: Long? = null
        var last = 0
        fun flush(chunk: String) {
            chunk.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEachIndexed { i, w ->
                // Only the first word after a tag carries its time.
                out += LyricWord(w, if (i == 0) time else null)
            }
        }
        for (m in WORD_TAG.findAll(text)) {
            flush(text.substring(last, m.range.first))
            time = (millis(m) - offset).coerceAtLeast(0)
            last = m.range.last + 1
        }
        flush(text.substring(last))
        return out
    }

    private fun millis(m: MatchResult): Long {
        val (min, sec, frac) = m.destructured
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
    }
}

/** A line is sung over at most this long; the rest of a longer gap is a pause. */
private const val MAX_LINE_MS = 6_000L

/** Words finish this share of the way to the next line, leaving a beat before it starts. */
private const val LINE_FILL = 0.85

/** Every word with its time: enhanced timings as given, otherwise spread evenly across the line. */
fun Lyrics.timedWords(): List<TimedWord> {
    val out = mutableListOf<TimedWord>()
    lines.forEachIndexed { i, line ->
        if (line.words.isEmpty()) return@forEachIndexed
        val next = lines.getOrNull(i + 1)?.timeMs ?: (line.timeMs + MAX_LINE_MS)
        val span = ((next - line.timeMs).coerceIn(0, MAX_LINE_MS) * LINE_FILL).toLong()
        val n = line.words.size
        var previous = line.timeMs
        line.words.forEachIndexed { j, word ->
            val time = (word.timeMs ?: (line.timeMs + span * j / n)).coerceAtLeast(previous)
            out += TimedWord(i, word.text, time)
            previous = time
        }
    }
    return out
}

/** How many of these words have been sung by [positionMs] (they are in time order). */
fun List<TimedWord>.countAt(positionMs: Long): Int {
    var lo = 0
    var hi = size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (this[mid].timeMs <= positionMs) lo = mid + 1 else hi = mid
    }
    return lo
}
