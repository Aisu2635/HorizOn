package dev.horizon.ui.faces

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import dev.horizon.ui.theme.Amber
import dev.horizon.ui.theme.HorizOnTheme
import dev.horizon.ui.theme.Ink
import dev.horizon.ui.theme.Muted
import dev.horizon.ui.theme.Paper

// Neutral charcoal tiles. Each half has its own gradient, lit from above, darkest just above
// the hinge and lifting again below it, so the two flaps read as separate pieces.
private val CardFill = Brush.verticalGradient(
    0.0f to Color(0xFF34343A),
    0.5f to Color(0xFF26262B),
    0.5f to Color(0xFF1D1D21),
    1.0f to Color(0xFF29292E),
)

/** A faint highlight along the tile's top edge. */
private val CardEdge = Color.White.copy(alpha = 0.07f)

/** The old top half falling forward over the hinge. */
private const val FALL_MS = 300

/** The new bottom half swinging down into place. */
private const val LAND_MS = 220

/** Small rebound after the bottom flap lands. */
private const val BOUNCE_DEGREES = 14f
private const val BOUNCE_MS = 80

/** Darkest shading at the edge-on point of a flap. */
private const val MAX_SHADE = 0.45f

private val TopHalf = GenericShape { size, _ -> addRect(Rect(0f, 0f, size.width, size.height / 2)) }
private val BottomHalf = GenericShape { size, _ -> addRect(Rect(0f, size.height / 2, size.width, size.height)) }

/** Retro split-flap clock: hours and minutes on two cards that flip over the hinge when they change. */
@Composable
fun FlipFace(time: ClockTime, modifier: Modifier = Modifier) {
    val (hours, minutes) = time.text.split(':')
    val meridiem = if (time.is24Hour) null else if (time.hour < 12) "AM" else "PM"
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = time.text },
        contentAlignment = Alignment.Center,
    ) {
        val cardWidth = min(maxWidth * 0.38f, maxHeight * 0.62f * 1.1f)
        val cardHeight = cardWidth / 1.1f
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(cardWidth * 0.08f),
        ) {
            FlipCard(hours, cardWidth, cardHeight, cornerLabel = meridiem)
            Column(verticalArrangement = Arrangement.spacedBy(cardHeight * 0.18f)) {
                repeat(2) {
                    Box(Modifier.size(cardWidth * 0.05f).background(Amber, CircleShape))
                }
            }
            FlipCard(minutes, cardWidth, cardHeight)
        }
    }
}

/**
 * One split-flap card. Layers, back to front:
 * new top half, old bottom half, then whichever flap is moving
 * (old top falling toward the viewer, or new bottom landing), then the hinge line.
 * Flap angles are negated when drawn: in Compose, negative rotationX tips the top edge toward the viewer.
 */
@Composable
private fun FlipCard(value: String, width: Dp, height: Dp, cornerLabel: String? = null) {
    var current by remember { mutableStateOf(value) }
    var previous by remember { mutableStateOf(value) }
    val fall = remember { Animatable(0f) }
    val land = remember { Animatable(0f) }
    var flipping by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (value == current) return@LaunchedEffect
        // If a flip was interrupted, start this one from whatever is showing now.
        previous = current
        current = value
        fall.snapTo(0f)
        land.snapTo(-90f)
        flipping = true
        fall.animateTo(90f, tween(FALL_MS, easing = FastOutLinearInEasing))
        land.animateTo(0f, tween(LAND_MS, easing = FastOutLinearInEasing))
        land.animateTo(-BOUNCE_DEGREES, tween(BOUNCE_MS / 2, easing = LinearOutSlowInEasing))
        land.animateTo(0f, tween(BOUNCE_MS, easing = FastOutLinearInEasing))
        flipping = false
    }

    val falling = flipping && fall.value < 90f
    val landing = flipping && !falling
    // The falling flap casts a shadow on the bottom half until it passes edge-on.
    val bottomShadow = if (falling) fall.value / 90f * MAX_SHADE else 0f

    Box(Modifier.size(width, height)) {
        CardHalf(current, cornerLabel, width, height, TopHalf)
        CardHalf(if (flipping) previous else current, cornerLabel, width, height, BottomHalf, shade = bottomShadow)
        if (falling) {
            CardHalf(
                previous, cornerLabel, width, height, TopHalf,
                rotationX = -fall.value,
                shade = fall.value / 90f * MAX_SHADE,
            )
        }
        if (landing) {
            CardHalf(
                current, cornerLabel, width, height, BottomHalf,
                rotationX = -land.value,
                shade = -land.value / 90f * MAX_SHADE,
            )
        }
        // The hinge gap across the middle.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(3.dp)
                .background(Ink),
        )
    }
}

/** The full card face, clipped to one half and optionally rotated about the hinge. */
@Composable
private fun CardHalf(
    text: String,
    cornerLabel: String?,
    width: Dp,
    height: Dp,
    half: GenericShape,
    rotationX: Float = 0f,
    shade: Float = 0f,
) {
    val density = LocalDensity.current
    val fontSize = with(density) { (height * 0.72f).toSp() }
    val labelSize = with(density) { (height * 0.075f).toSp() }
    val corner = RoundedCornerShape(width * 0.08f)
    Box(
        Modifier
            .size(width, height)
            .graphicsLayer {
                clip = true
                shape = half
                this.rotationX = rotationX
                transformOrigin = TransformOrigin.Center
                cameraDistance = 14 * this.density
            }
            .background(CardFill, corner)
            .border(1.dp, Brush.verticalGradient(0f to CardEdge, 0.12f to Color.Transparent), corner),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.02).em,
                color = Paper,
                textAlign = TextAlign.Center,
            ),
        )
        if (cornerLabel != null) {
            Text(
                text = cornerLabel,
                style = TextStyle(fontSize = labelSize, fontWeight = FontWeight.SemiBold, color = Muted),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = width * 0.07f, bottom = height * 0.07f),
            )
        }
        if (shade > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = shade), corner))
        }
    }
}

@Preview(widthDp = 844, heightDp = 390, showBackground = true, backgroundColor = 0xFF070708)
@Composable
private fun FlipFacePreview() {
    HorizOnTheme { FlipFace(ClockTime(10, 42, is24Hour = false)) }
}
