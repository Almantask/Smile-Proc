package com.example.smileproc.ui.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.smileproc.R
import com.example.smileproc.ui.theme.LocalSmileProcColors

/**
 * Confirmation dialog for "Delete all data"
 * (`docs/spec/android.md` › Delete all).
 */
@Composable
fun DeleteAllDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val smileColors = LocalSmileProcColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = smileColors.dialogContainer,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.delete_all_title)) },
        text = { Text(stringResource(R.string.delete_all_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shape = CircleShape,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.delete_all_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = CircleShape) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
