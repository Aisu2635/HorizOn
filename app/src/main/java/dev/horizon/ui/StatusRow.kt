package dev.horizon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.horizon.device.BatteryStatus
import dev.horizon.ui.theme.ChargeGreen
import dev.horizon.ui.theme.Muted

private val LabelStyle = TextStyle(
    fontSize = 15.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.08.em,
    color = Muted,
)

/** Date on the left, battery on the right; used above the full-screen clock faces. */
@Composable
fun StatusRow(
    date: String,
    battery: BatteryStatus?,
    modifier: Modifier = Modifier,
    accessory: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateLabel(date)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            accessory()
            if (battery != null) BatteryLabel(battery)
        }
    }
}

@Composable
fun DateLabel(date: String, modifier: Modifier = Modifier) {
    Text(date, style = LabelStyle, modifier = modifier)
}

/** Battery icon plus "82%", or "Charging · 82%" when [spelledOut]. */
@Composable
fun BatteryLabel(battery: BatteryStatus, modifier: Modifier = Modifier, spelledOut: Boolean = false) {
    val description = "Battery ${battery.percent} percent" + if (battery.pluggedIn) ", charging" else ""
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        BatteryIcon(battery)
        val text = if (spelledOut && battery.pluggedIn) "Charging · ${battery.percent}%" else "${battery.percent}%"
        Text(text, style = LabelStyle)
    }
}

@Composable
private fun BatteryIcon(battery: BatteryStatus) {
    val color = if (battery.pluggedIn) ChargeGreen else Muted
    Canvas(Modifier.size(width = 20.dp, height = 11.dp)) {
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(2.5.dp.toPx()),
            style = Stroke(stroke),
        )
        val inset = stroke + 1.dp.toPx()
        val fullWidth = size.width - inset * 2
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(fullWidth * battery.percent.coerceIn(0, 100) / 100f, size.height - inset * 2),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}
