package com.example.smileproc.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.Mood
import com.example.smileproc.ui.theme.LocalSmileProcColors
import com.example.smileproc.ui.theme.moodAccent
import com.example.smileproc.ui.theme.moodGlow

/**
 * Three accessible custom-drawn faces combining soft sphere depth, subtle top specular rim,
 * gentle rosy cheeks on medium/large sizes, and warm expressive facial features.
 */
@Composable
fun FaceIcon(
    mood: Mood,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSmileProcColors.current
    val faceColor = colors.moodAccent(mood)
    val featureColor = when (mood) {
        Mood.LOW -> colors.moodLowFeature
        Mood.OKAY -> colors.moodOkayFeature
        Mood.GOOD -> colors.moodGoodFeature
    }
    val topHighlightColor = remember(faceColor) { lerp(faceColor, Color.White, 0.24f) }
    val deepRimColor = remember(faceColor) { lerp(faceColor, Color.Black, 0.14f) }

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(topHighlightColor, faceColor, deepRimColor),
                center = Offset(center.x - radius * 0.18f, center.y - radius * 0.30f),
                radius = radius * 1.35f,
            ),
            radius = radius,
            center = center,
        )

        // Soft inner top specular rim
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.42f), Color.Transparent),
            ),
            radius = (radius - 1.dp.toPx()).coerceAtLeast(1f),
            center = center,
            style = Stroke(width = 1.5.dp.toPx()),
        )

        drawWarmFaceFeatures(
            mood = mood,
            center = center,
            radius = radius,
            featureColor = featureColor,
            blushColor = Color.White.copy(alpha = 0.24f),
        )
    }
}

private fun DrawScope.drawWarmFaceFeatures(
    mood: Mood,
    center: Offset,
    radius: Float,
    featureColor: Color,
    blushColor: Color,
) {
    val eyeRadius = (radius * 0.095f).coerceAtLeast(1.5f)
    val eyeOffsetX = radius * 0.33f
    val eyeOffsetY = radius * 0.17f
    val strokeWidth = (radius * 0.105f).coerceAtLeast(1.8.dp.toPx())

    if (radius > 16.dp.toPx()) {
        drawOval(
            color = blushColor,
            topLeft = Offset(center.x - radius * 0.62f, center.y + radius * 0.02f),
            size = Size(radius * 0.26f, radius * 0.15f),
        )
        drawOval(
            color = blushColor,
            topLeft = Offset(center.x + radius * 0.36f, center.y + radius * 0.02f),
            size = Size(radius * 0.26f, radius * 0.15f),
        )
    }

    if (mood == Mood.GOOD) {
        val archW = radius * 0.25f
        val archH = radius * 0.18f
        listOf(-eyeOffsetX, eyeOffsetX).forEach { dx ->
            val eyePath = Path().apply {
                arcTo(
                    rect = Rect(
                        left = center.x + dx - archW / 2f,
                        top = center.y - eyeOffsetY - archH / 2f,
                        right = center.x + dx + archW / 2f,
                        bottom = center.y - eyeOffsetY + archH / 2f,
                    ),
                    startAngleDegrees = 200f,
                    sweepAngleDegrees = 140f,
                    forceMoveTo = true,
                )
            }
            drawPath(
                path = eyePath,
                color = featureColor,
                style = Stroke(width = strokeWidth * 0.92f, cap = StrokeCap.Round),
            )
        }
    } else {
        drawCircle(
            color = featureColor,
            radius = eyeRadius,
            center = Offset(center.x - eyeOffsetX, center.y - eyeOffsetY),
        )
        drawCircle(
            color = featureColor,
            radius = eyeRadius,
            center = Offset(center.x + eyeOffsetX, center.y - eyeOffsetY),
        )
        if (radius > 14.dp.toPx()) {
            val gleamR = eyeRadius * 0.34f
            drawCircle(
                color = Color.White.copy(alpha = 0.80f),
                radius = gleamR,
                center = Offset(center.x - eyeOffsetX - eyeRadius * 0.25f, center.y - eyeOffsetY - eyeRadius * 0.28f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.80f),
                radius = gleamR,
                center = Offset(center.x + eyeOffsetX - eyeRadius * 0.25f, center.y - eyeOffsetY - eyeRadius * 0.28f),
            )
        }
    }

    val mouthWidth = radius * 0.72f
    val mouthCenterY = center.y + radius * 0.25f
    when (mood) {
        Mood.LOW -> {
            if (radius > 14.dp.toPx()) {
                drawLine(
                    color = featureColor,
                    start = Offset(center.x - eyeOffsetX * 1.32f, center.y - eyeOffsetY * 1.65f),
                    end = Offset(center.x - eyeOffsetX * 0.58f, center.y - eyeOffsetY * 2.1f),
                    strokeWidth = strokeWidth * 0.72f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = featureColor,
                    start = Offset(center.x + eyeOffsetX * 0.58f, center.y - eyeOffsetY * 2.1f),
                    end = Offset(center.x + eyeOffsetX * 1.32f, center.y - eyeOffsetY * 1.65f),
                    strokeWidth = strokeWidth * 0.72f,
                    cap = StrokeCap.Round,
                )
            }
            val path = Path().apply {
                arcTo(
                    rect = Rect(
                        left = center.x - mouthWidth * 0.44f,
                        top = mouthCenterY - radius * 0.04f,
                        right = center.x + mouthWidth * 0.44f,
                        bottom = mouthCenterY + radius * 0.32f,
                    ),
                    startAngleDegrees = 205f,
                    sweepAngleDegrees = 130f,
                    forceMoveTo = true,
                )
            }
            drawPath(
                path = path,
                color = featureColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Mood.OKAY -> {
            val path = Path().apply {
                moveTo(center.x - mouthWidth * 0.44f, mouthCenterY + radius * 0.02f)
                quadraticTo(
                    center.x,
                    mouthCenterY + radius * 0.11f,
                    center.x + mouthWidth * 0.44f,
                    mouthCenterY + radius * 0.02f,
                )
            }
            drawPath(
                path = path,
                color = featureColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Mood.GOOD -> {
            val path = Path().apply {
                arcTo(
                    rect = Rect(
                        left = center.x - mouthWidth * 0.52f,
                        top = mouthCenterY - radius * 0.34f,
                        right = center.x + mouthWidth * 0.52f,
                        bottom = mouthCenterY + radius * 0.22f,
                    ),
                    startAngleDegrees = 18f,
                    sweepAngleDegrees = 144f,
                    forceMoveTo = true,
                )
            }
            drawPath(
                path = path,
                color = featureColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun FaceButton(
    mood: Mood,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    showLabel: Boolean = true,
    selected: Boolean = false,
    isLatestHighlight: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val colors = LocalSmileProcColors.current
    val reducedMotion = rememberReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val hasHalo = selected || isLatestHighlight
    val scale by animateFloatAsState(
        targetValue = if (!reducedMotion && isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "faceScale",
    )

    val pulseTransition = rememberInfiniteTransition(label = "faceAura")
    val rawPulse by pulseTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "facePulse",
    )
    val haloScale = if (!reducedMotion && hasHalo) rawPulse else 1f

    val label = when (mood) {
        Mood.LOW -> stringResource(R.string.face_low)
        Mood.OKAY -> stringResource(R.string.face_okay)
        Mood.GOOD -> stringResource(R.string.face_good)
    }
    val glowColor = colors.moodGlow(mood)
    val faceAccent = colors.moodAccent(mood)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            }
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = size * 0.64f),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size + 24.dp)
                .drawBehind {
                    val faceRadius = size.toPx() * 0.5f
                    val outerGlowR = faceRadius * (if (hasHalo) 1.56f else 1.42f) * haloScale
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to glowColor,
                            0.55f to glowColor.copy(alpha = glowColor.alpha * 0.42f),
                            1f to Color.Transparent,
                            center = center,
                            radius = outerGlowR,
                        ),
                        radius = outerGlowR,
                        center = center,
                    )
                    if (hasHalo) {
                        val innerRingR = faceRadius * 1.13f * ((haloScale + 1f) * 0.5f)
                        val outerRingR = faceRadius * 1.25f * haloScale
                        drawCircle(
                            color = faceAccent.copy(alpha = if (selected) 0.62f else 0.44f),
                            radius = innerRingR,
                            center = center,
                            style = Stroke(width = if (selected) 2.dp.toPx() else 1.6.dp.toPx()),
                        )
                        drawCircle(
                            color = faceAccent.copy(alpha = if (selected) 0.28f else 0.20f),
                            radius = outerRingR,
                            center = center,
                            style = Stroke(width = 1.2.dp.toPx()),
                        )
                    }
                },
        ) {
            FaceIcon(mood = mood, size = size)
        }
        if (showLabel) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (hasHalo) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (hasHalo) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
