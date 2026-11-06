package com.shieldra.app.presentation.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.shieldra.app.R
import com.shieldra.app.presentation.auth.OwnerCheck
import com.shieldra.app.presentation.auth.OwnerCheckResult
import com.shieldra.app.presentation.model.EventDetailUiModel
import com.shieldra.app.presentation.model.EvidenceItemUi
import com.shieldra.storage.EvidenceExportPolicy
import com.shieldra.storage.EvidenceExportRedaction
import com.shieldra.storage.EvidenceExporter
import com.shieldra.storage.ExportAuditLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The evidence-export flow (§5.5, amendment A-37) — the one production file allowed to create a
 * document (the F-K3 egress gate is confined to files that name [EvidenceExportPolicy]).
 *
 * Every export goes: explicit tap -> passphrase dialog -> the platform screen-lock check
 * ([OwnerCheck]) -> [EvidenceExporter] (redaction, in-memory zip, AES-256-GCM seal, audit) -> the
 * system "save as" picker. The policy returned by the exporter is checked again here before a byte
 * is handed to the picker, and the plaintext archive never exists outside memory.
 */
class EvidenceExportFlow internal constructor(val begin: (EventDetailUiModel) -> Unit)

@Composable
fun rememberEvidenceExportFlow(
    readEvidence: (evidenceId: String) -> ByteArray?,
    auditLog: ExportAuditLog?,
    activity: FragmentActivity?,
    onMessage: (String) -> Unit,
): EvidenceExportFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<EventDetailUiModel?>(null) }
    var sealedArchive by remember { mutableStateOf<ByteArray?>(null) }
    // Written-to-unreadable counts of the archive the picker is holding (G-10). Kept beside the
    // bytes so the final message can say what actually made it into the file.
    var pendingCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val doneMessage = stringResource(R.string.export_done)
    val failedMessage = stringResource(R.string.export_failed)
    val refusedMessage = stringResource(R.string.export_refused)
    val noEvidenceMessage = stringResource(R.string.export_no_evidence)
    val authTitle = stringResource(R.string.export_auth_title)
    val authSubtitle = stringResource(R.string.export_auth_subtitle)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bytes = sealedArchive
        sealedArchive = null
        val counts = pendingCounts
        pendingCounts = null
        if (uri != null && bytes != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
            }.getOrDefault(false)
            val unreadable = counts?.second ?: 0
            onMessage(
                when {
                    !saved -> failedMessage
                    unreadable > 0 -> context.getString(
                        R.string.export_done_partial,
                        counts?.first ?: 0,
                        (counts?.first ?: 0) + unreadable,
                        unreadable,
                    )
                    else -> doneMessage
                },
            )
        }
    }

    pending?.let { detail ->
        ExportDialog(
            onDismiss = { pending = null },
            onConfirm = { passphrase, includeLocation ->
                pending = null
                val runExport = {
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            runCatching {
                                val exporter = EvidenceExporter(
                                    readEvidence = readEvidence,
                                    recordAudit = { auditLog?.append(it) },
                                )
                                exporter.export(
                                    items = detail.evidenceItems.map { it.toExportItem() },
                                    includeLocation = includeLocation,
                                    passphrase = passphrase,
                                    nowMs = System.currentTimeMillis(),
                                    authenticated = true,
                                    explicitAction = true,
                                )
                            }
                        }
                        passphrase.fill('\u0000')
                        val result = outcome.getOrNull()
                        val policy: EvidenceExportPolicy? = result?.policy
                        when {
                            result == null || policy?.isAllowed != true -> onMessage(failedMessage)
                            // G-10: every allowed file was gone, so the old flow sealed an empty zip
                            // and reported "export saved". Say that nothing was readable instead.
                            result.includedCount == 0 -> onMessage(noEvidenceMessage)
                            else -> {
                                sealedArchive = result.encryptedArchive
                                pendingCounts = result.includedCount to result.unreadableCount
                                saveLauncher.launch("shieldra-evidence-${detail.id.take(8)}.shx")
                            }
                        }
                    }
                    Unit
                }
                if (activity == null) {
                    passphrase.fill('\u0000')
                    onMessage(refusedMessage)
                } else {
                    OwnerCheck.request(activity, authTitle, authSubtitle) { check ->
                        when (check) {
                            OwnerCheckResult.Confirmed, OwnerCheckResult.NoScreenLock -> runExport()
                            OwnerCheckResult.Refused -> {
                                passphrase.fill('\u0000')
                                onMessage(refusedMessage)
                            }
                        }
                    }
                }
            },
        )
    }

    return remember { EvidenceExportFlow(begin = { pending = it }) }
}

private fun EvidenceItemUi.toExportItem(): EvidenceExporter.Item = EvidenceExporter.Item(
    evidenceId = id,
    redactionItem = when {
        kind.contains("PHOTO", ignoreCase = true) -> EvidenceExportRedaction.Item.PHOTO
        kind.equals("AUDIO", ignoreCase = true) -> EvidenceExportRedaction.Item.AUDIO
        kind.equals("LOCATION", ignoreCase = true) -> EvidenceExportRedaction.Item.LOCATION
        else -> EvidenceExportRedaction.Item.EVENT_METADATA
    },
)

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onConfirm: (passphrase: CharArray, includeLocation: Boolean) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var includeLocation by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.export_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.export_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.clickable { includeLocation = !includeLocation },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeLocation, onCheckedChange = { includeLocation = it })
                    Text(stringResource(R.string.export_include_location))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase.toCharArray(), includeLocation) },
                enabled = passphrase.length >= MIN_PASSPHRASE,
            ) { Text(stringResource(R.string.export_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val MIN_PASSPHRASE = 8
