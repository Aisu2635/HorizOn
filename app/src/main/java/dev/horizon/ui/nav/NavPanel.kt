package dev.horizon.ui.nav

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.horizon.device.BatteryStatus
import dev.horizon.nav.NavInfo
import dev.horizon.nav.NavState
import dev.horizon.nav.formatRouteDistance
import dev.horizon.ui.BatteryLabel
import dev.horizon.ui.DateLabel
import dev.horizon.ui.theme.Amber
import dev.horizon.ui.theme.Muted
import dev.horizon.ui.theme.Paper
import dev.horizon.ui.theme.PillSurface

/**
 * Turn-by-turn directions mirrored from Google Maps: the next maneuver, then ETA and distance
 * left. Fills the left half of the split view, or the whole screen when nothing is playing.
 * Double-tap to open Maps; a single tap behaves like a tap anywhere else.
 */
@Composable
fun NavPanel(
    nav: NavState,
    header: String,
    battery: BatteryStatus?,
    headsetSlot: @Composable () -> Unit,
    onTap: () -> Unit,
    onOpenApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appName = rememberAppName(nav.packageName)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(onTap, onOpenApp) {
                detectTapGestures(onTap = { onTap() }, onDoubleTap = { onOpenApp() })
            }
            .semantics {
                customActions = listOf(CustomAccessibilityAction("Open $appName") { onOpenApp(); true })
            },
    ) {
        // Scale with the space we get: half the screen in split view, all of it otherwise.
        val iconSize = min(96.dp, maxHeight * 0.24f)
        val distanceSize = min(maxWidth * 0.11f, maxHeight * 0.15f).coerceAtLeast(32.dp)
        Column(Modifier.fillMaxSize()) {
            DateLabel(header)
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = nav.info,
                // The distance ticks down in place; a new maneuver fades in.
                contentKey = { "${it.instruction}|${it.road}" },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "maneuver",
            ) { info ->
                Maneuver(nav, info, iconSize, distanceSize)
            }
            // A little less space below than above, so the trip facts don't drift away from the turn.
            Spacer(Modifier.weight(0.6f))
            TripRow(nav.info)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                headsetSlot()
                if (battery != null) BatteryLabel(battery, spelledOut = true)
                Spacer(Modifier.weight(1f))
                Text("via $appName", color = Muted, fontSize = 13.sp, letterSpacing = 0.04.em, maxLines = 1)
            }
        }
    }
}

/** Arrow, distance to the turn, and the instruction. */
@Composable
private fun Maneuver(nav: NavState, info: NavInfo, iconSize: Dp, distanceSize: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        ManeuverIcon(nav, info, Modifier.size(iconSize))
        Column {
            if (info.distanceToTurn != null) {
                val fontSize = with(LocalDensity.current) { distanceSize.toSp() }
                Text(
                    info.distanceToTurn,
                    color = Paper,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.02).em,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Paper)) { append(info.instruction) }
                    if (info.road != null) {
                        append(" ")
                        withStyle(SpanStyle(color = Muted)) { append(info.road) }
                    }
                },
                // With no distance (e.g. "Head north") the instruction is the headline, so it gets more room.
                fontSize = if (info.distanceToTurn == null) 34.sp else 24.sp,
                lineHeight = if (info.distanceToTurn == null) 40.sp else 30.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                // Announced when the maneuver changes, not on every distance update.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Maps' own arrow (white on transparent), tinted; a simple glyph when there is none. */
@Composable
private fun ManeuverIcon(nav: NavState, info: NavInfo, modifier: Modifier) {
    val icon = nav.maneuverIcon
    if (icon != null) {
        val image = remember(icon) { icon.asImageBitmap() }
        Image(image, contentDescription = null, colorFilter = ColorFilter.tint(Paper), modifier = modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(fallbackArrow(info.instruction), color = Paper, fontSize = 56.sp)
        }
    }
}

/** A rough arrow from an English instruction; straight ahead for anything else. */
internal fun fallbackArrow(instruction: String): String {
    val text = instruction.lowercase()
    return when {
        "u-turn" in text -> "↶"
        "left" in text -> "↰"
        "right" in text -> "↱"
        else -> "↑"
    }
}

/** ETA and distance left, over a thin trip progress bar. Hidden until the route is known. */
@Composable
private fun TripRow(info: NavInfo) {
    val locale = LocalLocale.current.platformLocale
    val eta = info.etaTime ?: info.arrival
    val remaining = info.remainingMeters?.let { formatRouteDistance(it, info.imperial, locale) }
    if (eta == null && remaining == null) return
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            // Show "ETA" only when the time was picked out; otherwise Maps' own wording already says it.
            if (eta != null) TripFact(if (info.etaTime != null) "ETA" else null, eta)
            if (remaining != null) TripFact("LEFT", remaining)
        }
        val progress = info.tripProgress
        if (progress != null) {
            Spacer(Modifier.height(14.dp))
            TripProgress(progress)
        }
    }
}

@Composable
private fun TripFact(label: String?, value: String) {
    Column(Modifier.clearAndSetSemantics { contentDescription = listOfNotNull(label, value).joinToString(" ") }) {
        if (label != null) Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em)
        Text(value, color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TripProgress(progress: Float) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(4.dp),
    ) {
        val y = size.height / 2
        val stroke = size.height
        drawLine(PillSurface, Offset(stroke / 2, y), Offset(size.width - stroke / 2, y), stroke, StrokeCap.Round)
        val end = stroke / 2 + (size.width - stroke) * progress.coerceIn(0f, 1f)
        drawLine(Amber, Offset(stroke / 2, y), Offset(end, y), stroke, StrokeCap.Round)
    }
}

/** Compact directions shown under the full-screen clock; tap to bring the directions back. */
@Composable
fun DirectionsPill(nav: NavState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(50)
    Row(
        modifier
            .clip(pill)
            .background(PillSurface)
            .clickable(onClickLabel = "Show directions", role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ManeuverIcon(nav, nav.info, Modifier.size(28.dp))
        nav.info.distanceToTurn?.let {
            Text(it, color = Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(
            nav.info.instruction,
            color = Muted,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Maps calls itself just "Maps" on the phone; spell out which one. */
private val KNOWN_APP_NAMES = mapOf(
    "com.google.android.apps.maps" to "Google Maps",
    "com.google.android.apps.navlite" to "Google Maps Go",
)

@Composable
private fun rememberAppName(packageName: String): String {
    val context = LocalContext.current
    return remember(packageName) {
        KNOWN_APP_NAMES[packageName] ?: context.appLabel(packageName) ?: "Maps"
    }
}

private fun Context.appLabel(packageName: String): String? = try {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
} catch (_: Exception) {
    null
}
