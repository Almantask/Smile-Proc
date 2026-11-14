package com.example.smileproc.ui.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.smileproc.ui.theme.LocalSmileProcColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.smileproc.R

/**
 * Password dialog for decrypting `.smileproc` imports
 * (`docs/spec/android.md` › Export and import).
 */
@Composable
fun ImportPasswordDialog(
    isImporting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    val smileColors = LocalSmileProcColors.current
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        containerColor = smileColors.dialogContainer,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.import_body),
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (isImporting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = stringResource(R.string.import_in_progress),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.import_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (!isImporting) {
                TextButton(
                    onClick = {
                        if (password.isNotEmpty()) {
                            onConfirm(password.toCharArray())
                        }
                    },
                    enabled = password.isNotEmpty(),
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.import_title))
                }
            }
        },
        dismissButton = {
            if (!isImporting) {
                TextButton(onClick = onDismiss, shape = CircleShape) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}
