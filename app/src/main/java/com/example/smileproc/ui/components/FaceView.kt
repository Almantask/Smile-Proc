package com.example.smileproc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.Mood

/**
 * Three accessible custom-drawn faces:
 * - Low: gentle downturned mouth
 * - Okay: gentle horizontal neutral line
 * - Good: gentle upturned smile
 *
 * Drawn with high-contrast colors adapted to light/dark themes.
 */
@Composable
fun FaceIcon(
    mood: Mood,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    val faceColor = when (mood) {
        Mood.LOW -> MaterialTheme.colorScheme.secondaryContainer
        Mood.OKAY -> MaterialTheme.colorScheme.tertiaryContainer
        Mood.GOOD -> MaterialTheme.colorScheme.primaryContainer
    }
    val featureColor = when (mood) {
        Mood.LOW -> MaterialTheme.colorScheme.onSecondaryContainer
        Mood.OKAY -> MaterialTheme.colorScheme.onTertiaryContainer
        Mood.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val outlineColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // Face circle background
        drawCircle(
            color = faceColor,
            radius = radius - 2f,
            center = center,
        )

        // Face outline
        drawCircle(
            color = outlineColor,
            radius = radius - 2f,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )

        // Eyes: two friendly dots
        val eyeRadius = radius * 0.09f
        val eyeOffsetX = radius * 0.35f
        val eyeOffsetY = radius * 0.22f

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

        // Mouth based on mood
        val mouthWidth = radius * 0.7f
        val mouthCenterY = center.y + radius * 0.32f
        val mouthStrokeWidth = 3.dp.toPx()

        when (mood) {
            Mood.LOW -> {
                // Gentle downward arc
                val path = Path().apply {
                    arcTo(
                        rect = Rect(
                            left = center.x - mouthWidth / 2f,
                            top = mouthCenterY - radius * 0.15f,
                            right = center.x + mouthWidth / 2f,
                            bottom = mouthCenterY + radius * 0.25f,
                        ),
                        startAngleDegrees = 200f,
                        sweepAngleDegrees = 140f,
                        forceMoveTo = true,
                    )
                }
                drawPath(
                    path = path,
                    color = featureColor,
                    style = Stroke(width = mouthStrokeWidth, cap = StrokeCap.Round),
                )
            }
            Mood.OKAY -> {
                // Straight gentle line
                drawLine(
                    color = featureColor,
                    start = Offset(center.x - mouthWidth / 2f, mouthCenterY),
                    end = Offset(center.x + mouthWidth / 2f, mouthCenterY),
                    strokeWidth = mouthStrokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            Mood.GOOD -> {
                // Gentle upward smile arc
                val path = Path().apply {
                    arcTo(
                        rect = Rect(
                            left = center.x - mouthWidth / 2f,
                            top = mouthCenterY - radius * 0.25f,
                            right = center.x + mouthWidth / 2f,
                            bottom = mouthCenterY + radius * 0.15f,
                        ),
                        startAngleDegrees = 20f,
                        sweepAngleDegrees = 140f,
                        forceMoveTo = true,
                    )
                }
                drawPath(
                    path = path,
                    color = featureColor,
                    style = Stroke(width = mouthStrokeWidth, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
fun FaceButton(
    mood: Mood,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    showLabel: Boolean = true,
    selected: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val label = when (mood) {
        Mood.LOW -> stringResource(R.string.face_low)
        Mood.OKAY -> stringResource(R.string.face_okay)
        Mood.GOOD -> stringResource(R.string.face_good)
    }

    val selectedBorder = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else null

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
        border = selectedBorder,
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            }
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            FaceIcon(mood = mood, size = size)
            if (showLabel) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
