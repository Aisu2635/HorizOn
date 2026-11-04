package dev.horizon.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Uses the four sample covers from the lyrics mockup. */
class AlbumColorsTest {
    private fun cover(vararg colors: Long) = colors.map { it.toInt() to 100 }

    private val sunset = cover(0xFFF2A65A, 0xFFC2542D, 0xFF3B1A12)
    private val lagoon = cover(0xFF7FD8E6, 0xFF1F7A9C, 0xFF0B2533)
    private val midnight = cover(0xFF1E2740, 0xFF10162A, 0xFF04060C)
    private val monochrome = cover(0xFFE6E6E6, 0xFF6E6E6E, 0xFF121212)

    @Test fun `bright cover keeps its colour`() {
        val c = AlbumColorRule.pick(sunset)!!
        assertTrue(c.fromCover)
        val hsl = rgbToHsl(c.accent)
        assertEquals(30f, hsl.h, 4f)
        assertEquals(0.65f, hsl.l, 0.03f)
    }

    @Test fun `the most colourful part of the cover wins`() {
        val hsl = rgbToHsl(AlbumColorRule.pick(lagoon)!!.accent)
        assertEquals(188f, hsl.h, 4f)
        assertTrue(hsl.l >= 0.6f)
    }

    @Test fun `dark cover is lifted, not dropped`() {
        val c = AlbumColorRule.pick(midnight)!!
        assertTrue(c.fromCover)
        val hsl = rgbToHsl(c.accent)
        assertEquals(224f, hsl.h, 8f)
        assertTrue(hsl.l >= 0.6f)
        assertTrue(hsl.s >= 0.5f)
    }

    @Test fun `greyscale cover gets warm white`() {
        val c = AlbumColorRule.pick(monochrome)!!
        assertFalse(c.fromCover)
        assertEquals(AlbumColorRule.WARM_WHITE, c.accent)
    }

    @Test fun `tiny swatches are ignored`() {
        // 1% of bright red on a grey cover is noise, not the cover's colour.
        val c = AlbumColorRule.pick(listOf(0xFF808080.toInt() to 990, 0xFFFF0000.toInt() to 10))!!
        assertFalse(c.fromCover)
    }

    @Test fun `tint is dark`() = assertTrue(rgbToHsl(AlbumColorRule.pick(sunset)!!.tint).l < 0.15f)

    @Test fun `no swatches gives nothing`() = assertNull(AlbumColorRule.pick(emptyList()))

    @Test fun `hsl round-trips`() {
        listOf(0xFFF2A65A, 0xFF1F7A9C, 0xFF04060C, 0xFFFFFFFF, 0xFF000000).forEach {
            val rgb = it.toInt()
            val back = hslToRgb(rgbToHsl(rgb))
            listOf(16, 8, 0).forEach { shift ->
                assertTrue(abs(((rgb shr shift) and 0xFF) - ((back shr shift) and 0xFF)) <= 1)
            }
        }
    }
}
