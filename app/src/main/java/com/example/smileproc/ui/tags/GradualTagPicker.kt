package com.example.smileproc.ui.tags

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ContextualFlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.CoreEmotionTag
import com.example.smileproc.core.EmotionTags
import com.example.smileproc.data.TagPickerMode

/**
 * Tag picker supporting Gradual mode (core -> fine) and Detailed list mode.
 * Up to 3 tags can be selected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradualTagPicker(
    selectedTags: List<String>,
    onTagsChanged: (List<String>) -> Unit,
    mode: TagPickerMode,
    onModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
    catalog: EmotionTags = EmotionTagsCatalog.get(LocalContext.current),
) {
    val context = LocalContext.current
    var selectedCoreId by remember { mutableStateOf<String?>(null) }
    val isMaxSelected = selectedTags.size >= 3

    Column(modifier = modifier.fillMaxWidth()) {
        // Selected tags pinned at the top
        if (selectedTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedTags.forEach { tagId ->
                    val label = context.resolveTagLabel(tagId)
                    val removeCd = stringResource(R.string.tags_remove_cd, label)
                    InputChip(
                        selected = true,
                        onClick = {
                            onTagsChanged(selectedTags - tagId)
                        },
                        label = { Text(label) },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = removeCd,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = removeCd },
                    )
                }
            }
        }

        // Mode switch header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val modeLabel = if (mode == TagPickerMode.GRADUAL) {
                stringResource(R.string.tags_mode_gradual)
            } else {
                stringResource(R.string.tags_mode_detailed)
            }
            Text(
                text = if (isMaxSelected) {
                    stringResource(R.string.tags_limit)
                } else if (mode == TagPickerMode.GRADUAL && selectedCoreId == null) {
                    stringResource(R.string.tags_step_core)
                } else if (mode == TagPickerMode.GRADUAL) {
                    context.resolveTagLabel(selectedCoreId!!)
                } else {
                    stringResource(R.string.tags_list_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val switchCd = stringResource(R.string.tags_mode_switch_cd)
            TextButton(
                onClick = onModeToggle,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = switchCd },
            ) {
                Text(modeLabel)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (mode == TagPickerMode.GRADUAL) {
            val activeCore = selectedCoreId?.let { id -> catalog.cores.firstOrNull { it.id == id } }
            if (activeCore == null) {
                // Step 1: 7 Core chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    catalog.cores.forEach { core ->
                        val coreLabel = context.resolveTagLabel(core.id)
                        val isCoreSelected = core.id in selectedTags
                        FilterChip(
                            selected = isCoreSelected,
                            onClick = { selectedCoreId = core.id },
                            label = { Text(coreLabel) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            } else {
                // Step 2: Finer chips + "Just {core}"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { selectedCoreId = null },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_back))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val coreLabel = context.resolveTagLabel(activeCore.id)
                    val justCoreLabel = stringResource(R.string.tags_just_core, coreLabel)
                    val isJustCoreSelected = activeCore.id in selectedTags

                    FilterChip(
                        selected = isJustCoreSelected,
                        onClick = {
                            toggleTag(
                                tagId = activeCore.id,
                                current = selectedTags,
                                isMax = isMaxSelected,
                                onChange = onTagsChanged,
                            )
                        },
                        label = { Text(justCoreLabel) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )

                    activeCore.fine.forEach { fineId ->
                        val fineLabel = context.resolveTagLabel(fineId)
                        val isFineSelected = fineId in selectedTags

                        FilterChip(
                            selected = isFineSelected,
                            onClick = {
                                toggleTag(
                                    tagId = fineId,
                                    current = selectedTags,
                                    isMax = isMaxSelected,
                                    onChange = onTagsChanged,
                                )
                            },
                            label = { Text(fineLabel) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        } else {
            // Detailed List Mode
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                catalog.cores.forEach { core ->
                    val coreLabel = context.resolveTagLabel(core.id)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = coreLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val justCoreLabel = stringResource(R.string.tags_just_core, coreLabel)
                            FilterChip(
                                selected = core.id in selectedTags,
                                onClick = {
                                    toggleTag(
                                        tagId = core.id,
                                        current = selectedTags,
                                        isMax = isMaxSelected,
                                        onChange = onTagsChanged,
                                    )
                                },
                                label = { Text(justCoreLabel) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                            core.fine.forEach { fineId ->
                                FilterChip(
                                    selected = fineId in selectedTags,
                                    onClick = {
                                        toggleTag(
                                            tagId = fineId,
                                            current = selectedTags,
                                            isMax = isMaxSelected,
                                            onChange = onTagsChanged,
                                        )
                                    },
                                    label = { Text(context.resolveTagLabel(fineId)) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun toggleTag(
    tagId: String,
    current: List<String>,
    isMax: Boolean,
    onChange: (List<String>) -> Unit,
) {
    if (tagId in current) {
        onChange(current - tagId)
    } else if (!isMax) {
        onChange(current + tagId)
    }
}
