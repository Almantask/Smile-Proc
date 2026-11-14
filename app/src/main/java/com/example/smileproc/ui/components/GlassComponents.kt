package com.example.smileproc.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.smileproc.AppLocale
import com.example.smileproc.R
import com.example.smileproc.core.Entry
import com.example.smileproc.core.Mood
import com.example.smileproc.ui.AppTab
import com.example.smileproc.ui.tags.EmotionTagsCatalog
import com.example.smileproc.ui.tags.coreTagChipColors
import com.example.smileproc.ui.tags.resolveContextTagLabel
import com.example.smileproc.ui.tags.resolveTagCoreColor
import com.example.smileproc.ui.tags.resolveTagLabel
import com.example.smileproc.ui.theme.LocalSmileProcColors
import com.example.smileproc.ui.theme.moodAccent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

private enum class MoteTone {
    BRAND,
    ECHO,
    CORAL,
    ORCHID,
}

private data class FloatingMote(
    val anchorX: Float,
    val anchorY: Float,
    val baseRadiusDp: Float,
    val ampXDp: Float,
    val ampYDp: Float,
    val freqX: Int,
    val freqY: Int,
    val phaseOffset: Float,
    val baseAlpha: Float,
    val tone: MoteTone,
)

private val AmbientMotes = listOf(
    FloatingMote(0.15f, 0.16f, 22f, 34f, 36f, 1, 2, 0.30f, 0.32f, MoteTone.BRAND),
    FloatingMote(0.84f, 0.12f, 16f, 30f, 32f, 2, 1, 1.15f, 0.35f, MoteTone.CORAL),
    FloatingMote(0.48f, 0.24f, 11f, 38f, 26f, 3, 2, 2.40f, 0.32f, MoteTone.ORCHID),
    FloatingMote(0.11f, 0.44f, 19f, 32f, 42f, 2, 3, 3.20f, 0.33f, MoteTone.ORCHID),
    FloatingMote(0.88f, 0.42f, 22f, 34f, 38f, 1, 3, 4.10f, 0.34f, MoteTone.ECHO),
    FloatingMote(0.36f, 0.62f, 12f, 44f, 32f, 3, 1, 0.85f, 0.36f, MoteTone.CORAL),
    FloatingMote(0.74f, 0.68f, 28f, 40f, 46f, 2, 1, 1.95f, 0.32f, MoteTone.BRAND),
    FloatingMote(0.22f, 0.78f, 17f, 36f, 40f, 1, 2, 5.05f, 0.36f, MoteTone.ORCHID),
    FloatingMote(0.86f, 0.84f, 14f, 30f, 34f, 3, 2, 2.80f, 0.36f, MoteTone.ECHO),
    FloatingMote(0.52f, 0.86f, 20f, 46f, 30f, 2, 3, 4.70f, 0.33f, MoteTone.BRAND),
)

/**
 * Full-bleed living aurora backdrop with multi-hue ambient washes, subtle mood echo, and floating bokeh motes.
 */
@Composable
fun SmileProcBackground(
    modifier: Modifier = Modifier,
    latestMood: Mood? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalSmileProcColors.current
    val reducedMotion = rememberReducedMotion()
    val infinite = rememberInfiniteTransition(label = "backdropAurora")
    val breathState = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "auroraPhase",
    )
    val orbitState = infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "moteOrbit",
    )
    val moodEchoColor = colors.moodAccent(latestMood ?: Mood.GOOD)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Read animation states inside drawBehind so composition does not rerun each frame.
                val phase = if (reducedMotion) 0.5f else breathState.value
                val orbit = if (reducedMotion) 0.7f else orbitState.value

                drawRect(color = colors.canvasBase)

                // Top-left violet aurora orb
                val c1 = Offset(size.width * (0.28f + 0.08f * phase), size.height * 0.12f)
                val r1 = size.maxDimension * (0.52f + 0.05f * phase)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colors.glowPrimary, Color.Transparent),
                        center = c1,
                        radius = r1,
                    ),
                    radius = r1,
                    center = c1,
                )

                // Top-right warm coral sunrise orb
                val c2 = Offset(size.width * (0.86f - 0.06f * phase), size.height * 0.20f)
                val r2 = size.maxDimension * 0.44f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colors.glowSecondary, Color.Transparent),
                        center = c2,
                        radius = r2,
                    ),
                    radius = r2,
                    center = c2,
                )

                // Mid-right mood-echo wash
                val c3 = Offset(
                    x = size.width * (0.72f + 0.04f * cos(orbit)),
                    y = size.height * (0.54f + 0.03f * sin(orbit)),
                )
                val r3 = size.maxDimension * 0.46f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            moodEchoColor.copy(alpha = if (colors.isDark) 0.20f else 0.16f),
                            Color.Transparent,
                        ),
                        center = c3,
                        radius = r3,
                    ),
                    radius = r3,
                    center = c3,
                )

                // Lower indigo-teal pool
                val c4 = Offset(
                    x = size.width * (0.20f - 0.04f * sin(orbit)),
                    y = size.height * (0.88f + 0.025f * cos(orbit)),
                )
                val r4 = size.maxDimension * 0.48f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.brandAccent.copy(alpha = if (colors.isDark) 0.28f else 0.20f),
                            Color.Transparent,
                        ),
                        center = c4,
                        radius = r4,
                    ),
                    radius = r4,
                    center = c4,
                )

                // Floating ambient bokeh motes on seamless periodic Lissajous paths
                val coralTone = if (colors.isDark) Color(0xFFFFA07A) else Color(0xFFFF8F6B)
                val orchidTone = if (colors.isDark) Color(0xFFC084FC) else Color(0xFFA855F7)
                AmbientMotes.forEach { mote ->
                    val col = when (mote.tone) {
                        MoteTone.BRAND -> colors.brandAccent
                        MoteTone.ECHO -> moodEchoColor
                        MoteTone.CORAL -> coralTone
                        MoteTone.ORCHID -> orchidTone
                    }
                    val angleX = orbit * mote.freqX + mote.phaseOffset
                    val angleY = orbit * mote.freqY + mote.phaseOffset * 1.3f
                    val dx = sin(angleX) * mote.ampXDp.dp.toPx()
                    val dy = cos(angleY) * mote.ampYDp.dp.toPx()
                    val moteCenter = Offset(size.width * mote.anchorX + dx, size.height * mote.anchorY + dy)

                    val sizePulse = 0.90f + 0.14f * sin(orbit + mote.phaseOffset)
                    val alphaPulse = 0.80f + 0.20f * cos(orbit * 2f + mote.phaseOffset)
                    val moteRadius = mote.baseRadiusDp.dp.toPx() * sizePulse
                    val peakAlpha = mote.baseAlpha * (if (colors.isDark) 1f else 0.92f) * alphaPulse

                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to col.copy(alpha = peakAlpha),
                            0.34f to col.copy(alpha = peakAlpha * 0.52f),
                            1f to Color.Transparent,
                            center = moteCenter,
                            radius = moteRadius,
                        ),
                        radius = moteRadius,
                        center = moteCenter,
                    )
                }
            },
        content = content,
    )
}

/**
 * Centered calm circular SmileProc mark + wordmark used at the top of Today.
 */
@Composable
fun SmileProcBrandHeader(
    modifier: Modifier = Modifier,
) {
    val colors = LocalSmileProcColors.current
    val markColor = remember(colors) {
        lerp(colors.brandAccent, colors.brandWordmark, 0.28f)
    }
    val markFill = remember(colors) {
        colors.brandAccent.copy(alpha = if (colors.isDark) 0.10f else 0.07f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val faceRadius = size.minDimension * 0.42f
            val strokeWidth = 1.6.dp.toPx()

            drawCircle(
                color = markFill,
                radius = faceRadius,
                center = center,
            )
            drawCircle(
                color = markColor,
                radius = faceRadius,
                center = center,
                style = Stroke(width = strokeWidth),
            )

            val eyeRadius = (faceRadius * 0.115f).coerceAtLeast(1.25.dp.toPx())
            val eyeOffsetX = faceRadius * 0.34f
            val eyeOffsetY = faceRadius * 0.15f
            drawCircle(
                color = markColor,
                radius = eyeRadius,
                center = Offset(center.x - eyeOffsetX, center.y - eyeOffsetY),
            )
            drawCircle(
                color = markColor,
                radius = eyeRadius,
                center = Offset(center.x + eyeOffsetX, center.y - eyeOffsetY),
            )

            val mouthHalfW = faceRadius * 0.36f
            val mouthY = center.y + faceRadius * 0.21f
            val smile = Path().apply {
                moveTo(center.x - mouthHalfW, mouthY)
                quadraticTo(
                    center.x,
                    mouthY + faceRadius * 0.16f,
                    center.x + mouthHalfW,
                    mouthY,
                )
            }
            drawPath(
                path = smile,
                color = markColor,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
            ),
            color = colors.brandWordmark,
        )
    }
}

/**
 * Frosted translucent card with subtle top-lit hairline border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSmileProcColors.current
    val fillBrush = remember(colors, highlighted) {
        if (highlighted) {
            Brush.verticalGradient(listOf(colors.glassHighlightTop, colors.glassHighlightBottom))
        } else {
            Brush.verticalGradient(listOf(colors.glassTop, colors.glassBottom))
        }
    }
    val borderBrush = remember(colors) {
        Brush.verticalGradient(listOf(colors.glassBorderTop, colors.glassBorderBottom))
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = true),
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = fillBrush, shape = shape)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(clickableModifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Pebble-shaped mood check-in card combining:
 * - Organic 28.dp rounded silhouette with subtle mood-washed fill and border
 * - Expressive vertical mood accent stripe along the leading edge
 * - Softly haloed face icon and core-colored feeling tag pills
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodEntryCard(
    entry: Entry,
    placeName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxTags: Int = 3,
    extras: com.example.smileproc.core.EntryExtras? = null,
    customTagsById: Map<String, com.example.smileproc.core.CustomTag> = emptyMap(),
) {
    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    val catalog = remember(context) { EmotionTagsCatalog.get(context) }
    val locale = AppLocale.effectiveLocale(context)
    val timeFormatter = remember(entry.timeZoneID, locale) {
        val zone = try { ZoneId.of(entry.timeZoneID) } catch (_: Exception) { ZoneId.systemDefault() }
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    }

    val moodAccent = smileColors.moodAccent(entry.mood)
    val cardShape = RoundedCornerShape(28.dp)
    val fillBrush = remember(smileColors, entry.mood) {
        val leftWash = lerp(smileColors.glassTop, moodAccent, if (smileColors.isDark) 0.14f else 0.08f)
        Brush.horizontalGradient(listOf(leftWash, smileColors.glassBottom))
    }
    val borderBrush = remember(smileColors, entry.mood) {
        Brush.horizontalGradient(listOf(moodAccent.copy(alpha = 0.46f), smileColors.glassBorderBottom))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(cardShape)
            .background(brush = fillBrush, shape = cardShape)
            .border(width = 1.dp, brush = borderBrush, shape = cardShape)
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(moodAccent),
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.drawBehind {
                    val haloR = this.size.minDimension * 0.78f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(moodAccent.copy(alpha = 0.32f), Color.Transparent),
                            center = center,
                            radius = haloR,
                        ),
                        radius = haloR,
                        center = center,
                    )
                },
            ) {
                FaceIcon(mood = entry.mood, size = 40.dp)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = timeFormatter.format(entry.createdAt),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (extras?.bookmarked == true) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bookmark),
                            contentDescription = stringResource(R.string.followup_bookmarked_label),
                            tint = smileColors.brandAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (placeName != null) {
                        GlassTagPill(
                            text = placeName,
                            containerColor = smileColors.brandAccent.copy(alpha = 0.16f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            borderColor = smileColors.brandAccent.copy(alpha = 0.35f),
                        )
                    }
                    entry.tags.take(maxTags).forEach { tagId ->
                        val coreColor = resolveTagCoreColor(catalog, tagId)
                        if (coreColor == null) {
                            GlassTagPill(text = context.resolveTagLabel(tagId))
                        } else {
                            val pillColors = coreTagChipColors(coreColor, isDark = smileColors.isDark)
                            GlassTagPill(
                                text = context.resolveTagLabel(tagId),
                                containerColor = pillColors.container,
                                contentColor = pillColors.content,
                                borderColor = pillColors.border,
                            )
                        }
                    }
                    if (extras != null) {
                        val extraLabels = buildList {
                            extras.contextTags.forEach { ctxId ->
                                add(context.resolveContextTagLabel(ctxId))
                            }
                            extras.customTagIds.forEach { customId ->
                                customTagsById[customId]?.let { add(it.label) }
                            }
                        }
                        val extraLimit = if (maxTags == Int.MAX_VALUE) extraLabels.size else 2
                        extraLabels.take(extraLimit).forEach { label ->
                            GlassTagPill(text = label)
                        }
                    }
                }

                val comment = entry.comment
                if (!comment.isNullOrBlank()) {
                    Text(
                        text = comment,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Full-width frosted capsule button (e.g., "All entries >").
 */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val colors = LocalSmileProcColors.current
    val pillBrush = remember(colors) {
        Brush.verticalGradient(listOf(colors.pillFillTop, colors.pillFillBottom))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(brush = pillBrush, shape = CircleShape)
            .border(width = 1.dp, color = colors.pillBorder, shape = CircleShape)
            .semantics { contentDescription = text }
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = contentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (showChevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(20.dp),
            )
        }
    }
}

/**
 * Compact frosted pill used for emotion tags and place labels inside entry cards.
 */
@Composable
fun GlassTagPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = null,
) {
    val colors = LocalSmileProcColors.current
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor ?: colors.chipFill,
        border = BorderStroke(1.dp, borderColor ?: colors.chipBorder),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
        )
    }
}

/**
 * Subtle hairline divider matching the frosted card borders.
 */
@Composable
fun GlassDivider(
    modifier: Modifier = Modifier,
) {
    val colors = LocalSmileProcColors.current
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = colors.glassDivider,
    )
}

/**
 * Transparent top bar with optional circular frosted back button for sub-screens and tabs.
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalSmileProcColors.current
    val backLabel = stringResource(R.string.action_back)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        if (onNavigateBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = backLabel }
                    .clickable(
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 24.dp),
                        onClick = onNavigateBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.chipFill)
                        .border(1.dp, colors.chipBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = if (onNavigateBack != null) 56.dp else 16.dp),
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/**
 * Frosted bottom navigation bar with glowing violet active pill.
 */
@Composable
fun GlassBottomNavBar(
    currentTab: AppTab,
    hasUnseenCard: Boolean,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSmileProcColors.current
    val reducedMotion = rememberReducedMotion()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(colors.navBarFill),
    ) {
        HorizontalDivider(thickness = 1.dp, color = colors.navBarBorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavBarTabItem(
                tab = AppTab.TODAY,
                selected = currentTab == AppTab.TODAY,
                label = stringResource(R.string.tab_today),
                iconRes = R.drawable.ic_tab_today,
                hasBadge = false,
                reducedMotion = reducedMotion,
                onClick = { onSelectTab(AppTab.TODAY) },
                modifier = Modifier.weight(1f),
            )
            NavBarTabItem(
                tab = AppTab.WEEK,
                selected = currentTab == AppTab.WEEK,
                label = stringResource(R.string.tab_week),
                iconRes = R.drawable.ic_tab_week,
                hasBadge = hasUnseenCard,
                reducedMotion = reducedMotion,
                onClick = { onSelectTab(AppTab.WEEK) },
                modifier = Modifier.weight(1f),
            )
            NavBarTabItem(
                tab = AppTab.SETTINGS,
                selected = currentTab == AppTab.SETTINGS,
                label = stringResource(R.string.tab_settings),
                iconRes = R.drawable.ic_tab_settings,
                hasBadge = false,
                reducedMotion = reducedMotion,
                onClick = { onSelectTab(AppTab.SETTINGS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavBarTabItem(
    tab: AppTab,
    selected: Boolean,
    label: String,
    iconRes: Int,
    hasBadge: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSmileProcColors.current
    val pillColor by animateColorAsState(
        targetValue = if (selected) colors.navActivePill else Color.Transparent,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 180),
        label = "navPill_${tab.name}",
    )
    val pillBorderColor by animateColorAsState(
        targetValue = if (selected) colors.navActiveBorder else Color.Transparent,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 180),
        label = "navBorder_${tab.name}",
    )
    val contentTint by animateColorAsState(
        targetValue = if (selected) colors.navActiveContent else colors.navInactiveContent,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 180),
        label = "navContent_${tab.name}",
    )
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 62.dp else 44.dp,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 200),
        label = "navWidth_${tab.name}",
    )

    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = label
            }
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(34.dp)
                .drawBehind {
                    if (selected) {
                        val glowRadius = size.maxDimension * 0.72f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.navActiveGlow, Color.Transparent),
                                center = center,
                                radius = glowRadius,
                            ),
                            radius = glowRadius,
                            center = center,
                        )
                    }
                }
                .clip(CircleShape)
                .background(pillColor, CircleShape)
                .border(1.dp, pillBorderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BadgedBox(
                badge = {
                    if (hasBadge) {
                        Badge(containerColor = colors.brandAccent)
                    }
                },
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = contentTint,
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = contentTint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Shared frosted modal bottom sheet wrapper.
 *
 * Supplies an explicit theme `contentColor` so `Text` inside doesn't fall back to black on
 * custom sheet surfaces, and aligns the Dialog window's system bar icon contrast with the theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val smileColors = LocalSmileProcColors.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = smileColors.sheetContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = smileColors.pillBorder) },
        modifier = modifier,
    ) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !smileColors.isDark
                controller.isAppearanceLightNavigationBars = !smileColors.isDark
            }
        }
        content()
    }
}
