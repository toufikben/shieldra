package com.shieldra.app.presentation.settings

import androidx.annotation.StringRes
import com.shieldra.app.R
import com.shieldra.app.presentation.model.SettingsIcon
import com.shieldra.app.presentation.model.SettingsSection
import com.shieldra.notification.NotificationPermissionState

/**
 * What a row's subtitle line is allowed to report.
 *
 * Every case is a fact the app can actually read. There is no case for a
 * delivery channel or a made-up event/byte count, which is exactly what the
 * Settings screen used to print (Batch E findings 4 and 5).
 */
enum class SettingsSubtitleKind {
    GuardsSummary,
    NotificationState,
    AuthenticationRequired,
    MatchSystem,
    StorageState,
    RetentionPending,
    None,
}

/** A Settings row's textual and structural facts, decided outside Compose. */
data class SettingsRowSpec(
    val id: String,
    @StringRes val titleRes: Int,
    val icon: SettingsIcon,
    val section: SettingsSection,
    val requiresAuth: Boolean = false,
    val subtitle: SettingsSubtitleKind = SettingsSubtitleKind.None,
)

/**
 * What a Settings row is genuinely able to do.
 *
 * [NotAvailable] is not a hidden feature: the row stays visible and the app says
 * plainly that the action is deferred. What it must not do is navigate somewhere
 * that implies a capability which does not exist.
 */
enum class SettingsRowTarget {
    NotificationSetup,
    NotAvailable,
}

/**
 * The pure decisions behind the Settings screen and the dashboard disclosure line.
 *
 * Which rows exist, where each sits, what each may print, and what it can do — all
 * decided without Android so the honesty is unit-testable. The wording is a
 * resource id, and the static review script guards the wording itself.
 */
object SettingsContent {

    /** The one Settings row with a real destination in this build. */
    const val NOTIFICATION_ROW_ID: String = "notifications"

    /** The storage row shows a state word, never figures the app cannot read. */
    val storageRowShowsFigures: Boolean = false

    enum class StorageRowState {
        /** Room is installed and real events are being persisted on this device. */
        InUse,

        /** The storage gate installed nothing: there is no local data to describe. */
        NotInstalled,
    }

    /**
     * The disclosure line under the shield. Both values describe facts the app can
     * prove, and neither may suggest monitoring (directive F1.2, §31).
     */
    enum class DashboardMode {
        NoStorageYet,
        LocalEventsOnly;

        val claimsMonitoring: Boolean get() = false
    }

    /**
     * The rows the screen really has, in the order it really shows them. A row with
     * no destination stays visible and honestly labelled rather than dressed up as
     * a feature that exists.
     */
    val rows: List<SettingsRowSpec> = listOf(
        SettingsRowSpec(
            "protection",
            R.string.settings_protection,
            SettingsIcon.Security,
            SettingsSection.Security,
            subtitle = SettingsSubtitleKind.GuardsSummary,
        ),
        SettingsRowSpec(
            NOTIFICATION_ROW_ID,
            R.string.notifications,
            SettingsIcon.Notifications,
            SettingsSection.Notifications,
            subtitle = SettingsSubtitleKind.NotificationState,
        ),
        SettingsRowSpec(
            "app_lock",
            R.string.settings_app_lock,
            SettingsIcon.Lock,
            SettingsSection.SecurityApp,
            requiresAuth = true,
            subtitle = SettingsSubtitleKind.AuthenticationRequired,
        ),
        SettingsRowSpec(
            "theme",
            R.string.settings_theme,
            SettingsIcon.Theme,
            SettingsSection.Appearance,
            subtitle = SettingsSubtitleKind.MatchSystem,
        ),
        SettingsRowSpec("language", R.string.settings_language, SettingsIcon.Language, SettingsSection.Appearance),
        SettingsRowSpec(
            "storage",
            R.string.settings_storage,
            SettingsIcon.Storage,
            SettingsSection.PrivacyData,
            subtitle = SettingsSubtitleKind.StorageState,
        ),
        SettingsRowSpec(
            "retention",
            R.string.settings_retention,
            SettingsIcon.Retention,
            SettingsSection.PrivacyData,
            requiresAuth = true,
            subtitle = SettingsSubtitleKind.RetentionPending,
        ),
        SettingsRowSpec("about", R.string.settings_about_shieldra, SettingsIcon.Info, SettingsSection.About),
        SettingsRowSpec("help", R.string.settings_help, SettingsIcon.Help, SettingsSection.About),
    )

    fun rowIds(): List<String> = rows.map { it.id }

    fun targetOf(id: String): SettingsRowTarget = when (id) {
        NOTIFICATION_ROW_ID -> SettingsRowTarget.NotificationSetup
        else -> SettingsRowTarget.NotAvailable
    }

    fun storageRowState(storageInstalled: Boolean): StorageRowState =
        if (storageInstalled) StorageRowState.InUse else StorageRowState.NotInstalled

    fun dashboardMode(storageInstalled: Boolean): DashboardMode =
        if (storageInstalled) DashboardMode.LocalEventsOnly else DashboardMode.NoStorageYet
}

/** The line under the shield: what this build really does, never what it might. */
@StringRes
internal fun disclosureLabelRes(mode: SettingsContent.DashboardMode): Int = when (mode) {
    SettingsContent.DashboardMode.NoStorageYet -> R.string.dashboard_no_storage_disclosure
    SettingsContent.DashboardMode.LocalEventsOnly -> R.string.dashboard_events_disclosure
}

/** The storage row names the state only; a figure would have to be invented. */
@StringRes
internal fun storageRowSubtitleRes(state: SettingsContent.StorageRowState): Int = when (state) {
    SettingsContent.StorageRowState.NotInstalled -> R.string.settings_storage_none
    SettingsContent.StorageRowState.InUse -> R.string.settings_storage_in_use
}

/** The notification row carries the live A-6 permission state as its subtitle. */
@StringRes
internal fun notificationStateRes(state: NotificationPermissionState): Int = when (state) {
    NotificationPermissionState.NotRequired -> R.string.notification_permission_not_required
    NotificationPermissionState.NotRequested -> R.string.notification_permission_not_requested
    NotificationPermissionState.Granted -> R.string.notification_permission_allowed
    NotificationPermissionState.Denied -> R.string.notification_permission_denied
}

/** Subtitle wording for a row's declared kind, or null when it has nothing to say. */
@StringRes
internal fun settingsSubtitleRes(
    kind: SettingsSubtitleKind,
    storageInstalled: Boolean,
    notificationState: NotificationPermissionState,
): Int? = when (kind) {
    SettingsSubtitleKind.None -> null
    SettingsSubtitleKind.GuardsSummary -> R.string.settings_guards_summary
    SettingsSubtitleKind.AuthenticationRequired -> R.string.settings_require_authentication
    SettingsSubtitleKind.MatchSystem -> R.string.settings_match_system
    SettingsSubtitleKind.RetentionPending -> R.string.settings_retention_pending
    SettingsSubtitleKind.NotificationState -> notificationStateRes(notificationState)
    SettingsSubtitleKind.StorageState ->
        storageRowSubtitleRes(SettingsContent.storageRowState(storageInstalled))
}
