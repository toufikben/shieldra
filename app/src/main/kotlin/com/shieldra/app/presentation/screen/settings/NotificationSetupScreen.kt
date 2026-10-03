package com.shieldra.app.presentation.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.components.foundation.ShieldraButtonVariant
import com.shieldra.app.design.components.foundation.ShieldraFullWidthButton
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.notification.LocalPostResult
import com.shieldra.notification.NotificationPermissionState

/**
 * The notification setup screen (amendment A-6).
 *
 * This is the only place SHIELDRA asks for `POST_NOTIFICATIONS`, and it is
 * reached from Settings — never from a Panic press, and never bundled with any
 * other permission (§23). The reason is stated before the request.
 *
 * The state shown is the live one. A refusal is presented as a refusal and the
 * screen says plainly that events are still recorded, because losing the
 * permission costs the notification and nothing else.
 */
@Composable
fun NotificationSetupScreen(
    permissionState: NotificationPermissionState,
    lastResult: LocalPostResult?,
    onRequest: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSystemSettings: () -> Unit = {},
    /** "Cancel" from Settings; "Continue" when the screen is a step of first-run setup. */
    doneLabel: Int = R.string.action_cancel,
) {
    val spacing = ShieldraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = spacing.l, vertical = spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(spacing.l))
        Text(
            text = stringResource(R.string.notifications),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.s))
        Text(
            text = stringResource(R.string.permission_notification_reason),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.l))
        Text(
            text = stringResource(permissionState.labelRes()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        lastResult?.let { result ->
            Spacer(Modifier.height(spacing.s))
            Text(
                text = stringResource(result.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(spacing.xl))
        when (permissionState) {
            NotificationPermissionState.NotRequested -> ShieldraFullWidthButton(
                text = stringResource(R.string.notification_turn_on),
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
            )
            // Android stops showing the dialog after it was refused, so asking again does nothing.
            // The only place the owner can still allow it is the app's own system settings page.
            NotificationPermissionState.Denied -> ShieldraFullWidthButton(
                text = stringResource(R.string.notification_open_settings),
                onClick = onOpenSystemSettings,
                modifier = Modifier.fillMaxWidth(),
            )
            // Already allowed, or nothing to allow on this Android version: no button to press.
            NotificationPermissionState.Granted, NotificationPermissionState.NotRequired -> Unit
        }
        Spacer(Modifier.height(spacing.s))
        ShieldraFullWidthButton(
            text = stringResource(doneLabel),
            onClick = onBack,
            variant = ShieldraButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun NotificationPermissionState.labelRes(): Int = when (this) {
    NotificationPermissionState.NotRequired -> R.string.notification_permission_not_required
    NotificationPermissionState.NotRequested -> R.string.notification_permission_not_requested
    NotificationPermissionState.Granted -> R.string.notification_permission_allowed
    NotificationPermissionState.Denied -> R.string.notification_permission_denied
}

private fun LocalPostResult.labelRes(): Int = when (this) {
    LocalPostResult.NotAttempted -> R.string.notification_result_not_attempted
    LocalPostResult.PostedLocally -> R.string.notification_result_posted
    is LocalPostResult.FailedToPost -> R.string.notification_result_failed
}

@Preview(showBackground = true)
@Composable
private fun NotificationSetupPreview() {
    ShieldraTheme(darkTheme = true) {
        NotificationSetupScreen(
            permissionState = NotificationPermissionState.NotRequested,
            lastResult = null,
            onRequest = {},
            onBack = {},
        )
    }
}
