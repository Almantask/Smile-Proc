package com.example.smileproc.ui.today

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.smileproc.ui.components.GlassModalBottomSheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.CustomTag
import com.example.smileproc.core.Entry
import com.example.smileproc.core.EntryExtras
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.tags.BookmarkMomentToggle
import com.example.smileproc.ui.tags.ContextAndCustomTagsSection
import com.example.smileproc.ui.tags.TagPicker
import com.example.smileproc.ui.tags.notePromptStringRes
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpSheet(
    entry: Entry,
    tagPickerMode: TagPickerMode,
    onTagPickerModeToggle: () -> Unit,
    onSaveDetails: (tags: List<String>, comment: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    initialExtras: EntryExtras? = null,
    availableCustomTags: List<CustomTag> = emptyList(),
    onSaveExtras: ((EntryExtras) -> Unit)? = null,
    onCreateCustomTag: ((CustomTag) -> Unit)? = null,
    onDeleteCustomTag: ((String) -> Unit)? = null,
) {
    var selectedTags by rememberSaveable(entry.id) { mutableStateOf(entry.tags) }
    var noteText by rememberSaveable(entry.id) { mutableStateOf(entry.comment.orEmpty()) }
    var selectedContextTags by rememberSaveable(entry.id) {
        mutableStateOf(initialExtras?.contextTags.orEmpty())
    }
    var selectedCustomTagIds by rememberSaveable(entry.id) {
        mutableStateOf(initialExtras?.customTagIds.orEmpty())
    }
    var bookmarked by rememberSaveable(entry.id) {
        mutableStateOf(initialExtras?.bookmarked == true)
    }

    fun persistAndClose() {
        val trimmed = noteText.trim().ifEmpty { null }
        onSaveDetails(selectedTags, trimmed)
        onSaveExtras?.invoke(
            EntryExtras(
                entryId = entry.id,
                contextTags = selectedContextTags,
                customTagIds = selectedCustomTagIds,
                bookmarked = bookmarked,
                updatedAt = Instant.now(),
            ),
        )
        onDismiss()
    }

    GlassModalBottomSheet(
        onDismissRequest = { persistAndClose() },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.today_saved_header),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.today_followup_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BookmarkMomentToggle(
                    bookmarked = bookmarked,
                    onToggle = { bookmarked = !bookmarked },
                )
            }

            TagPicker(
                selectedTags = selectedTags,
                onTagsChanged = { selectedTags = it },
                mode = tagPickerMode,
                onModeToggle = onTagPickerModeToggle,
            )

            ContextAndCustomTagsSection(
                selectedContextTags = selectedContextTags,
                onContextTagsChanged = { selectedContextTags = it },
                availableCustomTags = availableCustomTags,
                selectedCustomTagIds = selectedCustomTagIds,
                onCustomTagIdsChanged = { selectedCustomTagIds = it },
                onCreateCustomTag = onCreateCustomTag,
                onDeleteCustomTag = onDeleteCustomTag,
            )

            OutlinedTextField(
                value = noteText,
                onValueChange = { if (it.length <= 1000) noteText = it },
                label = { Text(stringResource(R.string.today_comment_label)) },
                placeholder = { Text(stringResource(notePromptStringRes(entry.id))) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )

            val doneLabel = stringResource(R.string.action_done)
            Button(
                onClick = { persistAndClose() },
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .semantics { contentDescription = doneLabel },
            ) {
                Text(doneLabel)
            }
        }
    }
}
