package com.example.smileproc.ui.tags

import com.example.smileproc.ui.theme.LocalSmileProcColors

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.smileproc.R
import com.example.smileproc.core.CoreEmotionTag
import com.example.smileproc.core.EmotionTags
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometric and hit-testing helpers for the 3-ring [EmotionWheel].
 *
 * Ring 1 (inner): `HOLE_RATIO .. INNER_RATIO` -> Core emotions
 * Ring 2 (middle): `INNER_RATIO .. MIDDLE_RATIO` -> Middle emotions (`core.groups[g].id`)
 * Ring 3 (outer): `MIDDLE_RATIO .. 1.0f` -> Outer emotions (`core.groups[g].outer[o]`)
 */
object EmotionWheelMath {
    const val START_ANGLE_DEG = -90f // 12 o'clock
    const val HOLE_RATIO = 0.16f
    const val INNER_RATIO = 0.40f
    const val MIDDLE_RATIO = 0.68f

    fun isCenterHoleTap(
        tapX: Float,
        tapY: Float,
        centerX: Float,
        centerY: Float,
        radius: Float,
    ): Boolean {
        val dx = tapX - centerX
        val dy = tapY - centerY
        val dist = sqrt(dx * dx + dy * dy)
        return dist < radius * HOLE_RATIO
    }

    /**
     * Resolves the tag ID tapped at (tapX, tapY) relative to wheel center (centerX, centerY).
     * Returns null if tapped in the center hole or outside the wheel radius.
     */
    fun findTappedTag(
        tapX: Float,
        tapY: Float,
        centerX: Float,
        centerY: Float,
        radius: Float,
        catalog: EmotionTags,
        focusedCoreId: String? = null,
        coreOnly: Boolean = false,
    ): String? {
        val dx = tapX - centerX
        val dy = tapY - centerY
        val dist = sqrt(dx * dx + dy * dy)
        val holeRadius = radius * HOLE_RATIO
        val innerRadius = radius * INNER_RATIO
        val middleRadius = radius * MIDDLE_RATIO

        if (dist < holeRadius || dist > radius) return null

        val angleRad = atan2(dy.toDouble(), dx.toDouble())
        val angleDeg = Math.toDegrees(angleRad).toFloat()
        var normalizedAngle = (angleDeg - START_ANGLE_DEG) % 360f
        if (normalizedAngle < 0f) normalizedAngle += 360f

        val core: CoreEmotionTag
        val coreSweep: Float
        val angleInCore: Float

        if (focusedCoreId != null) {
            core = catalog.cores.firstOrNull { it.id == focusedCoreId } ?: return null
            coreSweep = 360f
            angleInCore = normalizedAngle
        } else {
            val coreCount = catalog.cores.size
            if (coreCount == 0) return null
            coreSweep = 360f / coreCount
            val coreIndex = (normalizedAngle / coreSweep).toInt().coerceIn(0, coreCount - 1)
            core = catalog.cores[coreIndex]
            angleInCore = normalizedAngle - (coreIndex * coreSweep)
        }

        if (coreOnly || dist < innerRadius) {
            return core.id
        }

        if (core.groups.isNotEmpty()) {
            val groupCount = core.groups.size
            val groupSweep = coreSweep / groupCount
            val groupIndex = (angleInCore / groupSweep).toInt().coerceIn(0, groupCount - 1)
            val group = core.groups[groupIndex]

            return if (dist < middleRadius) {
                group.id
            } else {
                val outerCount = group.outer.size
                if (outerCount == 0) return group.id
                val outerSweep = groupSweep / outerCount
                val angleInGroup = angleInCore - (groupIndex * groupSweep)
                val outerIndex = (angleInGroup / outerSweep).toInt().coerceIn(0, outerCount - 1)
                group.outer[outerIndex]
            }
        } else {
            val fineCount = core.fine.size
            if (fineCount == 0) return core.id
            val fineSweep = coreSweep / fineCount
            val fineIndex = (angleInCore / fineSweep).toInt().coerceIn(0, fineCount - 1)
            return core.fine[fineIndex]
        }
    }
}

/**
 * Interactive 3-Ring Emotion Wheel composable.
 *
 * Supports:
 * - Full 3-ring wheel (`focusedCoreId == null`, `coreOnly == false`):
 *   Inner ring (8 cores) + Middle ring (41 emotions) + Outer ring (82 emotions).
 * - Focused Core 3-ring wheel (`focusedCoreId != null`):
 *   Expands one core family across the full 360° wheel so its middle and outer wedges are large and easy to tap;
 *   tapping the center hole zooms back out to all cores.
 * - Core-only wheel (`coreOnly == true`):
 *   Displays the 8 core emotion slices across the full radius (Step 1 of Gradual Wheel mode).
 */
@Composable
fun EmotionWheel(
    selectedTags: List<String>,
    onTagToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
    catalog: EmotionTags = EmotionTagsCatalog.get(LocalContext.current),
    focusedCoreId: String? = null,
    coreOnly: Boolean = false,
    onCenterHoleClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val wheelCd = stringResource(R.string.tags_wheel_cd)
    val backLabel = stringResource(R.string.action_back)
    val density = LocalDensity.current

    val smileColors = LocalSmileProcColors.current
    val surfaceColor = smileColors.sheetContainer
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val strokeColor = surfaceColor.copy(alpha = 0.94f)
    val highlightStrokeColor = smileColors.wheelSelectionStroke

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val diameter: Dp = min(maxWidth - 16.dp, 380.dp)
        if (diameter <= 0.dp) return@BoxWithConstraints

        val diameterPx = with(density) { diameter.toPx() }
        val radiusPx = diameterPx / 2f
        val holeRadiusPx = radiusPx * EmotionWheelMath.HOLE_RATIO
        val innerRadiusPx = if (coreOnly) radiusPx else radiusPx * EmotionWheelMath.INNER_RATIO
        val middleRadiusPx = radiusPx * EmotionWheelMath.MIDDLE_RATIO

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
                .pointerInput(catalog, selectedTags, focusedCoreId, coreOnly, onCenterHoleClick) {
                    detectTapGestures { offset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.width / 2f
                        if (onCenterHoleClick != null && EmotionWheelMath.isCenterHoleTap(offset.x, offset.y, cx, cy, r)) {
                            onCenterHoleClick()
                            return@detectTapGestures
                        }
                        val tapped = EmotionWheelMath.findTappedTag(
                            tapX = offset.x,
                            tapY = offset.y,
                            centerX = cx,
                            centerY = cy,
                            radius = r,
                            catalog = catalog,
                            focusedCoreId = focusedCoreId,
                            coreOnly = coreOnly,
                        )
                        if (tapped != null) {
                            onTagToggled(tapped)
                        }
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val activeCores = if (focusedCoreId != null) {
                catalog.cores.filter { it.id == focusedCoreId }
            } else {
                catalog.cores
            }
            val coreCount = activeCores.size
            if (coreCount == 0) return@Canvas
            val coreSweep = 360f / coreCount

            val dividerWidthPx = with(density) { if (focusedCoreId != null || coreOnly) 1.5.dp.toPx() else 0.9.dp.toPx() }
            val highlightWidthPx = with(density) { 2.8.dp.toPx() }

            // Soft aurora glow behind the wheel
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(smileColors.brandAccent.copy(alpha = 0.20f), Color.Transparent),
                    center = center,
                    radius = radiusPx * 1.12f,
                ),
                radius = radiusPx * 1.12f,
                center = center,
            )

            activeCores.forEachIndexed { i, core ->
                val startAngle = EmotionWheelMath.START_ANGLE_DEG + (i * coreSweep)
                val baseColor = parseCoreColor(core.color)
                val isCoreSelected = core.id in selectedTags

                // 1. Draw Inner Ring (Core)
                val coreWedgePath = Path().apply {
                    if (coreSweep >= 359.9f) {
                        addOval(Rect(center.x - innerRadiusPx, center.y - innerRadiusPx, center.x + innerRadiusPx, center.y + innerRadiusPx))
                    } else {
                        arcWedge(
                            center = center,
                            innerRadius = holeRadiusPx,
                            outerRadius = innerRadiusPx,
                            startAngleDegrees = startAngle,
                            sweepAngleDegrees = coreSweep,
                        )
                    }
                }

                drawPath(coreWedgePath, color = baseColor)
                drawPath(coreWedgePath, color = strokeColor, style = Stroke(width = dividerWidthPx))
                if (isCoreSelected) {
                    drawPath(coreWedgePath, color = highlightStrokeColor, style = Stroke(width = highlightWidthPx))
                }

                // Core label
                val coreLabel = context.resolveTagLabel(core.id)
                val coreMidAngle = if (coreSweep >= 359.9f) -90f else startAngle + (coreSweep / 2f)
                val coreMidRadius = (holeRadiusPx + innerRadiusPx) / 2f
                drawRadialOrCurvedLabel(
                    label = coreLabel,
                    center = center,
                    angleDegrees = coreMidAngle,
                    midRadius = coreMidRadius,
                    ringThickness = innerRadiusPx - holeRadiusPx,
                    baseTextSizeSp = when {
                        coreOnly -> 12.5f
                        focusedCoreId != null -> 11f
                        diameterPx < 600f -> 8.5f
                        else -> 9.5f
                    },
                    isSelected = isCoreSelected,
                    curved = coreSweep >= 40f,
                    density = density,
                    textPaint = textPaint,
                    highlightColor = highlightStrokeColor,
                )

                if (!coreOnly) {
                    if (core.groups.isNotEmpty()) {
                        // 2. Draw Middle Ring (core.groups) & 3. Outer Ring (group.outer)
                        val groupCount = core.groups.size
                        val groupSweep = coreSweep / groupCount

                        core.groups.forEachIndexed { g, group ->
                            val groupStartAngle = startAngle + (g * groupSweep)
                            val isGroupSelected = group.id in selectedTags
                            val middleColor = lerp(baseColor, Color.White, if (g % 2 == 0) 0.14f else 0.04f)

                            val groupWedgePath = Path().apply {
                                arcWedge(
                                    center = center,
                                    innerRadius = innerRadiusPx,
                                    outerRadius = middleRadiusPx,
                                    startAngleDegrees = groupStartAngle,
                                    sweepAngleDegrees = groupSweep,
                                )
                            }

                            drawPath(groupWedgePath, color = middleColor)
                            drawPath(groupWedgePath, color = strokeColor, style = Stroke(width = dividerWidthPx))
                            if (isGroupSelected) {
                                drawPath(groupWedgePath, color = highlightStrokeColor, style = Stroke(width = highlightWidthPx))
                            }

                            val groupMidAngle = groupStartAngle + (groupSweep / 2f)
                            val groupMidRadius = (innerRadiusPx + middleRadiusPx) / 2f
                            val groupLabel = context.resolveTagLabel(group.id)
                            drawRadialOrCurvedLabel(
                                label = groupLabel,
                                center = center,
                                angleDegrees = groupMidAngle,
                                midRadius = groupMidRadius,
                                ringThickness = middleRadiusPx - innerRadiusPx,
                                baseTextSizeSp = when {
                                    focusedCoreId != null -> 9.5f
                                    diameterPx < 600f -> 6.2f
                                    else -> 7.0f
                                },
                                isSelected = isGroupSelected,
                                curved = false,
                                density = density,
                                textPaint = textPaint,
                                highlightColor = highlightStrokeColor,
                            )

                            // 3. Draw Outer Ring for this group
                            val outerCount = group.outer.size
                            if (outerCount > 0) {
                                val outerSweep = groupSweep / outerCount
                                group.outer.forEachIndexed { o, outerId ->
                                    val outerStartAngle = groupStartAngle + (o * outerSweep)
                                    val isOuterSelected = outerId in selectedTags
                                    val outerColor = lerp(baseColor, Color.White, if ((g * outerCount + o) % 2 == 0) 0.28f else 0.18f)

                                    val outerWedgePath = Path().apply {
                                        arcWedge(
                                            center = center,
                                            innerRadius = middleRadiusPx,
                                            outerRadius = radiusPx,
                                            startAngleDegrees = outerStartAngle,
                                            sweepAngleDegrees = outerSweep,
                                        )
                                    }

                                    drawPath(outerWedgePath, color = outerColor)
                                    drawPath(outerWedgePath, color = strokeColor, style = Stroke(width = dividerWidthPx))
                                    if (isOuterSelected) {
                                        drawPath(outerWedgePath, color = highlightStrokeColor, style = Stroke(width = highlightWidthPx))
                                    }

                                    val outerMidAngle = outerStartAngle + (outerSweep / 2f)
                                    val outerMidRadius = (middleRadiusPx + radiusPx) / 2f
                                    val outerLabel = context.resolveTagLabel(outerId)
                                    drawRadialOrCurvedLabel(
                                        label = outerLabel,
                                        center = center,
                                        angleDegrees = outerMidAngle,
                                        midRadius = outerMidRadius,
                                        ringThickness = radiusPx - middleRadiusPx,
                                        baseTextSizeSp = when {
                                            focusedCoreId != null -> 8.8f
                                            diameterPx < 600f -> 5.6f
                                            else -> 6.4f
                                        },
                                        isSelected = isOuterSelected,
                                        curved = false,
                                        density = density,
                                        textPaint = textPaint,
                                        highlightColor = highlightStrokeColor,
                                    )
                                }
                            }
                        }
                    } else {
                        // Fallback 2-ring drawing if groups is empty
                        val fineCount = core.fine.size
                        if (fineCount > 0) {
                            val fineSweep = coreSweep / fineCount
                            core.fine.forEachIndexed { j, fineId ->
                                val fineStartAngle = startAngle + (j * fineSweep)
                                val isFineSelected = fineId in selectedTags
                                val fineColor = lerp(baseColor, Color.White, if (j % 2 == 0) 0.18f else 0.08f)

                                val fineWedgePath = Path().apply {
                                    arcWedge(
                                        center = center,
                                        innerRadius = innerRadiusPx,
                                        outerRadius = radiusPx,
                                        startAngleDegrees = fineStartAngle,
                                        sweepAngleDegrees = fineSweep,
                                    )
                                }

                                drawPath(fineWedgePath, color = fineColor)
                                drawPath(fineWedgePath, color = strokeColor, style = Stroke(width = dividerWidthPx))
                                if (isFineSelected) {
                                    drawPath(fineWedgePath, color = highlightStrokeColor, style = Stroke(width = highlightWidthPx))
                                }

                                val fineMidAngle = fineStartAngle + (fineSweep / 2f)
                                val fineMidRadius = (innerRadiusPx + radiusPx) / 2f
                                val fineLabel = context.resolveTagLabel(fineId)
                                drawRadialOrCurvedLabel(
                                    label = fineLabel,
                                    center = center,
                                    angleDegrees = fineMidAngle,
                                    midRadius = fineMidRadius,
                                    ringThickness = radiusPx - innerRadiusPx,
                                    baseTextSizeSp = 8.5f,
                                    isSelected = isFineSelected,
                                    curved = false,
                                    density = density,
                                    textPaint = textPaint,
                                    highlightColor = highlightStrokeColor,
                                )
                            }
                        }
                    }
                }
            }

            // Center Hole
            drawCircle(
                color = surfaceColor,
                radius = holeRadiusPx,
                center = center,
            )
            drawCircle(
                color = smileColors.brandAccent.copy(alpha = 0.55f),
                radius = holeRadiusPx,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            if (onCenterHoleClick != null) {
                drawContext.canvas.nativeCanvas.apply {
                    textPaint.textSize = with(density) { 9.5.sp.toPx() }
                    textPaint.color = onSurfaceColor.toArgb()
                    drawText(backLabel, center.x, center.y + with(density) { 3.5.sp.toPx() }, textPaint)
                }
            }
        }
    }
}

private fun DrawScope.drawRadialOrCurvedLabel(
    label: String,
    center: Offset,
    angleDegrees: Float,
    midRadius: Float,
    ringThickness: Float,
    baseTextSizeSp: Float,
    isSelected: Boolean,
    curved: Boolean,
    density: Density,
    textPaint: Paint,
    highlightColor: Color,
) {
    drawContext.canvas.nativeCanvas.apply {
        save()
        val normAngle = (angleDegrees % 360f + 360f) % 360f
        textPaint.color = Color(0xFF1C1B1F).toArgb()

        if (curved) {
            val isBottomHalf = normAngle in 0f..180f
            val rotation = if (isBottomHalf) normAngle - 90f else normAngle + 90f
            rotate(rotation, center.x, center.y)
            textPaint.textSize = with(density) { baseTextSizeSp.sp.toPx() }
            val textY = if (isBottomHalf) {
                center.y + midRadius + textPaint.textSize * 0.35f
            } else {
                center.y - midRadius + textPaint.textSize * 0.35f
            }
            drawText(label, center.x, textY, textPaint)
            if (isSelected) {
                val dotY = if (isBottomHalf) textY - with(density) { 10.dp.toPx() } else textY + with(density) { 6.dp.toPx() }
                drawCircle(center.x, dotY, with(density) { 2.5.dp.toPx() }, Paint().apply {
                    color = highlightColor.toArgb()
                    isAntiAlias = true
                })
            }
        } else {
            val isLeftSide = normAngle in 90f..270f
            val rotation = if (isLeftSide) normAngle + 180f else normAngle
            rotate(rotation, center.x, center.y)

            val maxTextWidth = (ringThickness - with(density) { 6.dp.toPx() }).coerceAtLeast(16f)
            var sizePx = with(density) { baseTextSizeSp.sp.toPx() }
            textPaint.textSize = sizePx
            val measured = textPaint.measureText(label)
            if (measured > maxTextWidth && measured > 0f) {
                sizePx = (sizePx * (maxTextWidth / measured)).coerceAtLeast(with(density) { 4.8.sp.toPx() })
                textPaint.textSize = sizePx
            }

            val textX = if (isLeftSide) center.x - midRadius else center.x + midRadius
            val textY = center.y + sizePx * 0.34f
            drawText(label, textX, textY, textPaint)

            if (isSelected) {
                val dotOffset = (ringThickness / 2f - with(density) { 4.dp.toPx() }).coerceAtLeast(4f)
                val dotX = if (isLeftSide) textX - dotOffset else textX + dotOffset
                drawCircle(dotX, center.y, with(density) { 2.2.dp.toPx() }, Paint().apply {
                    color = highlightColor.toArgb()
                    isAntiAlias = true
                })
            }
        }
        restore()
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
    lineTo(
        (center.x + innerRadius * cos(endRad)).toFloat(),
        (center.y + innerRadius * sin(endRad)).toFloat(),
    )
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
