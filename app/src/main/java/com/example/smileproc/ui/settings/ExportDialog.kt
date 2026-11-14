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
 * Password dialog for encrypted `.smileproc` export
 * (`docs/spec/android.md` › Export and import).
 */
@Composable
fun ExportDialog(
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var passwordAgain by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val smileColors = LocalSmileProcColors.current
    val isMinLength = password.length >= 8
    val isMatching = password == passwordAgain
    val isValid = isMinLength && isMatching

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        containerColor = smileColors.dialogContainer,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.export_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.export_password_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isExporting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = stringResource(R.string.export_in_progress),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            showError = false
                        },
                        label = { Text(stringResource(R.string.export_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        isError = showError && !isMinLength,
                        supportingText = if (showError && !isMinLength) {
                            { Text(stringResource(R.string.export_password_min)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = passwordAgain,
                        onValueChange = {
                            passwordAgain = it
                            showError = false
                        },
                        label = { Text(stringResource(R.string.export_password_again)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        isError = showError && isMinLength && !isMatching,
                        supportingText = if (showError && isMinLength && !isMatching) {
                            { Text(stringResource(R.string.export_password_mismatch)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (!isExporting) {
                TextButton(
                    onClick = {
                        if (isValid) {
                            onConfirm(password.toCharArray())
                        } else {
                            showError = true
                        }
                    },
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.export_title))
                }
            }
        },
        dismissButton = {
            if (!isExporting) {
                TextButton(onClick = onDismiss, shape = CircleShape) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}
