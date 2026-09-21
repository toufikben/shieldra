package com.shieldra.app.design.components.foundation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.tokens.ShieldraCardShape

enum class ShieldraSnackbarKind { Success, Error, Info }

class ShieldraSnackbarVisuals(
    override val message: String,
    val kind: ShieldraSnackbarKind = ShieldraSnackbarKind.Info,
    private val actionText: String? = null,
    override val withDismissAction: Boolean = actionText != null,
    override val duration: SnackbarDuration =
        if (actionText != null) SnackbarDuration.Long else SnackbarDuration.Short,
) : SnackbarVisuals {
    override val actionLabel: String?
        get() = actionText
}

@Composable
fun ShieldraSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data -> ShieldraSnackbar(data) },
    )
}

@Composable
private fun ShieldraSnackbar(data: SnackbarData) {
    Snackbar(
        shape = ShieldraCardShape,
        containerColor = when ((data.visuals as? ShieldraSnackbarVisuals)?.kind) {
            ShieldraSnackbarKind.Success -> MaterialTheme.colorScheme.primaryContainer
            ShieldraSnackbarKind.Error -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when ((data.visuals as? ShieldraSnackbarVisuals)?.kind) {
            ShieldraSnackbarKind.Success -> MaterialTheme.colorScheme.onPrimaryContainer
            ShieldraSnackbarKind.Error -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            val label = data.visuals.actionLabel
            if (label != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { data.performAction() }) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
