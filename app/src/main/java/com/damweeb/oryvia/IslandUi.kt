package com.damweeb.oryvia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

/** Invisible margin around the island that also counts as touch area. */
const val TOUCH_SLOP_DP = 16
@Composable
fun IslandRoot(
    ui: UiState,
    s: IslandSettings,
    onIdleTap: () -> Unit,
    onIdleLongPress: () -> Unit,
    onExpandedTap: () -> Unit,
    onExpandedLongPress: () -> Unit,
    onMediaAction: (MediaAction) -> Unit,
    onDismiss: () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        IslandBody(
                ui, s, onIdleTap, onIdleLongPress, onExpandedTap, onExpandedLongPress, onMediaAction, onDismiss
        )
    }
}

@Composable
private fun IslandBody(
    ui: UiState,
    s: IslandSettings,
    onIdleTap: () -> Unit,
    onIdleLongPress: () -> Unit,
    onExpandedTap: () -> Unit,
    onExpandedLongPress: () -> Unit,
    onMediaAction: (MediaAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val punch = s.mode == IslandMode.PUNCH_HOLE
    val content = ui.content
    val suppressed = ui.landscape && s.hideInLandscape
    val expanded = ui.expanded && content != null && !suppressed

    // Each kind of content gets its own height, width comes from settings.
    val contentHeight = when (content) {
        is IslandContent.Media -> s.expandedHeightDp + 44
        is IslandContent.Charging -> minOf(s.expandedHeightDp, 56)
        else -> s.expandedHeightDp
    }
    val expandedHeight = if (punch) maxOf(contentHeight, s.punchSizeDp) else contentHeight
    val expandedWidth = if (punch) maxOf(s.expandedWidthDp, s.punchSizeDp + 140) else s.expandedWidthDp

    // width, height, corner radius (all in dp)
    val target = when {
        suppressed -> Triple(0, 0, 0)
        expanded -> Triple(expandedWidth, expandedHeight, s.cornerRadiusDp)
        !s.idleVisible -> Triple(0, 0, 0)
        punch -> Triple(s.punchSizeDp, s.punchSizeDp, s.punchSizeDp / 2)
        else -> Triple(s.idleWidthDp, s.idleHeightDp, s.idleHeightDp / 2)
    }

    val speed = s.animationSpeed.coerceIn(0.3f, 3f)
    val dpSpec = spring<Dp>(dampingRatio = 0.72f, stiffness = 380f * speed)
    val width by animateDpAsState(target.first.dp, dpSpec, label = "width")
    val height by animateDpAsState(target.second.dp, dpSpec, label = "height")
    val radius by animateDpAsState(target.third.dp, dpSpec, label = "radius")
    val background by animateColorAsState(
        targetValue = if (expanded) {
            Color(s.backgroundColor)
        } else {
            Color(s.idleColor).copy(alpha = s.idleOpacity)
        },
        animationSpec = tween((350 / speed).toInt()),
        label = "background",
    )

    val r = radius.coerceAtLeast(0.dp)
    val shape = if (s.mode == IslandMode.NOTCH) {
        // flat top, rounded bottom: it hangs from the top edge like a notch
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = r, bottomStart = r)
    } else {
        RoundedCornerShape(r)
    }

    val textColor = Color(s.textColor)
    val accent = Color(s.accentColor)

    // No margin while the island is fully hidden, so an empty window can't catch touches.
    val slop = if (target.first > 0) TOUCH_SLOP_DP.dp else 0.dp
    // Where the expanded island sits relative to its idle spot.
    val shiftX = if (punch) s.expandedOffsetXDp.coerceAtLeast(0) else s.expandedOffsetXDp
    val shiftY = if (s.mode == IslandMode.NOTCH) 0 else s.expandedOffsetYDp.coerceAtLeast(0)
    val offsetX by animateDpAsState((if (expanded) shiftX else 0).dp, dpSpec, label = "offsetX")
    val offsetY by animateDpAsState((if (expanded) shiftY else 0).dp, dpSpec, label = "offsetY")

    // Notch and pill windows are centered, so moving the island by dx takes 2*dx of
    // padding on the opposite side. The punch hole window is pinned left, so it needs dx.
    // coerceAtLeast stops the spring's overshoot from producing a negative padding (that crashes).
    val padStart = if (punch) offsetX.coerceAtLeast(0.dp) else (offsetX * 2).coerceAtLeast(0.dp)
    val padEnd = if (punch) 0.dp else (-offsetX * 2).coerceAtLeast(0.dp)

    // Swipe to dismiss: follows the finger, hides the island once dragged far enough.
    val density = LocalDensity.current
    val dismissDistance = with(density) { 48.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val dragSpec = if (dragging) snap<Float>() else spring<Float>(stiffness = Spring.StiffnessMedium)
    val shownX by animateFloatAsState(if (dragging) dragX else 0f, dragSpec, label = "dragX")
    val shownY by animateFloatAsState(if (dragging) dragY else 0f, dragSpec, label = "dragY")
    val swipeProgress = (maxOf(abs(shownX), -shownY, 0f) / dismissDistance).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .padding(start = padStart, top = offsetY.coerceAtLeast(0.dp), end = padEnd)
            .pointerInput(expanded) {
                detectTapGestures(
                    onTap = { if (expanded) onExpandedTap() else onIdleTap() },
                    onLongPress = { if (expanded) onExpandedLongPress() else onIdleLongPress() },
                )
            }
            .pointerInput(expanded) {
                if (!expanded) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                    },
                    onDragEnd = {
                        val far = maxOf(abs(dragX), -dragY) >= dismissDistance
                        dragging = false
                        if (far) onDismiss()
                    },
                    onDragCancel = { dragging = false },
                )
            }
            .padding(slop),
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val scale = 1f - 0.12f * swipeProgress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.5f * swipeProgress
                    translationY = minOf(shownY, 0f) * 0.5f
                }
                .size(width.coerceAtLeast(0.dp), height.coerceAtLeast(0.dp))
                .clip(shape)
                .background(background),
        ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200, delayMillis = 100)),
            exit = fadeOut(tween(80)),
        ) {
            // The content is laid out at its full size and simply revealed by the growing
            // shape. Centered for notch/pill, pinned left (past the camera) for punch hole.
            Box(
                modifier = Modifier
                    .wrapContentSize(
                        align = if (punch) Alignment.TopStart else Alignment.TopCenter,
                        unbounded = true,
                    )
                    .size(expandedWidth.dp, expandedHeight.dp)
                    .padding(
                        start = if (punch) s.punchSizeDp.dp else 0.dp,
                        top = if (s.mode == IslandMode.NOTCH) 4.dp else 0.dp,
                    ),
            ) {
                when (content) {
                    is IslandContent.NotificationItem -> NotificationBody(
                        content,
                        textColor,
                        accent
                    )

                    is IslandContent.Media -> MediaBody(content, textColor, accent, onMediaAction)
                    is IslandContent.Charging -> ChargingBody(content, textColor, accent)
                    null -> Unit
                }
            }
        }
        }
    }
}

// ---------------------------------------------------------------- content

@Composable
private fun NotificationBody(c: IslandContent.NotificationItem, text: Color, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = c.icon
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = if (c.tintIcon) ContentScale.Fit else ContentScale.Crop,
                colorFilter = if (c.tintIcon) ColorFilter.tint(accent) else null,
            )
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.25f))
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = c.appName,
                color = text.copy(alpha = 0.6f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = c.title,
                color = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (c.text.isNotBlank()) {
                Text(
                    text = c.text,
                    color = text.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    }


@Composable
private fun MediaBody(
    c: IslandContent.Media,
    text: Color,
    accent: Color,
    onAction: (MediaAction) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val art = c.art
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.25f))
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = c.title,
                    color = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (c.artist.isNotBlank()) {
                    Text(
                        text = c.artist,
                        color = text.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(Glyph.PREVIOUS, text) { onAction(MediaAction.PREVIOUS) }
            ControlButton(if (c.playing) Glyph.PAUSE else Glyph.PLAY, accent) {
                onAction(MediaAction.TOGGLE)
            }
            ControlButton(Glyph.NEXT, text) { onAction(MediaAction.NEXT) }
        }
    }
}

@Composable
private fun ChargingBody(c: IslandContent.Charging, text: Color, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = c.label,
            color = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(14.dp))
        LinearProgressIndicator(
            progress = { c.percent / 100f },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape),
            color = if (c.charging) accent else text,
            trackColor = text.copy(alpha = 0.2f),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = "${c.percent}%",
            color = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------- media buttons

private enum class Glyph { PREVIOUS, PLAY, PAUSE, NEXT }

@Composable
private fun ControlButton(glyph: Glyph, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, color, Modifier.size(22.dp))
    }
}

/** Tiny hand-drawn icons so the app does not need the big material-icons-extended library. */
@Composable
private fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (glyph) {
            Glyph.PLAY -> drawPath(
                Path().apply {
                    moveTo(w * 0.22f, h * 0.12f)
                    lineTo(w * 0.88f, h * 0.5f)
                    lineTo(w * 0.22f, h * 0.88f)
                    close()
                },
                color,
            )
            Glyph.PAUSE -> {
                drawRect(color, Offset(w * 0.2f, h * 0.12f), Size(w * 0.22f, h * 0.76f))
                drawRect(color, Offset(w * 0.58f, h * 0.12f), Size(w * 0.22f, h * 0.76f))
            }
            Glyph.NEXT -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.1f, h * 0.15f)
                        lineTo(w * 0.7f, h * 0.5f)
                        lineTo(w * 0.1f, h * 0.85f)
                        close()
                    },
                    color,
                )
                drawRect(color, Offset(w * 0.74f, h * 0.15f), Size(w * 0.16f, h * 0.7f))
            }
            Glyph.PREVIOUS -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.9f, h * 0.15f)
                        lineTo(w * 0.3f, h * 0.5f)
                        lineTo(w * 0.9f, h * 0.85f)
                        close()
                    },
                    color,
                )
                drawRect(color, Offset(w * 0.1f, h * 0.15f), Size(w * 0.16f, h * 0.7f))
            }
        }
    }
}
