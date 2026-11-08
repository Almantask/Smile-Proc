package com.example.smileproc.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.Entry
import com.example.smileproc.core.LatLon
import com.example.smileproc.core.PlaceKind
import com.example.smileproc.core.PlaceLabel
import com.example.smileproc.core.PlaceResolver
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaceNamingSheet(
    centroid: LatLon,
    existingLabels: List<PlaceLabel>,
    onSaveLabel: (PlaceLabel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialLabel: PlaceLabel? = null,
    clusterMemberPoints: List<LatLon> = emptyList(),
    allEntries: List<Entry> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var nameInput by rememberSaveable { mutableStateOf(initialLabel?.name.orEmpty()) }
    var selectedKind by rememberSaveable { mutableStateOf(initialLabel?.kind ?: PlaceKind.OTHER) }
    var pendingMergeCandidate by remember { mutableStateOf<PlaceLabel?>(null) }

    val homeLabel = stringResource(R.string.place_kind_home)
    val workLabel = stringResource(R.string.place_kind_work)
    val otherLabel = stringResource(R.string.place_kind_other)
    val saveActionLabel = stringResource(R.string.action_save)
    val cancelLabel = stringResource(R.string.action_cancel)

    fun dismissSheet() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    fun saveLabelDirectly() {
        val trimmed = nameInput.trim()
        if (trimmed.isEmpty()) return
        val labelToSave = PlaceLabel(
            id = initialLabel?.id ?: UUID.randomUUID().toString(),
            name = trimmed,
            kind = selectedKind,
            anchorLatitude = initialLabel?.anchorLatitude ?: centroid.latitude,
            anchorLongitude = initialLabel?.anchorLongitude ?: centroid.longitude,
            createdAt = initialLabel?.createdAt ?: Instant.now(),
        )
        onSaveLabel(labelToSave)
        dismissSheet()
    }

    fun checkMergeAndSave() {
        val trimmed = nameInput.trim()
        if (trimmed.isEmpty()) return

        // When creating a new place (not editing an existing one), check if centroid is within 100m of an existing label
        if (initialLabel == null) {
            val config = PatternConfigCatalog.get(context)
            val candidate = PlaceResolver.findMergeCandidate(centroid, existingLabels, config)
            if (candidate != null) {
                pendingMergeCandidate = candidate
                return
            }
        }
        saveLabelDirectly()
    }

    fun mergeIntoCandidate(candidate: PlaceLabel) {
        val config = PatternConfigCatalog.get(context)
        val existingMemberPoints = allEntries
            .filter { entry ->
                val loc = entry.location
                loc != null &&
                    loc.horizontalAccuracy <= config.placeMaxAccuracyMeters &&
                    PlaceResolver.findMergeCandidate(
                        LatLon(loc.latitude, loc.longitude),
                        listOf(candidate),
                        config,
                    ) != null
            }
            .map { LatLon(it.location!!.latitude, it.location!!.longitude) }

        val newPoints = clusterMemberPoints.ifEmpty { listOf(centroid) }
        val mergedAnchor = PlaceResolver.mergedAnchor(candidate, existingMemberPoints, newPoints)
        val updatedLabel = candidate.copy(
            anchorLatitude = mergedAnchor.latitude,
            anchorLongitude = mergedAnchor.longitude,
        )
        onSaveLabel(updatedLabel)
        pendingMergeCandidate = null
        dismissSheet()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val titleText = if (initialLabel != null) {
                stringResource(R.string.places_rename)
            } else {
                stringResource(R.string.place_name_title)
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Quick suggestion chips: Home and Work
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = nameInput == homeLabel && selectedKind == PlaceKind.HOME,
                    onClick = {
                        nameInput = homeLabel
                        selectedKind = PlaceKind.HOME
                    },
                    label = { Text(homeLabel) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = nameInput == workLabel && selectedKind == PlaceKind.WORK,
                    onClick = {
                        nameInput = workLabel
                        selectedKind = PlaceKind.WORK
                    },
                    label = { Text(workLabel) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text(stringResource(R.string.place_name_custom_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Kind selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.places_change_kind),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedKind == PlaceKind.HOME,
                        onClick = { selectedKind = PlaceKind.HOME },
                        label = { Text(homeLabel) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    FilterChip(
                        selected = selectedKind == PlaceKind.WORK,
                        onClick = { selectedKind = PlaceKind.WORK },
                        label = { Text(workLabel) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    FilterChip(
                        selected = selectedKind == PlaceKind.OTHER,
                        onClick = { selectedKind = PlaceKind.OTHER },
                        label = { Text(otherLabel) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { dismissSheet() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(cancelLabel)
                }
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Button(
                    onClick = { checkMergeAndSave() },
                    enabled = nameInput.trim().isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(saveActionLabel)
                }
            }
        }
    }

    // Merge Confirmation Dialog
    pendingMergeCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = {
                // Clicking outside or dismiss saves as separate place
                saveLabelDirectly()
                pendingMergeCandidate = null
            },
            title = {
                Text(stringResource(R.string.place_name_merge, candidate.name))
            },
            confirmButton = {
                TextButton(
                    onClick = { mergeIntoCandidate(candidate) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // User chose "No" -> save as separate new place
                        pendingMergeCandidate = null
                        saveLabelDirectly()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_no))
                }
            },
        )
    }
}
