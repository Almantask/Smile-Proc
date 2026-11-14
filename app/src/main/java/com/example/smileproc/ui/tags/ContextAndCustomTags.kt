package com.example.smileproc.ui.tags

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.BuiltInContextTag
import com.example.smileproc.core.CustomTag
import com.example.smileproc.ui.theme.LocalSmileProcColors
import java.time.Instant

@StringRes
fun notePromptStringRes(entryId: String): Int = when (Math.floorMod(entryId.hashCode(), 4)) {
    0 -> R.string.note_prompt_1
    1 -> R.string.note_prompt_2
    2 -> R.string.note_prompt_3
    else -> R.string.note_prompt_4
}

@Composable
fun BookmarkMomentToggle(
    bookmarked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val smileColors = LocalSmileProcColors.current
    val label = if (bookmarked) {
        stringResource(R.string.followup_bookmarked_label)
    } else {
        stringResource(R.string.followup_bookmark_label)
    }
    FilterChip(
        selected = bookmarked,
        onClick = onToggle,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(
                    if (bookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline,
                ),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = smileColors.chipFill,
            selectedContainerColor = smileColors.brandAccent.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = smileColors.brandAccent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = bookmarked,
            borderColor = smileColors.chipBorder,
            selectedBorderColor = smileColors.brandAccent,
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = label },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContextAndCustomTagsSection(
    selectedContextTags: List<String>,
    onContextTagsChanged: (List<String>) -> Unit,
    availableCustomTags: List<CustomTag>,
    selectedCustomTagIds: List<String>,
    onCustomTagIdsChanged: (List<String>) -> Unit,
    onCreateCustomTag: ((CustomTag) -> Unit)? = null,
    onDeleteCustomTag: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val smileColors = LocalSmileProcColors.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf<CustomTag?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.followup_context_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BuiltInContextTag.entries.forEach { tag ->
                val isSelected = tag.id in selectedContextTags
                val label = context.resolveContextTagLabel(tag.id)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val updated = if (isSelected) {
                            selectedContextTags - tag.id
                        } else {
                            selectedContextTags + tag.id
                        }
                        onContextTagsChanged(updated)
                    },
                    label = { Text(label) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = smileColors.chipFill,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = smileColors.chipBorder,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        if (onCreateCustomTag != null || availableCustomTags.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.followup_custom_tags_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (onCreateCustomTag != null) {
                    val canAddMore = availableCustomTags.size < CustomTag.MAX_ACTIVE_TAGS
                    TextButton(
                        onClick = { if (canAddMore) showCreateDialog = true },
                        enabled = canAddMore,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = CircleShape,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.followup_custom_tag_add))
                    }
                }
            }

            if (availableCustomTags.size >= CustomTag.MAX_ACTIVE_TAGS) {
                Text(
                    text = stringResource(R.string.followup_custom_tag_max_reached, CustomTag.MAX_ACTIVE_TAGS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (availableCustomTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableCustomTags.forEach { customTag ->
                        val isSelected = customTag.id in selectedCustomTagIds
                        val removeDesc = stringResource(R.string.followup_custom_tag_delete, customTag.label)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val updated = if (isSelected) {
                                    selectedCustomTagIds - customTag.id
                                } else {
                                    selectedCustomTagIds + customTag.id
                                }
                                onCustomTagIdsChanged(updated)
                            },
                            label = { Text(customTag.label) },
                            trailingIcon = if (onDeleteCustomTag != null) {
                                {
                                    IconButton(
                                        onClick = { tagToDelete = customTag },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .semantics { contentDescription = removeDesc },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = removeDesc,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = smileColors.chipFill,
                                selectedContainerColor = smileColors.brandAccent.copy(alpha = 0.22f),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = smileColors.chipBorder,
                                selectedBorderColor = smileColors.brandAccent,
                            ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog && onCreateCustomTag != null) {
        var newLabel by remember { mutableStateOf("") }
        val normalizedPreview = CustomTag.normalizeLabel(newLabel)
        val isDuplicate = availableCustomTags.any {
            it.label.equals(normalizedPreview, ignoreCase = true)
        }
        val canSave = normalizedPreview.isNotEmpty() && !isDuplicate
        val saveText = stringResource(R.string.action_save)
        val cancelText = stringResource(R.string.action_cancel)

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = smileColors.dialogContainer,
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.followup_custom_tag_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { if (it.length <= CustomTag.MAX_LABEL_LENGTH) newLabel = it },
                    placeholder = { Text(stringResource(R.string.followup_custom_tag_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val created = CustomTag.create(normalizedPreview, Instant.now())
                        onCreateCustomTag(created)
                        if (created.id !in selectedCustomTagIds) {
                            onCustomTagIdsChanged(selectedCustomTagIds + created.id)
                        }
                        showCreateDialog = false
                    },
                    enabled = canSave,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(saveText)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(cancelText)
                }
            },
        )
    }

    val pendingDelete = tagToDelete
    if (pendingDelete != null && onDeleteCustomTag != null) {
        val deleteLabel = stringResource(R.string.action_delete)
        val cancelLabel = stringResource(R.string.action_cancel)
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            containerColor = smileColors.dialogContainer,
            shape = RoundedCornerShape(28.dp),
            title = { Text(pendingDelete.label) },
            text = { Text(stringResource(R.string.followup_custom_tag_delete, pendingDelete.label)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustomTag(pendingDelete.id)
                        onCustomTagIdsChanged(selectedCustomTagIds - pendingDelete.id)
                        tagToDelete = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(deleteLabel, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { tagToDelete = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(cancelLabel)
                }
            },
        )
    }
}
