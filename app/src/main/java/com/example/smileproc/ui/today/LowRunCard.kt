package com.example.smileproc.ui.today

import androidx.compose.foundation.shape.CircleShape
import com.example.smileproc.ui.components.GlassCard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.smileproc.R

@Composable
fun LowRunCard(
    onSeeSupport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        highlighted = true,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.lowrun_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val notNowLabel = stringResource(R.string.action_not_now)
                TextButton(
                    onClick = onDismiss,
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = notNowLabel },
                ) {
                    Text(notNowLabel)
                }

                val seeSupportLabel = stringResource(R.string.lowrun_see_support)
                OutlinedButton(
                    onClick = onSeeSupport,
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = seeSupportLabel },
                ) {
                    Text(seeSupportLabel)
                }
            }
        }
    }
}
