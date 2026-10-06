package dev.horizon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.horizon.device.Headset
import dev.horizon.ui.theme.Amber
import dev.horizon.ui.theme.ChargeGreen
import dev.horizon.ui.theme.Muted
import dev.horizon.ui.theme.Paper
import kotlinx.coroutines.delay

private val LowRed = Color(0xFFE5675C)
private val RingTrack = Color.White.copy(alpha = 0.12f)
private val BadgeSurface = Color(0xFF1A1715)

/** Green when well charged, amber when getting low, red when low. */
private fun levelColor(percent: Int?): Color = when {
    percent == null -> Muted
    percent <= 20 -> LowRed
    percent <= 50 -> Amber
    else -> ChargeGreen
}

private fun Headset.describe(): String =
    if (percent != null) "$name, battery $percent percent" else "$name connected"

/** How long the badge shows the headphone name before shrinking to ring + percentage. */
private const val BADGE_EXPANDED_MS = 6_000L

/** Duration of the expand/shrink animation. */
private const val BADGE_RESIZE_MS = 260

/**
 * Compact headphones badge: icon inside a ring gauge and the percentage. It shows the name
 * when the headphones connect, then shrinks; tap to show the name again (tap again to shrink).
 * The ring sweeps up to the level when it appears and animates on changes.
 */
@Composable
fun HeadsetBadge(headset: Headset, modifier: Modifier = Modifier) {
    // Starts expanded each time the badge appears (i.e. when headphones connect).
    var expanded by remember { mutableStateOf(true) }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(BADGE_EXPANDED_MS)
            expanded = false
        }
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(BadgeSurface)
            .clickable { expanded = !expanded }
            .clearAndSetSemantics {
                contentDescription = headset.describe()
                onClick(label = if (expanded) "Hide name" else "Show name") { expanded = !expanded; true }
            }
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BatteryRing(headset.percent, diameter = 34.dp, stroke = 3.dp)
        // One size animation only (the name's own), so the pill resizes in a single smooth motion.
        // Text fades out quickly before the width closes, and fades in after it opens.
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(tween(BADGE_RESIZE_MS, easing = FastOutSlowInEasing), clip = true) +
                fadeIn(tween(BADGE_RESIZE_MS / 2, delayMillis = BADGE_RESIZE_MS / 2)),
            exit = fadeOut(tween(BADGE_RESIZE_MS / 3)) +
                shrinkHorizontally(tween(BADGE_RESIZE_MS, easing = FastOutSlowInEasing), clip = true),
        ) {
            Text(
                headset.name,
                color = Paper,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .widthIn(max = 200.dp),
            )
        }
        if (headset.percent != null) {
            Text(
                "${headset.percent}%",
                color = levelColor(headset.percent),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/** Larger banner shown for a few seconds when headphones connect. */
@Composable
fun HeadsetConnectedBanner(headset: Headset, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(BadgeSurface)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .clearAndSetSemantics { contentDescription = "Connected: " + headset.describe() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            BatteryRing(headset.percent, diameter = 64.dp, stroke = 5.dp)
        }
        Column {
            Text(
                "CONNECTED",
                color = levelColor(headset.percent),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
            )
            Text(
                headset.name,
                color = Paper,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 280.dp),
            )
            Text(
                if (headset.percent != null) "Battery ${headset.percent}%" else "Battery level not reported",
                color = Muted,
                fontSize = 14.sp,
            )
        }
    }
}

/** Asks for the Nearby devices permission so the badge can show name and battery. */
@Composable
fun HeadsetPermissionChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(BadgeSurface)
            .clickable(role = Role.Button, onClickLabel = "Allow headphone battery", onClick = onClick)
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BatteryRing(percent = null, diameter = 34.dp, stroke = 3.dp)
        Text("Show headphone battery", color = Paper, fontSize = 14.sp, maxLines = 1)
    }
}

/** A ring gauge with a headphones glyph inside; the arc fills to [percent]. */
@Composable
private fun BatteryRing(percent: Int?, diameter: Dp, stroke: Dp) {
    val color by animateColorAsState(levelColor(percent), tween(600), label = "ringColor")
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(percent) {
        sweep.animateTo((percent ?: 0) / 100f, tween(900, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.size(diameter)) {
        val strokePx = stroke.toPx()
        val inset = strokePx / 2
        val arcSize = Size(size.width - strokePx, size.height - strokePx)
        drawArc(RingTrack, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(strokePx))
        if (percent != null) {
            drawArc(
                color, -90f, 360f * sweep.value, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
        }
        drawHeadphones(Paper, size.width * 0.46f)
    }
}

/** A simple headphones glyph: headband arc and two ear cups, centered. */
private fun DrawScope.drawHeadphones(color: Color, width: Float) {
    val left = center.x - width / 2
    val top = center.y - width * 0.45f
    val band = width * 0.11f
    drawArc(
        color, 180f, 180f, useCenter = false,
        topLeft = Offset(left + band / 2, top),
        size = Size(width - band, width * 0.9f),
        style = Stroke(band, cap = StrokeCap.Round),
    )
    val cupWidth = width * 0.26f
    val cupHeight = width * 0.42f
    val cupTop = center.y
    val radius = CornerRadius(cupWidth * 0.35f)
    drawRoundRect(color, Offset(left, cupTop), Size(cupWidth, cupHeight), radius)
    drawRoundRect(color, Offset(left + width - cupWidth, cupTop), Size(cupWidth, cupHeight), radius)
}
