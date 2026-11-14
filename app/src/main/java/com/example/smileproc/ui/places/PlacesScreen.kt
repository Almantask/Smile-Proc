package com.example.smileproc.ui.places

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.ui.graphics.Color
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.components.GlassDivider
import com.example.smileproc.ui.components.GlassTopBar
import com.example.smileproc.ui.theme.LocalSmileProcColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.core.ClusterID
import com.example.smileproc.core.Entry
import com.example.smileproc.core.LatLon
import com.example.smileproc.core.PlaceAssignment
import com.example.smileproc.core.PlaceLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    entries: List<Entry>,
    placeLabels: List<PlaceLabel>,
    places: PlaceAssignment,
    onSavePlaceLabel: (PlaceLabel) -> Unit,
    onDeletePlaceLabel: (String) -> Unit,
    onRemoveLocationFromAllEntries: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var labelToRename by remember { mutableStateOf<PlaceLabel?>(null) }
    var clusterToName by remember { mutableStateOf<Pair<LatLon, List<LatLon>>?>(null) }
    var labelToForget by remember { mutableStateOf<PlaceLabel?>(null) }
    var showRemoveLocationDialog by remember { mutableStateOf(false) }

    val unnamedClusters = remember(places) {
        places.clusters.values.filter { it.id.isUnnamed }
    }
    val hasAnyPlaces = placeLabels.isNotEmpty() || unnamedClusters.isNotEmpty()

    val smileColors = LocalSmileProcColors.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.places_title),
                onNavigateBack = onNavigateBack,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        if (!hasAnyPlaces) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlassCard(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.places_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Named Places Section
                if (placeLabels.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.places_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    placeLabels.forEach { label ->
                        val cluster = places.clusters[ClusterID.named(label.id)]
                        val memberCount = cluster?.memberIDs?.size ?: 0
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label.name,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = pluralStringResource(
                                                R.plurals.place_unnamed_count,
                                                memberCount,
                                                memberCount,
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    SuggestionChip(
                                        onClick = { labelToRename = label },
                                        label = {
                                            Text(PlaceFormatters.formatPlaceKind(context, label.kind))
                                        },
                                        shape = CircleShape,
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = smileColors.chipFill,
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        border = BorderStroke(1.dp, smileColors.chipBorder),
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = { labelToRename = label },
                                        shape = CircleShape,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Text(stringResource(R.string.places_rename))
                                    }
                                    TextButton(
                                        onClick = { labelToForget = label },
                                        shape = CircleShape,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Text(stringResource(R.string.places_forget_name))
                                    }
                                }
                            }
                        }
                    }
                }

                // Unnamed Places Section
                if (unnamedClusters.isNotEmpty()) {
                    if (placeLabels.isNotEmpty()) {
                        GlassDivider()
                    }
                    Text(
                        text = stringResource(R.string.place_unnamed_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    unnamedClusters.forEach { cluster ->
                        val memberEntries = entries.filter { it.id in cluster.memberIDs }
                        val description = PlaceFormatters.formatUnnamedDescription(context, memberEntries)
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = {
                                            val points = memberEntries.mapNotNull { it.location }
                                                .map { LatLon(it.latitude, it.longitude) }
                                            clusterToName = Pair(cluster.centroid, points)
                                        },
                                        shape = CircleShape,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Text(stringResource(R.string.place_name_title))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Danger zone: Remove location from all entries
                OutlinedButton(
                    onClick = { showRemoveLocationDialog = true },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                ) {
                    Text(
                        text = stringResource(R.string.places_remove_location),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    // Naming Sheet for unnamed cluster
    clusterToName?.let { (centroid, points) ->
        PlaceNamingSheet(
            centroid = centroid,
            existingLabels = placeLabels,
            clusterMemberPoints = points,
            allEntries = entries,
            onSaveLabel = { label ->
                onSavePlaceLabel(label)
                clusterToName = null
            },
            onDismiss = { clusterToName = null },
        )
    }

    // Rename Sheet for existing label
    labelToRename?.let { label ->
        PlaceNamingSheet(
            centroid = LatLon(label.anchorLatitude, label.anchorLongitude),
            existingLabels = placeLabels,
            initialLabel = label,
            allEntries = entries,
            onSaveLabel = { updated ->
                onSavePlaceLabel(updated)
                labelToRename = null
            },
            onDismiss = { labelToRename = null },
        )
    }

    // Forget Name Confirmation Dialog
    labelToForget?.let { label ->
        AlertDialog(
            onDismissRequest = { labelToForget = null },
            containerColor = smileColors.dialogContainer,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(stringResource(R.string.places_forget_name))
            },
            text = {
                Text(stringResource(R.string.places_forget_name_confirm, label.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlaceLabel(label.id)
                        labelToForget = null
                    },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { labelToForget = null },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Remove location from all entries Confirmation Dialog
    if (showRemoveLocationDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveLocationDialog = false },
            containerColor = smileColors.dialogContainer,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(stringResource(R.string.places_remove_location_confirm_title))
            },
            text = {
                Text(stringResource(R.string.places_remove_location_confirm_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveLocationFromAllEntries()
                        showRemoveLocationDialog = false
                    },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.places_remove_location_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveLocationDialog = false },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
