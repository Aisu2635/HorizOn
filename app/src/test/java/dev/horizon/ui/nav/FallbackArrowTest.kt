package dev.horizon.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackArrowTest {
    @Test fun `turns pick a side`() {
        assertEquals("↰", fallbackArrow("Turn left"))
        assertEquals("↱", fallbackArrow("Slight right"))
    }

    @Test fun `u-turn wins over its side`() = assertEquals("↶", fallbackArrow("Make a U-turn to the left"))

    @Test fun `anything else is straight ahead`() {
        assertEquals("↑", fallbackArrow("Head north"))
        assertEquals("↑", fallbackArrow("Starting navigation…"))
    }
}
