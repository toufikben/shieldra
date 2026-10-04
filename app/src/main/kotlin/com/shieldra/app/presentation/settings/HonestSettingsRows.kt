package com.shieldra.app.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.presentation.model.SettingsRowUiModel
import com.shieldra.notification.NotificationPermissionState

/**
 * The Settings rows this build can honestly show.
 *
 * Every subtitle comes from a fact passed in by the caller — whether storage was
 * really installed and what the platform really answered about notifications —
 * never from a demo table. That replaced the row that used to advertise delivery
 * channels the app does not have (Batch E finding 4).
 */
@Composable
fun honestSettingsRows(
    storageInstalled: Boolean,
    notificationState: NotificationPermissionState,
    alertChannelsActive: Boolean = false,
): List<SettingsRowUiModel> = SettingsContent.rows.map { spec ->
    SettingsRowUiModel(
        id = spec.id,
        title = stringResource(spec.titleRes),
        subtitle = settingsSubtitleRes(spec.subtitle, storageInstalled, notificationState, alertChannelsActive)
            ?.let { stringResource(it) },
        icon = spec.icon,
        requiresAuth = spec.requiresAuth,
        section = spec.section,
    )
}
