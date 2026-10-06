package dev.horizon.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.hypot

/** Our own maneuver arrows, used when the navigation app sends no arrow image (and in the demo). */
enum class ManeuverArrow { Straight, TurnLeft, TurnRight, SlightLeft, SlightRight, UTurnLeft, UTurnRight, Arrive }

/** Countries that drive on the left, where a U-turn swings to the right. */
private val LEFT_HAND_TRAFFIC = setOf(
    "AG", "AI", "AU", "BB", "BD", "BM", "BN", "BS", "BT", "BW", "CY", "DM", "FJ", "FK", "GB", "GD", "GG", "GY",
    "HK", "ID", "IE", "IM", "IN", "JE", "JM", "JP", "KE", "KI", "KN", "KY", "LC", "LK", "LS", "MO", "MS", "MT",
    "MU", "MV", "MW", "MY", "MZ", "NA", "NP", "NZ", "PG", "PK", "SB", "SC", "SG", "SR", "SZ", "TC", "TH", "TL",
    "TO", "TT", "TZ", "UG", "VC", "VG", "VI", "WS", "ZA", "ZM", "ZW",
)

/** Whether traffic drives on the left in [countryCode] (ISO 3166, e.g. "IN"). */
fun drivesOnLeft(countryCode: String?): Boolean = countryCode?.uppercase() in LEFT_HAND_TRAFFIC

/**
 * Picks an arrow from an English instruction. A side named in the instruction wins; otherwise a
 * U-turn swings away from the kerb: right where traffic drives on the left (e.g. India), left elsewhere.
 */
fun maneuverArrow(instruction: String, leftHandTraffic: Boolean): ManeuverArrow {
    val text = instruction.lowercase()
    val left = "left" in text
    val right = "right" in text
    return when {
        "arrive" in text || "destination" in text -> ManeuverArrow.Arrive
        "u-turn" in text || "u turn" in text -> when {
            left -> ManeuverArrow.UTurnLeft
            right -> ManeuverArrow.UTurnRight
            leftHandTraffic -> ManeuverArrow.UTurnRight
            else -> ManeuverArrow.UTurnLeft
        }
        (left || right) && listOf("keep", "slight", "bear", "fork").any { it in text } ->
            if (left) ManeuverArrow.SlightLeft else ManeuverArrow.SlightRight
        left -> ManeuverArrow.TurnLeft
        right -> ManeuverArrow.TurnRight
        else -> ManeuverArrow.Straight
    }
}

/** Draws [arrow] as a bold, rounded line with a solid head, filling the given square. */
@Composable
fun ManeuverArrowIcon(arrow: ManeuverArrow, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Left-hand shapes are the right-hand ones mirrored.
        val mirrored = arrow in setOf(ManeuverArrow.TurnLeft, ManeuverArrow.SlightLeft, ManeuverArrow.UTurnLeft)
        scale(if (mirrored) -1f else 1f, 1f) {
            when (arrow) {
                ManeuverArrow.Straight -> drawArrow(color, tip = u(0.5f, 0.1f), from = u(0.5f, 0.92f)) { u(0.5f, 0.92f) }
                ManeuverArrow.TurnLeft, ManeuverArrow.TurnRight ->
                    drawArrow(color, tip = u(0.9f, 0.38f), from = u(0.3f, 0.92f)) {
                        lineTo(u(0.3f, 0.6f))
                        quadraticTo(u(0.3f, 0.38f), u(0.52f, 0.38f))
                        u(0.52f, 0.38f)
                    }
                ManeuverArrow.SlightLeft, ManeuverArrow.SlightRight ->
                    drawArrow(color, tip = u(0.8f, 0.12f), from = u(0.38f, 0.92f)) {
                        lineTo(u(0.38f, 0.6f))
                        quadraticTo(u(0.38f, 0.46f), u(0.5f, 0.38f))
                        u(0.5f, 0.38f)
                    }
                ManeuverArrow.UTurnLeft, ManeuverArrow.UTurnRight ->
                    // Drawn swinging right; mirrored for a left U-turn.
                    drawArrow(color, tip = u(0.7f, 0.84f), from = u(0.3f, 0.92f)) {
                        lineTo(u(0.3f, 0.42f))
                        cubicTo(u(0.3f, 0.1f), u(0.7f, 0.1f), u(0.7f, 0.42f))
                        u(0.7f, 0.42f)
                    }
                ManeuverArrow.Arrive -> {
                    val stroke = size.minDimension * 0.12f
                    drawCircle(color, radius = size.minDimension * 0.3f, style = Stroke(stroke))
                    drawCircle(color, radius = size.minDimension * 0.12f)
                }
            }
        }
    }
}

/** A point in the unit square, scaled to this canvas. */
private fun DrawScope.u(x: Float, y: Float) = Offset(x * size.width, y * size.height)

private fun Path.lineTo(p: Offset) = lineTo(p.x, p.y)

private fun Path.quadraticTo(control: Offset, end: Offset) = quadraticTo(control.x, control.y, end.x, end.y)

private fun Path.cubicTo(c1: Offset, c2: Offset, end: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)

/**
 * Strokes a line from [from] through whatever [shaft] adds (it returns where it ended), straight
 * on to the base of a solid head whose point is [tip].
 */
private fun DrawScope.drawArrow(color: Color, tip: Offset, from: Offset, shaft: Path.() -> Offset) {
    val stroke = size.minDimension * 0.13f
    val head = size.minDimension * 0.22f
    val path = Path()
    path.moveTo(from.x, from.y)
    val last = path.shaft()
    val dx = tip.x - last.x
    val dy = tip.y - last.y
    val length = hypot(dx, dy).coerceAtLeast(0.001f)
    val dir = Offset(dx / length, dy / length)
    val base = tip - dir * head
    // Run the shaft slightly into the head so the round cap is hidden.
    val shaftEnd = base + dir * (stroke * 0.3f)
    path.lineTo(shaftEnd.x, shaftEnd.y)
    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val side = Offset(-dir.y, dir.x) * (head * 0.8f)
    drawPath(
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo((base + side).x, (base + side).y)
            lineTo((base - side).x, (base - side).y)
            close()
        },
        color,
    )
}
