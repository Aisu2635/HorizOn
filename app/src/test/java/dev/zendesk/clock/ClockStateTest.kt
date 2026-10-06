package dev.zendesk.clock

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockStateTest {
    @Test fun `24h pads hour`() = assertEquals("09:05", formatClockTime(9, 5, is24Hour = true))
    @Test fun `12h midnight is 12`() = assertEquals("12:00", formatClockTime(0, 0, is24Hour = false))
    @Test fun `12h noon is 12`() = assertEquals("12:30", formatClockTime(12, 30, is24Hour = false))
    @Test fun `12h afternoon`() = assertEquals("9:41", formatClockTime(21, 41, is24Hour = false))
}
