package com.example.smileproc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.smileproc.core.Mood

@Composable
fun FaceButton(
    mood: Mood,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = MaterialTheme.colorScheme.onSurface
        Canvas(Modifier.size(88.dp)) {
            val stroke = Stroke(width = size.minDimension * 0.06f, cap = StrokeCap.Round)
            drawCircle(color = color, style = stroke)
            val eyeY = size.height * 0.40f
            drawCircle(color, radius = size.minDimension * 0.045f, center = Offset(size.width * 0.36f, eyeY))
            drawCircle(color, radius = size.minDimension * 0.045f, center = Offset(size.width * 0.64f, eyeY))
            val mouth = Path()
            val left = size.width * 0.32f
            val right = size.width * 0.68f
            val y = size.height * 0.64f
            when (mood) {
                Mood.GOOD -> {
                    mouth.moveTo(left, y - size.height * 0.04f)
                    mouth.quadraticTo(size.width / 2f, y + size.height * 0.12f, right, y - size.height * 0.04f)
                }
                Mood.OKAY -> {
                    mouth.moveTo(left, y)
                    mouth.lineTo(right, y)
                }
                Mood.LOW -> {
                    mouth.moveTo(left, y + size.height * 0.06f)
                    mouth.quadraticTo(size.width / 2f, y - size.height * 0.08f, right, y + size.height * 0.06f)
                }
            }
            drawPath(mouth, color, style = stroke)
        }
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}
