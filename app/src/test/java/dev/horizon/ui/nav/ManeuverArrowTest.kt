package dev.horizon.ui.nav

import dev.horizon.ui.nav.ManeuverArrow.Arrive
import dev.horizon.ui.nav.ManeuverArrow.SlightLeft
import dev.horizon.ui.nav.ManeuverArrow.SlightRight
import dev.horizon.ui.nav.ManeuverArrow.Straight
import dev.horizon.ui.nav.ManeuverArrow.TurnLeft
import dev.horizon.ui.nav.ManeuverArrow.TurnRight
import dev.horizon.ui.nav.ManeuverArrow.UTurnLeft
import dev.horizon.ui.nav.ManeuverArrow.UTurnRight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverArrowTest {
    private fun arrow(instruction: String, leftHandTraffic: Boolean = false) = maneuverArrow(instruction, leftHandTraffic)

    @Test fun `turns pick a side`() {
        assertEquals(TurnLeft, arrow("Turn left"))
        assertEquals(TurnRight, arrow("Turn right"))
    }

    @Test fun `keeping or forking is a slight turn`() {
        assertEquals(SlightRight, arrow("Keep right"))
        assertEquals(SlightLeft, arrow("Slight left"))
    }

    @Test fun `u-turn swings away from the kerb`() {
        assertEquals(UTurnRight, arrow("Make a U-turn", leftHandTraffic = true))
        assertEquals(UTurnLeft, arrow("Make a U-turn", leftHandTraffic = false))
    }

    @Test fun `a side named in the u-turn wins`() =
        assertEquals(UTurnLeft, arrow("Make a U-turn to the left", leftHandTraffic = true))

    @Test fun `arrival and anything else`() {
        assertEquals(Arrive, arrow("Arrive at destination"))
        assertEquals(Straight, arrow("Head north"))
        assertEquals(Straight, arrow("Starting navigation…"))
    }

    @Test fun `driving side by country`() {
        assertTrue(drivesOnLeft("IN"))
        assertTrue(drivesOnLeft("gb"))
        assertFalse(drivesOnLeft("US"))
        assertFalse(drivesOnLeft(""))
        assertFalse(drivesOnLeft(null))
    }

    @Test fun `short roads wrap as a whole`() {
        assertEquals("toward Main St", keepTogether("toward Main St"))
        val long = "toward Sardar Vallabhbhai Patel Road"
        assertEquals(long, keepTogether(long))
    }
}
