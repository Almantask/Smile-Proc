package com.example.smileproc.ui.tags

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.smileproc.core.EmotionTags
import com.example.smileproc.data.TagPickerMode

/**
 * Backward-compatible alias for [TagPicker].
 */
@Composable
fun GradualTagPicker(
    selectedTags: List<String>,
    onTagsChanged: (List<String>) -> Unit,
    mode: TagPickerMode,
    onModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
    catalog: EmotionTags = EmotionTagsCatalog.get(LocalContext.current),
) {
    TagPicker(
        selectedTags = selectedTags,
        onTagsChanged = onTagsChanged,
        mode = mode,
        onModeToggle = onModeToggle,
        modifier = modifier,
        catalog = catalog,
    )
}
