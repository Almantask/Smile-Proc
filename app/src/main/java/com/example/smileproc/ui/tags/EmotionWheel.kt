package com.example.smileproc.ui.tags

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.smileproc.R
import com.example.smileproc.core.EmotionTags
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometric and hit-testing helpers for [EmotionWheel].
 */
object EmotionWheelMath {
    const val START_ANGLE_DEG = -90f // 12 o'clock
    const val HOLE_RATIO = 0.22f
    const val INNER_RATIO = 0.56f

    /**
     * Resolves the tag ID tapped at (tapX, tapY) relative to wheel center (cx, cy).
     * Returns null if tapped in the center hole or outside the wheel radius.
     */
    fun findTappedTag(
        tapX: Float,
        tapY: Float,
        centerX: Float,
        centerY: Float,
        radius: Float,
        catalog: EmotionTags,
    ): String? {
        val dx = tapX - centerX
        val dy = tapY - centerY
        val dist = sqrt(dx * dx + dy * dy)
        val holeRadius = radius * HOLE_RATIO
        val innerRadius = radius * INNER_RATIO

        if (dist < holeRadius || dist > radius) return null

        val angleRad = atan2(dy.toDouble(), dx.toDouble())
        var angleDeg = Math.toDegrees(angleRad).toFloat()
        var normalizedAngle = (angleDeg - START_ANGLE_DEG) % 360f
        if (normalizedAngle < 0f) normalizedAngle += 360f

        val coreCount = catalog.cores.size
        if (coreCount == 0) return null
        val coreSweep = 360f / coreCount

        val coreIndex = (normalizedAngle / coreSweep).toInt().coerceIn(0, coreCount - 1)
        val core = catalog.cores[coreIndex]

        return if (dist < innerRadius) {
            core.id
        } else {
            val fineCount = core.fine.size
            if (fineCount == 0) return core.id
            val fineSweep = coreSweep / fineCount
            val angleInCore = normalizedAngle - (coreIndex * coreSweep)
            val fineIndex = (angleInCore / fineSweep).toInt().coerceIn(0, fineCount - 1)
            core.fine[fineIndex]
        }
    }
}

/**
 * Interactive Emotion Wheel composable per `docs/spec/screens.md`.
 *
 * Inner ring: 7 core emotions.
 * Outer ring: 35 fine tags (5 sub-wedges per core).
 * Diameter: min(width - 32.dp, 360.dp).
 * Tap selects/deselects; chips echo the selection.
 */
@Composable
fun EmotionWheel(
    selectedTags: List<String>,
    onTagToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
    catalog: EmotionTags = EmotionTagsCatalog.get(LocalContext.current),
) {
    val context = LocalContext.current
    val wheelCd = stringResource(R.string.tags_wheel_cd)
    val density = LocalDensity.current

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val strokeColor = surfaceColor.copy(alpha = 0.9f)
    val highlightStrokeColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val diameter: Dp = min(maxWidth - 32.dp, 360.dp)
        if (diameter <= 0.dp) return@BoxWithConstraints

        val diameterPx = with(density) { diameter.toPx() }
        val radiusPx = diameterPx / 2f
        val holeRadiusPx = radiusPx * EmotionWheelMath.HOLE_RATIO
        val innerRadiusPx = radiusPx * EmotionWheelMath.INNER_RATIO

        val textPaint = remember(density, onSurfaceColor) {
            Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
        }

        Canvas(
            modifier = Modifier
                .size(diameter)
                .semantics {
                    contentDescription = wheelCd
                    role = Role.Image
                }
                .pointerInput(catalog, selectedTags) {
                    detectTapGestures { offset ->
                        val tapped = EmotionWheelMath.findTappedTag(
                            tapX = offset.x,
                            tapY = offset.y,
                            centerX = size.width / 2f,
                            centerY = size.height / 2f,
                            radius = size.width / 2f,
                            catalog = catalog,
                        )
                        if (tapped != null) {
                            onTagToggled(tapped)
                        }
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val coreCount = catalog.cores.size
            if (coreCount == 0) return@Canvas
            val coreSweep = 360f / coreCount

            val dividerWidthPx = with(density) { 1.5.dp.toPx() }
            val highlightWidthPx = with(density) { 3.dp.toPx() }

            // 1. Draw Inner Ring (Cores)
            catalog.cores.forEachIndexed { i, core ->
                val startAngle = EmotionWheelMath.START_ANGLE_DEG + (i * coreSweep)
                val baseColor = parseCoreColor(core.color)
                val isSelected = core.id in selectedTags

                val wedgePath = Path().apply {
                    arcWedge(
                        center = center,
                        innerRadius = holeRadiusPx,
                        outerRadius = innerRadiusPx,
                        startAngleDegrees = startAngle,
                        sweepAngleDegrees = coreSweep,
                    )
                }

                // Fill
                drawPath(wedgePath, color = baseColor)

                // Divider stroke
                drawPath(
                    wedgePath,
                    color = strokeColor,
                    style = Stroke(width = dividerWidthPx),
                )

                // Selected highlight
                if (isSelected) {
                    drawPath(
                        wedgePath,
                        color = highlightStrokeColor,
                        style = Stroke(width = highlightWidthPx),
                    )
                }

                // Core label text
                val midAngle = startAngle + (coreSweep / 2f)
                val midRadius = (holeRadiusPx + innerRadiusPx) / 2f
                val label = context.resolveTagLabel(core.id)

                drawContext.canvas.nativeCanvas.apply {
                    save()
                    val normMid = (midAngle % 360f + 360f) % 360f
                    val isBottomHalf = normMid in 0f..180f
                    val rotation = if (isBottomHalf) normMid - 90f else normMid + 90f
                    rotate(rotation, center.x, center.y)
                    val textY = if (isBottomHalf) {
                        center.y + midRadius + with(density) { 3.5.sp.toPx() }
                    } else {
                        center.y - midRadius + with(density) { 3.5.sp.toPx() }
                    }
                    textPaint.textSize = with(density) {
                        if (diameterPx < 600f) 10.sp.toPx() else 11.5.sp.toPx()
                    }
                    textPaint.color = Color(0xFF1C1B1F).toArgb()
                    drawText(label, center.x, textY, textPaint)
                    if (isSelected) {
                        val dotY = if (isBottomHalf) textY - with(density) { 12.dp.toPx() } else textY + with(density) { 7.dp.toPx() }
                        drawCircle(center.x, dotY, with(density) { 3.dp.toPx() }, Paint().apply {
                            color = highlightStrokeColor.toArgb()
                            isAntiAlias = true
                        })
                    }
                    restore()
                }

                // 2. Draw Outer Ring (Fine tags for this core)
                val fineCount = core.fine.size
                if (fineCount > 0) {
                    val fineSweep = coreSweep / fineCount
                    core.fine.forEachIndexed { j, fineId ->
                        val fineStartAngle = startAngle + (j * fineSweep)
                        val isFineSelected = fineId in selectedTags
                        // Subtle alternating lightness factor for fine wedges
                        val tint = if (j % 2 == 0) 1.0f else 0.94f
                        val fineColor = Color(
                            red = (baseColor.red * tint).coerceIn(0f, 1f),
                            green = (baseColor.green * tint).coerceIn(0f, 1f),
                            blue = (baseColor.blue * tint).coerceIn(0f, 1f),
                            alpha = 1f,
                        )

                        val fineWedgePath = Path().apply {
                            arcWedge(
                                center = center,
                                innerRadius = innerRadiusPx,
                                outerRadius = radiusPx,
                                startAngleDegrees = fineStartAngle,
                                sweepAngleDegrees = fineSweep,
                            )
                        }

                        // Fill
                        drawPath(fineWedgePath, color = fineColor)

                        // Divider stroke
                        drawPath(
                            fineWedgePath,
                            color = strokeColor,
                            style = Stroke(width = dividerWidthPx),
                        )

                        // Selected highlight
                        if (isFineSelected) {
                            drawPath(
                                fineWedgePath,
                                color = highlightStrokeColor,
                                style = Stroke(width = highlightWidthPx),
                            )
                        }

                        // Fine label text (drawn radially along wedge ray)
                        val fineMidAngle = fineStartAngle + (fineSweep / 2f)
                        val fineMidRadius = (innerRadiusPx + radiusPx) / 2f
                        val fineLabel = context.resolveTagLabel(fineId)

                        drawContext.canvas.nativeCanvas.apply {
                            save()
                            val normFine = (fineMidAngle % 360f + 360f) % 360f
                            val isLeftSide = normFine in 90f..270f
                            val rotation = if (isLeftSide) normFine + 180f else normFine
                            rotate(rotation, center.x, center.y)

                            val textX = if (isLeftSide) center.x - fineMidRadius else center.x + fineMidRadius
                            val textY = center.y + with(density) { 3.sp.toPx() }

                            textPaint.textSize = with(density) {
                                if (diameterPx < 600f) 8.sp.toPx() else 9.sp.toPx()
                            }
                            textPaint.color = Color(0xFF1C1B1F).toArgb()
                            drawText(fineLabel, textX, textY, textPaint)
                            if (isFineSelected) {
                                val dotX = if (isLeftSide) textX - with(density) { 10.dp.toPx() } else textX + with(density) { 10.dp.toPx() }
                                drawCircle(dotX, center.y, with(density) { 2.5.dp.toPx() }, Paint().apply {
                                    color = highlightStrokeColor.toArgb()
                                    isAntiAlias = true
                                })
                            }
                            restore()
                        }
                    }
                }
            }

            // 3. Center Hole
            drawCircle(
                color = surfaceColor,
                radius = holeRadiusPx,
                center = center,
            )
            drawCircle(
                color = strokeColor,
                radius = holeRadiusPx,
                center = center,
                style = Stroke(width = dividerWidthPx),
            )
        }
    }
}

private fun Path.arcWedge(
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    startAngleDegrees: Float,
    sweepAngleDegrees: Float,
) {
    reset()
    val startRad = Math.toRadians(startAngleDegrees.toDouble())
    val endRad = Math.toRadians((startAngleDegrees + sweepAngleDegrees).toDouble())

    // Outer arc
    arcTo(
        rect = Rect(
            center.x - outerRadius,
            center.y - outerRadius,
            center.x + outerRadius,
            center.y + outerRadius,
        ),
        startAngleDegrees = startAngleDegrees,
        sweepAngleDegrees = sweepAngleDegrees,
        forceMoveTo = true,
    )
    // Line to inner arc end
    lineTo(
        (center.x + innerRadius * cos(endRad)).toFloat(),
        (center.y + innerRadius * sin(endRad)).toFloat(),
    )
    // Inner arc in reverse
    arcTo(
        rect = Rect(
            center.x - innerRadius,
            center.y - innerRadius,
            center.x + innerRadius,
            center.y + innerRadius,
        ),
        startAngleDegrees = startAngleDegrees + sweepAngleDegrees,
        sweepAngleDegrees = -sweepAngleDegrees,
        forceMoveTo = false,
    )
    close()
}
