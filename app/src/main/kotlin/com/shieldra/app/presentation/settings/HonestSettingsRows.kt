package com.shieldra.app.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import com.shieldra.app.presentation.model.SettingsRowUiModel
import com.shieldra.app.settings.AppLanguage
import com.shieldra.app.settings.ThemeMode
import com.shieldra.notification.NotificationPermissionState
import com.shieldra.storage.EvidenceRetentionPolicy

/**
 * The Settings rows this build can honestly show.
 *
 * Every subtitle comes from a fact passed in by the caller — whether storage was
 * really installed, what the platform really answered about notifications, and the
 * owner's own stored choices (theme, language, app lock) — never from a demo table.
 * That replaced the row that used to advertise delivery channels the app does not
 * have (Batch E finding 4).
 *
 * [retentionPolicy] is the policy the open storage's sweeper actually runs with, not the compiled-in
 * default: the retention row states a window, and an owner who changed the setting must not be shown
 * the shipped numbers beside a sweep expiring at their own.
 */
@Composable
fun honestSettingsRows(
    storageInstalled: Boolean,
    notificationState: NotificationPermissionState,
    alertChannelsActive: Boolean = false,
    protectionArmed: Boolean = false,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    language: AppLanguage = AppLanguage.SYSTEM,
    appLockEnabled: Boolean = false,
    retentionPolicy: EvidenceRetentionPolicy = EvidenceRetentionPolicy.default,
): List<SettingsRowUiModel> = SettingsContent.rows.map { spec ->
    SettingsRowUiModel(
        id = spec.id,
        title = stringResource(spec.titleRes),
        subtitle = when {
            // The one row whose wording carries numbers: read them off the policy in force.
            spec.subtitle == SettingsSubtitleKind.RetentionWindows && storageInstalled ->
                stringResource(
                    R.string.settings_retention_windows,
                    retentionPolicy.eventMetadataRetentionDays.toInt(),
                    retentionPolicy.evidenceRetentionDays.toInt(),
                )
            else -> settingsSubtitleRes(
                spec.subtitle, storageInstalled, notificationState, alertChannelsActive, protectionArmed,
                themeMode, language, appLockEnabled,
            )?.let { stringResource(it) }
        },
        icon = spec.icon,
        requiresAuth = spec.requiresAuth,
        section = spec.section,
    )
}
