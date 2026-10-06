package dev.horizon.clock

/** Hand angles in degrees, clockwise from 12 o'clock. */
data class HandAngles(val hour: Float, val minute: Float)

fun handAngles(hour: Int, minute: Int): HandAngles = HandAngles(
    hour = (hour % 12) * 30f + minute * 0.5f,
    minute = minute * 6f,
)
