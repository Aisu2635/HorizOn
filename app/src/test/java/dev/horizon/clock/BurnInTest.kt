package dev.horizon.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BurnInTest {
    @Test fun `shift holds within a step`() =
        assertEquals(burnInShift(10), burnInShift(10L + BURN_IN_STEP_MINUTES - 1))

    @Test fun `shift moves on the next step`() =
        assertNotEquals(burnInShift(10), burnInShift(10L + BURN_IN_STEP_MINUTES))

    @Test fun `shift stays small over a day`() {
        for (minute in 0L until 24 * 60) {
            val (x, y) = burnInShift(minute)
            assertTrue("($x, $y) at minute $minute", abs(x) <= 4 && abs(y) <= 4)
        }
    }
}
