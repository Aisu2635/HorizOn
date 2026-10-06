package dev.horizon.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

private const val MAPS = "com.google.android.apps.maps"
private const val NBSP = ' '

class NavParserTest {
    private val base = NavFields(
        packageName = MAPS,
        category = "navigation",
        isOngoing = true,
        title = "100${NBSP}m · Turn left toward Street A",
        subText = "Arrive 7:53 pm",
        shortCriticalText = "100${NBSP}m",
        progress = 1_000,
        progressMax = 5_000,
    )

    @Test fun `distance instruction and road are split`() {
        val info = NavParser.parse(base)!!
        assertEquals("100${NBSP}m", info.distanceToTurn)
        assertEquals("Turn left", info.instruction)
        assertEquals("toward Street A", info.road)
        assertFalse(info.imperial)
        assertFalse(info.starting)
    }

    @Test fun `bare instruction has no distance or road`() {
        val info = NavParser.parse(base.copy(title = "Head north", shortCriticalText = ""))!!
        assertNull(info.distanceToTurn)
        assertEquals("Head north", info.instruction)
        assertNull(info.road)
    }

    @Test fun `instruction without a road`() {
        val info = NavParser.parse(base.copy(title = "100${NBSP}m · Turn left"))!!
        assertEquals("Turn left", info.instruction)
        assertNull(info.road)
    }

    @Test fun `distance falls back to the title when short text is missing`() =
        assertEquals("100${NBSP}m", NavParser.parse(base.copy(shortCriticalText = null))!!.distanceToTurn)

    @Test fun `arrival time is picked out`() {
        assertEquals("7:53 pm", NavParser.parse(base)!!.etaTime)
        assertEquals("19:59", NavParser.parse(base.copy(subText = "Arrive 19:59"))!!.etaTime)
    }

    @Test fun `unknown arrival wording is kept whole`() {
        val info = NavParser.parse(base.copy(subText = "Llegada 19:59"))!!
        assertEquals("Llegada 19:59", info.arrival)
        assertNull(info.etaTime)
    }

    @Test fun `remaining distance and progress come from the route`() {
        val info = NavParser.parse(base)!!
        assertEquals(4_000, info.remainingMeters)
        assertEquals(0.2f, info.tripProgress!!, 0.0001f)
    }

    @Test fun `route not known yet means starting`() {
        val info = NavParser.parse(base.copy(title = "Starting navigation…", shortCriticalText = null, progress = 0, progressMax = 0))!!
        assertTrue(info.starting)
        assertNull(info.remainingMeters)
        assertNull(info.tripProgress)
    }

    @Test fun `feet and miles are imperial`() {
        assertTrue(NavParser.parse(base.copy(shortCriticalText = "350${NBSP}ft"))!!.imperial)
        assertTrue(NavParser.parse(base.copy(shortCriticalText = "0.3 mi"))!!.imperial)
    }

    @Test fun `other apps, categories and dismissible notifications are ignored`() {
        assertNull(NavParser.parse(base.copy(packageName = "com.example.chat")))
        assertNull(NavParser.parse(base.copy(category = null)))
        assertNull(NavParser.parse(base.copy(isOngoing = false)))
        assertNull(NavParser.parse(base.copy(title = " ")))
    }

    @Test fun `route distances format in the app's units`() {
        assertEquals("850 m", formatRouteDistance(853, imperial = false, Locale.US))
        assertEquals("4.2 km", formatRouteDistance(4_212, imperial = false, Locale.US))
        assertEquals("82.4 km", formatRouteDistance(82_400, imperial = false, Locale.US))
        assertEquals("150 km", formatRouteDistance(150_000, imperial = false, Locale.US))
        assertEquals("100 ft", formatRouteDistance(31, imperial = true, Locale.US))
        assertEquals("2.9 mi", formatRouteDistance(4_718, imperial = true, Locale.US))
        assertEquals("4,2 km", formatRouteDistance(4_212, imperial = false, Locale.GERMANY))
    }
}
