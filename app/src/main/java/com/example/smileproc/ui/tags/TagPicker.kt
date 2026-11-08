package com.example.smileproc.ui.tags

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.EmotionTags
import com.example.smileproc.data.TagPickerMode

/**
 * Unified Emotion Tag Picker supporting:
 * - Gradual mode (Step 1 core -> Step 2 fine)
 * - Detailed mode:
 *   - Emotion Wheel canvas (default when TalkBack is off and font scale is normal)
 *   - Emotion List picker (when TalkBack/touch exploration is active, font scale >= 1.5x, or toggled)
 * - Pinned selection echo chips at the top (max 3 tags).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    selectedTags: List<String>,
    onTagsChanged: (List<String>) -> Unit,
    mode: TagPickerMode,
    onModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
    catalog: EmotionTags = EmotionTagsCatalog.get(LocalContext.current),
) {
    val context = LocalContext.current
    var selectedCoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var preferListInDetailed by rememberSaveable { mutableStateOf(false) }

    val accessibilityManager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    val isTouchExploration = accessibilityManager?.isTouchExplorationEnabled == true
    val isLargeFont = LocalDensity.current.fontScale >= 1.5f
    val forceList = isTouchExploration || isLargeFont

    val isMaxSelected = selectedTags.size >= 3

    fun toggleTag(tagId: String) {
        if (tagId in selectedTags) {
            onTagsChanged(selectedTags - tagId)
        } else if (selectedTags.size < 3) {
            onTagsChanged(selectedTags + tagId)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Pinned selected tags at the top
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
                        onClick = { onTagsChanged(selectedTags - tagId) },
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

        // Header with Mode switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val titleText = if (isMaxSelected) {
                stringResource(R.string.tags_limit)
            } else if (mode == TagPickerMode.GRADUAL && selectedCoreId == null) {
                stringResource(R.string.tags_step_core)
            } else if (mode == TagPickerMode.GRADUAL) {
                context.resolveTagLabel(selectedCoreId!!)
            } else if (forceList || preferListInDetailed) {
                stringResource(R.string.tags_list_title)
            } else {
                stringResource(R.string.tags_wheel_cd)
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // In detailed mode, if not forced to list, allow user to toggle list / wheel view
                if (mode == TagPickerMode.DETAILED && !forceList) {
                    val viewToggleLabel = if (preferListInDetailed) {
                        stringResource(R.string.tags_wheel_cd)
                    } else {
                        stringResource(R.string.tags_list_title)
                    }
                    TextButton(
                        onClick = { preferListInDetailed = !preferListInDetailed },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(viewToggleLabel)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                val modeLabel = if (mode == TagPickerMode.GRADUAL) {
                    stringResource(R.string.tags_mode_gradual)
                } else {
                    stringResource(R.string.tags_mode_detailed)
                }
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
                        onClick = { toggleTag(activeCore.id) },
                        label = { Text(justCoreLabel) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )

                    activeCore.fine.forEach { fineId ->
                        val fineLabel = context.resolveTagLabel(fineId)
                        val isFineSelected = fineId in selectedTags

                        FilterChip(
                            selected = isFineSelected,
                            onClick = { toggleTag(fineId) },
                            label = { Text(fineLabel) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        } else {
            // Detailed Mode
            if (forceList || preferListInDetailed) {
                // Accessible List View
                EmotionListPicker(
                    selectedTags = selectedTags,
                    onTagToggled = { toggleTag(it) },
                    catalog = catalog,
                )
            } else {
                // Wheel Canvas View
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmotionWheel(
                        selectedTags = selectedTags,
                        onTagToggled = { toggleTag(it) },
                        catalog = catalog,
                    )
                }
            }
        }
    }
}
