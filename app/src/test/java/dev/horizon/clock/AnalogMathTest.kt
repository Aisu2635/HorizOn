package dev.horizon.clock

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalogMathTest {
    @Test fun `midnight points both hands up`() = assertEquals(HandAngles(0f, 0f), handAngles(0, 0))
    @Test fun `three oclock`() = assertEquals(HandAngles(90f, 0f), handAngles(15, 0))
    @Test fun `hour hand creeps with minutes`() = assertEquals(HandAngles(321f, 252f), handAngles(22, 42))
}
