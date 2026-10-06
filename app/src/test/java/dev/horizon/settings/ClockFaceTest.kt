package dev.horizon.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockFaceTest {
    @Test fun `stored key round-trips`() =
        ClockFace.entries.forEach { assertEquals(it, ClockFace.fromKey(it.name)) }

    @Test fun `missing or unknown key falls back to default`() {
        assertEquals(ClockFace.Default, ClockFace.fromKey(null))
        assertEquals(ClockFace.Default, ClockFace.fromKey("Sundial"))
    }
}
