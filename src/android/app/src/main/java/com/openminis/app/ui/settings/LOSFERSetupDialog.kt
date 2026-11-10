package com.openminis.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openminis.app.R
import com.openminis.app.data.repository.ProviderRepository
import com.openminis.app.ui.components.openExternalUrl
import kotlinx.coroutines.launch

/**
 * [LOSFER] One-click setup dialog. A single API-key field plus a short
 * "how to get one" note linking to https://api.losfer.cn/pc/api-list/keys/.
 *
 * Verify & Add first probes the key against GET /v1/models (see
 * [LOSFERSetup.validateKey]); only a 200 passes. On success the key is stored
 * on an OpenAI-protocol instance pointed at https://api.losfer.cn/v1 and the
 * model list is fetched immediately.
 */
@Composable
fun LOSFERSetupDialog(
    repo: ProviderRepository?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(stringResource(R.string.losfer_dialog_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(stringResource(R.string.losfer_dialog_desc), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; error = null },
                    label = { Text(stringResource(R.string.losfer_api_key_label)) },
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.losfer_get_key_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.losfer_get_key_link),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .linkClickable { openExternalUrl(context, LOSFERSetup.KEY_URL) },
                )
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(18.dp)
                        )
                        Text(stringResource(R.string.losfer_adding), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && apiKey.isNotBlank() && repo != null,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val check = LOSFERSetup.validateKey(apiKey)
                        when (check) {
                            is KeyCheck.Valid -> {
                                try {
                                    LOSFERSetup.addToRepository(context, repo!!, apiKey)
                                    busy = false
                                    onDismiss()
                                } catch (e: Exception) {
                                    busy = false
                                    error = context.getString(R.string.losfer_failed_to_add)
                                }
                            }
                            is KeyCheck.Invalid -> {
                                busy = false
                                error = context.getString(R.string.losfer_invalid_key)
                            }
                            is KeyCheck.NetworkError -> {
                                busy = false
                                error = context.getString(R.string.losfer_network_error) + " (${check.message})"
                            }
                        }
                    }
                },
            ) { Text(stringResource(R.string.losfer_validate_and_add)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Tap handler without the ripple/indication — used for the inline link. */
@Composable
private fun Modifier.linkClickable(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    )
