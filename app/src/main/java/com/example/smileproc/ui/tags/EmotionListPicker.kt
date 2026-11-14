package com.example.smileproc.ui.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.EmotionTags
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.theme.LocalSmileProcColors

/**
 * Accessible list picker displaying emotion tags organized in sections by core emotion.
 * Used when TalkBack is active, at large font scales (>= 1.5x), or when selected by the user.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmotionListPicker(
    selectedTags: List<String>,
    onTagToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
    catalog: EmotionTags = EmotionTagsCatalog.get(LocalContext.current),
) {
    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    val isMaxSelected = selectedTags.size >= 3

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        catalog.cores.forEach { core ->
            val coreLabel = context.resolveTagLabel(core.id)
            val coreColor = parseCoreColor(core.color)
            val chipPalette = coreTagChipColors(coreColor, isDark = smileColors.isDark, emphasized = true)
            GlassCard(
                contentPadding = PaddingValues(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = coreLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = chipPalette.content,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val justCoreLabel = stringResource(R.string.tags_just_core, coreLabel)
                        val isCoreSelected = core.id in selectedTags
                        FilterChip(
                            selected = isCoreSelected,
                            onClick = {
                                if (isCoreSelected || !isMaxSelected) {
                                    onTagToggled(core.id)
                                }
                            },
                            label = { Text(justCoreLabel) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = smileColors.chipFill,
                                selectedContainerColor = chipPalette.container,
                                selectedLabelColor = chipPalette.content,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isCoreSelected,
                                borderColor = smileColors.chipBorder,
                                selectedBorderColor = chipPalette.border,
                            ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                        core.fine.forEach { fineId ->
                            val fineLabel = context.resolveTagLabel(fineId)
                            val isFineSelected = fineId in selectedTags
                            FilterChip(
                                selected = isFineSelected,
                                onClick = {
                                    if (isFineSelected || !isMaxSelected) {
                                        onTagToggled(fineId)
                                    }
                                },
                                label = { Text(fineLabel) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = smileColors.chipFill,
                                    selectedContainerColor = chipPalette.container,
                                    selectedLabelColor = chipPalette.content,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isFineSelected,
                                    borderColor = smileColors.chipBorder,
                                    selectedBorderColor = chipPalette.border,
                                ),
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
