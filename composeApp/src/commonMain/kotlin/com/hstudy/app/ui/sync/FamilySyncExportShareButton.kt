package com.hstudy.app.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hstudy.app.i18n.LocalDisplayLanguage
import com.hstudy.app.i18n.SyncDiagnosticAdapter
import com.hstudy.app.platform.ShareResult
import kotlinx.coroutines.launch

/**
 * Shared export/share action for both family-sync screens. Export and sharing are separate facts:
 * a generated file is shown as exported, while the platform result is rendered independently as
 * Presented/Completed/Cancelled/Failed. In particular, Android never claims delivery.
 */
@Composable
internal fun FamilySyncExportShareButton(
    label: String,
    processingLabel: String,
    exportedMessage: String,
    sharePresentedMessage: String,
    shareCompletedMessage: String,
    shareCancelledMessage: String,
    shareFailedMessage: (detail: String?) -> String,
    exportFailedMessage: (detail: String?) -> String,
    onExport: suspend () -> Result<String>,
    onShare: (String, (ShareResult) -> Unit) -> ShareResult,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
    onTransferringChange: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val displayLanguage = LocalDisplayLanguage.current
    var busy by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var shareResult by remember { mutableStateOf<ShareResult?>(null) }
    var shareAttempt by remember { mutableIntStateOf(0) }

    fun localized(raw: String?): String? = raw?.let { SyncDiagnosticAdapter.localize(it, displayLanguage) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val onClick = {
            if (!busy && enabled) {
                val attempt = ++shareAttempt
                scope.launch {
                    busy = true
                    exported = false
                    exportFailed = false
                    exportError = null
                    shareResult = null
                    onTransferringChange(true)
                    try {
                        val result = try {
                            onExport()
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                        result.fold(
                            onSuccess = { path ->
                                exported = true
                                var callbackReceived = false
                                val immediateResult = try {
                                    onShare(path) { result ->
                                        // Only the current attempt may publish a platform callback.
                                        // A late iOS callback from an older share sheet is stale.
                                        if (shareAttempt == attempt) {
                                            callbackReceived = true
                                            shareResult = result
                                        }
                                    }
                                } catch (e: Exception) {
                                    ShareResult.Failed(e.message ?: "Share failed")
                                }
                                // A synchronous terminal callback wins over the immediate return;
                                // otherwise publish the platform's immediate Presented/Failed result.
                                if (shareAttempt == attempt && !callbackReceived) {
                                    shareResult = immediateResult
                                }
                            },
                            onFailure = {
                                exportFailed = true
                                exportError = localized(it.message)
                            },
                        )
                    } finally {
                        busy = false
                        onTransferringChange(false)
                    }
                }
            }
        }
        if (outlined) {
            OutlinedButton(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(label)
            }
        } else {
            Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(label)
            }
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(processingLabel, style = MaterialTheme.typography.labelSmall)
        }
        if (exported) {
            Text(exportedMessage, style = MaterialTheme.typography.labelSmall)
        }
        val statusMessage = when {
            exportFailed -> exportFailedMessage(exportError)
            shareResult is ShareResult.Presented -> sharePresentedMessage
            shareResult is ShareResult.Completed -> shareCompletedMessage
            shareResult is ShareResult.Cancelled -> shareCancelledMessage
            shareResult is ShareResult.Failed -> shareFailedMessage(localized((shareResult as ShareResult.Failed).message))
            else -> null
        }
        statusMessage?.let { message ->
            val failed = exportFailed || shareResult is ShareResult.Failed
            Text(
                message,
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
