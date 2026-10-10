package com.example.smileproc.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.smileproc.R
import com.example.smileproc.core.EmotionCatalog
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    catalog: EmotionCatalog,
    mode: String,
    selected: List<String>,
    onSelected: (List<String>) -> Unit,
    onMode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listMode = prefersList(context)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.forEach { id ->
                    val label = tagLabel(context, id)
                    FilterChip(
                        selected = true,
                        onClick = { onSelected(selected - id) },
                        label = { Text(label) },
                        modifier = Modifier.semantics {
                            contentDescription = text(context, R.string.tags_a11y_remove, label)
                        },
                    )
                }
            }
        }
        TextButton(
            onClick = { onMode(if (mode == "detailed") "gradual" else "detailed") },
            modifier = Modifier.semantics { contentDescription = text(context, R.string.tags_a11y_mode) },
        ) {
            Text(
                if (mode == "detailed") {
                    text(context, R.string.tags_mode_gradual)
                } else {
                    text(context, R.string.tags_mode_detailed)
                },
            )
        }
        when {
            listMode -> ListPicker(catalog, selected, onSelected)
            mode == "detailed" -> WheelPicker(catalog, selected, onSelected)
            else -> GradualPicker(catalog, selected, onSelected)
        }
    }
}

@Composable
private fun prefersList(context: Context): Boolean {
    val fontScale = LocalDensity.current.fontScale
    val talkBack = remember(context) {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        manager?.isTouchExplorationEnabled == true
    }
    return talkBack || fontScale >= 1.3f
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradualPicker(
    catalog: EmotionCatalog,
    selected: List<String>,
    onSelected: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    var coreId = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val core = catalog.cores.firstOrNull { it.id == coreId.value }
    if (core == null) {
        Text(text(context, R.string.tags_closest), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            catalog.cores.forEach { item ->
                FilterChip(
                    selected = false,
                    onClick = { coreId.value = item.id },
                    label = { Text(tagLabel(context, item.id)) },
                )
            }
        }
    } else {
        TextButton(onClick = { coreId.value = null }) { Text(text(context, R.string.common_back)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = core.id in selected,
                onClick = { onSelected(toggle(selected, core.id)) },
                label = { Text(text(context, R.string.tags_justCore, tagLabel(context, core.id))) },
            )
            core.fine.forEach { id ->
                FilterChip(
                    selected = id in selected,
                    onClick = { onSelected(toggle(selected, id)) },
                    label = { Text(tagLabel(context, id)) },
                )
            }
        }
    }
}

@Composable
private fun ListPicker(
    catalog: EmotionCatalog,
    selected: List<String>,
    onSelected: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        catalog.cores.forEach { core ->
            Text(tagLabel(context, core.id), style = MaterialTheme.typography.titleSmall)
            FilterChip(
                selected = core.id in selected,
                onClick = { onSelected(toggle(selected, core.id)) },
                label = { Text(text(context, R.string.tags_justCore, tagLabel(context, core.id))) },
            )
            core.fine.forEach { id ->
                FilterChip(
                    selected = id in selected,
                    onClick = { onSelected(toggle(selected, id)) },
                    label = { Text(tagLabel(context, id)) },
                )
            }
        }
    }
}

@Composable
private fun WheelPicker(
    catalog: EmotionCatalog,
    selected: List<String>,
    onSelected: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        val diameter = min(maxWidth.value - 32f, 360f).dp
        val cores = catalog.cores
        Canvas(
            Modifier
                .height(diameter)
                .fillMaxWidth()
                .pointerInput(selected, cores) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        val radius = min(size.width, size.height) / 2f
                        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 450.0) % 360.0
                        val slice = 360.0 / cores.size
                        val index = (angle / slice).toInt().mod(cores.size)
                        val core = cores[index]
                        val id = when {
                            distance < radius * 0.42f -> core.id
                            distance <= radius -> core.fine[((angle % slice) / (slice / core.fine.size)).toInt().mod(core.fine.size)]
                            else -> null
                        }
                        if (id != null) onSelected(toggle(selected, id))
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f
            cores.forEachIndexed { index, core ->
                val slice = (Math.PI * 2) / cores.size
                val mid = -Math.PI / 2 + slice * index + slice / 2
                val color = parseColor(core.color)
                drawCircle(
                    color = color,
                    radius = radius * 0.38f,
                    center = Offset(
                        center.x + (cos(mid) * radius * 0.22f).toFloat(),
                        center.y + (sin(mid) * radius * 0.22f).toFloat(),
                    ),
                )
                core.fine.forEachIndexed { fineIndex, _ ->
                    val fineSlice = slice / core.fine.size
                    val fineMid = -Math.PI / 2 + slice * index + fineSlice * fineIndex + fineSlice / 2
                    drawCircle(
                        color = color,
                        radius = radius * 0.08f,
                        center = Offset(
                            center.x + (cos(fineMid) * radius * 0.72f).toFloat(),
                            center.y + (sin(fineMid) * radius * 0.72f).toFloat(),
                        ),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
        }
        Text(
            text = selected.joinToString { tagLabel(context, it) },
            modifier = Modifier.padding(top = diameter),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun toggle(selected: List<String>, id: String): List<String> {
    if (id in selected) return selected - id
    if (selected.size >= 3) return selected
    return selected + id
}

private fun parseColor(hex: String): Color {
    val value = hex.removePrefix("#").toLong(16)
    return Color(0xFF000000 or value)
}
