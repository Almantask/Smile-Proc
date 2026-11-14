package com.example.smileproc.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.ui.components.GlassCard
import com.example.smileproc.ui.components.SmileProcBackground
import com.example.smileproc.ui.theme.LocalSmileProcColors
import com.example.smileproc.ui.theme.SmileProcTheme

/**
 * Shown when an encrypted database is present on disk while its Android Keystore key is missing or
 * unusable (`docs/spec/android.md` › Storage and encryption).
 *
 * Offers *Import an export* (wired in M9) and *Start fresh* (behind an explicit confirmation).
 */
@Composable
fun RecoveryScreen(
    onImportExport: () -> Unit,
    onStartFresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStartFreshConfirm by rememberSaveable { mutableStateOf(false) }

    val importLabel = stringResource(R.string.recovery_import)
    val startFreshLabel = stringResource(R.string.recovery_start_fresh)
    val cancelLabel = stringResource(R.string.action_cancel)
    val colors = LocalSmileProcColors.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier = Modifier.widthIn(max = 480.dp),
            highlighted = true,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.recovery_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.recovery_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onImportExport,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .semantics { contentDescription = importLabel },
                ) {
                    Text(
                        text = importLabel,
                        textAlign = TextAlign.Center,
                    )
                }
                OutlinedButton(
                    onClick = { showStartFreshConfirm = true },
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .semantics { contentDescription = startFreshLabel },
                ) {
                    Text(
                        text = startFreshLabel,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (showStartFreshConfirm) {
        AlertDialog(
            onDismissRequest = { showStartFreshConfirm = false },
            containerColor = colors.dialogContainer,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(text = startFreshLabel)
            },
            text = {
                Text(
                    text = stringResource(R.string.recovery_start_fresh_confirm),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStartFreshConfirm = false
                        onStartFresh()
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = startFreshLabel },
                ) {
                    Text(text = startFreshLabel)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStartFreshConfirm = false },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = cancelLabel },
                ) {
                    Text(text = cancelLabel)
                }
            },
        )
    }
}

@Preview(name = "Recovery - Light", showBackground = true)
@Preview(
    name = "Recovery - Dark 200% font",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    fontScale = 2.0f,
)
@Composable
private fun RecoveryScreenPreview() {
    SmileProcTheme {
        SmileProcBackground {
            RecoveryScreen(
                onImportExport = {},
                onStartFresh = {},
            )
        }
    }
}
