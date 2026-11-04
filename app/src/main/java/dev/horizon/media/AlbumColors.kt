package dev.horizon.media

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A colour in HSL: hue 0..360, saturation and lightness 0..1. */
data class Hsl(val h: Float, val s: Float, val l: Float)

/** Colours for everything music-related, picked from the album cover. ARGB ints, always opaque. */
data class AlbumColors(
    /** Lines, glow, progress, app name: bright enough to read on pure black. */
    val accent: Int,
    /** A dark wash for the top-left of the music card. */
    val tint: Int,
    /** False when the cover had no colour to use (black-and-white or greyscale art). */
    val fromCover: Boolean,
)

/**
 * The rule for album colours (Lyrics_plan.md, section 6). Pure Kotlin, so it is tested on the JVM.
 *
 * 1. Pick the swatch with the most visible colour: saturation × min(1, 3 × lightness), so a
 *    near-black swatch's "colour" counts for little.
 * 2. No colour on the cover: a warm white, rather than an accent the cover never had.
 * 3. Otherwise lift it until it reads on pure black. Dark covers get brightened, not dropped.
 */
object AlbumColorRule {
    /** Accent for covers with no colour. */
    const val WARM_WHITE = 0xFFECE4D8.toInt()

    private const val MIN_SATURATION = 0.15f
    private const val MIN_VISIBLE = 0.05f

    /** Card tint for covers with no colour: a barely warm grey. */
    val neutralTint: Int = hslToRgb(Hsl(30f, 0.06f, 0.09f))

    /**
     * Picks colours from the cover's swatches, given as (rgb, population) pairs as Palette reports
     * them. Swatches covering less than 2% of the cover are ignored as noise. Null when there are none.
     */
    fun pick(swatches: List<Pair<Int, Int>>): AlbumColors? {
        if (swatches.isEmpty()) return null
        val total = swatches.sumOf { it.second }.coerceAtLeast(1)
        val candidates = swatches.filter { it.second * 50 >= total }.ifEmpty { swatches }
        val best = candidates.map { rgbToHsl(it.first) }.maxBy(::visibleColour)
        if (best.s < MIN_SATURATION || visibleColour(best) < MIN_VISIBLE) {
            return AlbumColors(WARM_WHITE, neutralTint, fromCover = false)
        }
        val accent = Hsl(best.h, max(0.55f, best.s), best.l.coerceIn(0.62f, 0.74f))
        val tint = Hsl(best.h, min(0.6f, best.s), 0.12f)
        return AlbumColors(hslToRgb(accent), hslToRgb(tint), fromCover = true)
    }

    private fun visibleColour(c: Hsl) = c.s * min(1f, c.l * 3f)
}

fun rgbToHsl(argb: Int): Hsl {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    if (mx == mn) return Hsl(0f, 0f, l)
    val d = mx - mn
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        r -> (g - b) / d + (if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return Hsl(h, s, l)
}

fun hslToRgb(c: Hsl): Int {
    val chroma = (1f - abs(2f * c.l - 1f)) * c.s
    val hp = (c.h % 360f + 360f) % 360f / 60f
    val x = chroma * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(chroma, x, 0f)
        hp < 2f -> Triple(x, chroma, 0f)
        hp < 3f -> Triple(0f, chroma, x)
        hp < 4f -> Triple(0f, x, chroma)
        hp < 5f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    val m = c.l - chroma / 2f
    fun channel(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
}
