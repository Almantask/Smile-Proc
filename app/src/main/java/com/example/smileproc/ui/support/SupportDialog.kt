package com.example.smileproc.ui.support

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.smileproc.R
import com.example.smileproc.core.SupportCatalog
import com.example.smileproc.core.SupportContact
import java.util.Locale

object SupportContactsHelper {
    fun loadVerifiedContacts(context: Context): List<SupportContact> {
        return try {
            val jsonStr = context.assets.open("support.json").bufferedReader().use { it.readText() }
            val catalog = SupportCatalog.fromJson(jsonStr)
            val country = Locale.getDefault().country
            catalog.contactsForRegion(country, includeUnverified = false)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Composable
fun SupportDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val contacts = remember(context) { SupportContactsHelper.loadVerifiedContacts(context) }
    val closeLabel = stringResource(R.string.action_close)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.support_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.support_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = "If you're in danger right now, contact your local emergency number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (contacts.isNotEmpty()) {
                    contacts.forEach { contact ->
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            contact.phone?.let { phone ->
                                val callLabel = stringResource(R.string.support_contact_call, contact.name)
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .semantics { contentDescription = callLabel },
                                ) {
                                    Text(callLabel)
                                }
                            }
                            contact.sms?.let { sms ->
                                val textLabel = stringResource(R.string.support_contact_text, contact.name)
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_SENDTO, "smsto:$sms".toUri()))
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .semantics { contentDescription = textLabel },
                                ) {
                                    Text(textLabel)
                                }
                            }
                            contact.url?.let { url ->
                                val webLabel = stringResource(R.string.support_contact_website, contact.name)
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .semantics { contentDescription = webLabel },
                                ) {
                                    Text(webLabel)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = closeLabel },
            ) {
                Text(closeLabel)
            }
        },
        modifier = modifier,
    )
}
