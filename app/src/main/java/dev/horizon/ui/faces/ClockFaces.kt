package dev.horizon.ui.faces

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.horizon.clock.formatClockTime
import dev.horizon.settings.ClockFace

/** Everything a clock face needs to draw the current time. */
data class ClockTime(val hour: Int, val minute: Int, val is24Hour: Boolean) {
    /** e.g. "9:41" or "21:41". */
    val text: String get() = formatClockTime(hour, minute, is24Hour)
}

/** Draws the selected [face]; each face fills the space it is given. */
@Composable
fun ClockFaceContent(face: ClockFace, time: ClockTime, modifier: Modifier = Modifier) {
    when (face) {
        ClockFace.Digital -> DigitalFace(time, modifier)
        ClockFace.Analog -> AnalogFace(time, modifier)
        ClockFace.Flip -> FlipFace(time, modifier)
    }
}
