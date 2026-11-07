package com.example.smileproc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.Mood

/**
 * Custom-drawn accessible face representation for Moods:
 * 1 = Low, 2 = Okay, 3 = Good. Every face is an equally valid answer.
 */
@Composable
fun FaceIcon(
    mood: Mood,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    faceColor: Color = MaterialTheme.colorScheme.primaryContainer,
    featureColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // Face circle
        drawCircle(
            color = faceColor,
            radius = radius - 2f,
            center = center,
        )
        drawCircle(
            color = featureColor.copy(alpha = 0.35f),
            radius = radius - 2f,
            center = center,
            style = Stroke(width = radius * 0.06f),
        )

        // Eyes
        val eyeRadius = radius * 0.10f
        val eyeOffsetY = center.y - radius * 0.22f
        val eyeOffsetX = radius * 0.35f
        drawCircle(
            color = featureColor,
            radius = eyeRadius,
            center = Offset(center.x - eyeOffsetX, eyeOffsetY),
        )
        drawCircle(
            color = featureColor,
            radius = eyeRadius,
            center = Offset(center.x + eyeOffsetX, eyeOffsetY),
        )

        // Mouth
        val mouthStrokeWidth = radius * 0.08f
        val mouthWidth = radius * 0.70f
        val mouthCenterY = center.y + radius * 0.30f

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
) {
    val haptic = LocalHapticFeedback.current
    val label = when (mood) {
        Mood.LOW -> stringResource(R.string.face_low)
        Mood.OKAY -> stringResource(R.string.face_okay)
        Mood.GOOD -> stringResource(R.string.face_good)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = label
            }
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = size),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .padding(8.dp),
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
