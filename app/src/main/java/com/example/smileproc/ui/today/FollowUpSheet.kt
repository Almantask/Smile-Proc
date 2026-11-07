package com.example.smileproc.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.Entry
import com.example.smileproc.data.TagPickerMode
import com.example.smileproc.ui.tags.GradualTagPicker

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
) {
    var selectedTags by rememberSaveable(entry.id) { mutableStateOf(entry.tags) }
    var noteText by rememberSaveable(entry.id) { mutableStateOf(entry.comment.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = {
            val trimmed = noteText.trim().ifEmpty { null }
            onSaveDetails(selectedTags, trimmed)
            onDismiss()
        },
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

            GradualTagPicker(
                selectedTags = selectedTags,
                onTagsChanged = { selectedTags = it },
                mode = tagPickerMode,
                onModeToggle = onTagPickerModeToggle,
            )

            OutlinedTextField(
                value = noteText,
                onValueChange = { if (it.length <= 1000) noteText = it },
                label = { Text(stringResource(R.string.today_comment_label)) },
                placeholder = { Text(stringResource(R.string.today_comment_hint)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )

            val doneLabel = stringResource(R.string.action_done)
            Button(
                onClick = {
                    val trimmed = noteText.trim().ifEmpty { null }
                    onSaveDetails(selectedTags, trimmed)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = doneLabel },
            ) {
                Text(doneLabel)
            }
        }
    }
}
