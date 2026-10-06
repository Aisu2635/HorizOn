package dev.horizon.clock

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ClockStateTest {
    @Test fun `24h pads hour`() = assertEquals("09:05", formatClockTime(9, 5, is24Hour = true))
    @Test fun `12h midnight is 12`() = assertEquals("12:00", formatClockTime(0, 0, is24Hour = false))
    @Test fun `12h noon is 12`() = assertEquals("12:30", formatClockTime(12, 30, is24Hour = false))
    @Test fun `12h afternoon`() = assertEquals("9:41", formatClockTime(21, 41, is24Hour = false))

    @Test fun `date line matches mockup`() =
        assertEquals("TUESDAY, OCT 6", formatClockDate(LocalDate.of(2026, 10, 6), Locale.US))

    @Test fun `next minute from mid-minute`() = assertEquals(15_000L, millisUntilNextMinute(3 * 60_000L + 45_000L))
    @Test fun `next minute from exact boundary is a full minute`() = assertEquals(60_000L, millisUntilNextMinute(120_000L))
}
