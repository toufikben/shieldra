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
    NotificationState,
    AlertChannelsState,
    AppLockNotEnforced,
    MatchSystem,
    StorageState,
    RetentionWindows,
    None,
}

/** A Settings row's textual and structural facts, decided outside Compose. */
data class SettingsRowSpec(
    val id: String,
    @param:StringRes val titleRes: Int,
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
    AlertChannels,
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

    /** A-32: the owner's own Telegram bot and email account, the channels alerts leave through. */
    const val ALERT_CHANNELS_ROW_ID: String = "alert_channels"

    /** The storage row shows a state word, never figures the app cannot read. */
    val storageRowShowsFigures: Boolean = false

    enum class StorageRowState {
        /** Room is installed and real events are being persisted on this device. */
        InUse,

        /** The storage gate installed nothing: there is no local data to describe. */
        NotInstalled,
    }

    /**
     * The disclosure line under the shield. Every value describes a fact the app can prove: the
     * storage half comes from the installed gate, and [DashboardMode.LocalEventsWithMotion] is the
     * one case where a running monitor publishes its own state (directive F1.2, §31, A-19).
     */
    enum class DashboardMode {
        NoStorageYet,
        LocalEventsOnly,
        LocalEventsWithMotion;

        val claimsMonitoring: Boolean get() = this == LocalEventsWithMotion
    }

    /**
     * The rows the screen really has, in the order it really shows them. A row that reports a real
     * state stays visible and honestly labelled even when it opens nothing (the retention row), but
     * a row that reads as a capability the owner could switch on is deleted: Q-K-9 removed the
     * "Protection" row and its "Lock Guard · Guards" subtitle, because no Lock Guard runs in this
     * build and a setting that offers a guard the owner cannot enable is a claim, not a setting.
     */
    val rows: List<SettingsRowSpec> = listOf(
        SettingsRowSpec(
            NOTIFICATION_ROW_ID,
            R.string.notifications,
            SettingsIcon.Notifications,
            SettingsSection.Notifications,
            subtitle = SettingsSubtitleKind.NotificationState,
        ),
        SettingsRowSpec(
            ALERT_CHANNELS_ROW_ID,
            R.string.settings_alert_channels,
            SettingsIcon.Notifications,
            SettingsSection.Notifications,
            subtitle = SettingsSubtitleKind.AlertChannelsState,
        ),
        SettingsRowSpec(
            "app_lock",
            R.string.settings_app_lock,
            SettingsIcon.Lock,
            SettingsSection.SecurityApp,
            // F-K9: the row used to read "Require authentication" above a key policy that requires
            // none (EvidenceKeyPolicy.requireUserAuthentication = false), and the flag rendered
            // nothing. The interface now says what the policy does, and no row claims a gate it
            // does not enforce (Q-K-4, A-26).
            subtitle = SettingsSubtitleKind.AppLockNotEnforced,
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
            subtitle = SettingsSubtitleKind.RetentionWindows,
        ),
        SettingsRowSpec("about", R.string.settings_about_shieldra, SettingsIcon.Info, SettingsSection.About),
        SettingsRowSpec("help", R.string.settings_help, SettingsIcon.Help, SettingsSection.About),
    )

    fun rowIds(): List<String> = rows.map { it.id }

    fun targetOf(id: String): SettingsRowTarget = when (id) {
        NOTIFICATION_ROW_ID -> SettingsRowTarget.NotificationSetup
        ALERT_CHANNELS_ROW_ID -> SettingsRowTarget.AlertChannels
        else -> SettingsRowTarget.NotAvailable
    }

    fun storageRowState(storageInstalled: Boolean): StorageRowState =
        if (storageInstalled) StorageRowState.InUse else StorageRowState.NotInstalled

    fun dashboardMode(storageInstalled: Boolean, motionMonitoring: Boolean): DashboardMode = when {
        !storageInstalled -> DashboardMode.NoStorageYet
        motionMonitoring -> DashboardMode.LocalEventsWithMotion
        else -> DashboardMode.LocalEventsOnly
    }
}

/** The line under the shield: what this build really does, never what it might. */
@StringRes
internal fun disclosureLabelRes(mode: SettingsContent.DashboardMode): Int = when (mode) {
    SettingsContent.DashboardMode.NoStorageYet -> R.string.dashboard_no_storage_disclosure
    SettingsContent.DashboardMode.LocalEventsOnly -> R.string.dashboard_events_disclosure
    SettingsContent.DashboardMode.LocalEventsWithMotion -> R.string.dashboard_motion_disclosure
}

/** The storage row names the state only; a figure would have to be invented. */
@StringRes
internal fun storageRowSubtitleRes(state: SettingsContent.StorageRowState): Int = when (state) {
    SettingsContent.StorageRowState.NotInstalled -> R.string.settings_storage_none
    SettingsContent.StorageRowState.InUse -> R.string.settings_storage_in_use
}

/**
 * The app-lock row's subtitle (F-K9). The evidence key policy sets
 * `requireUserAuthentication = false`, so the honest sentence is that no lock is
 * enforced. The interface was changed to match the policy; the policy was not
 * changed to match the interface.
 */
@StringRes
internal fun appLockSubtitleRes(): Int = R.string.settings_app_lock_not_enforced

/**
 * The retention row's subtitle (F-K6). §5.4 with amendment A-1 fixed the windows
 * and Q-K-2 made the sweeps run, so "Policy pending" described a decision that has
 * long been made. While the storage gate installed nothing there is also nothing
 * to retain, and the row says that instead.
 */
@StringRes
internal fun retentionSubtitleRes(storageInstalled: Boolean): Int =
    if (storageInstalled) R.string.settings_retention_windows else R.string.settings_retention_no_data

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
    alertChannelsActive: Boolean = false,
): Int? = when (kind) {
    SettingsSubtitleKind.None -> null
    SettingsSubtitleKind.AppLockNotEnforced -> appLockSubtitleRes()
    SettingsSubtitleKind.MatchSystem -> R.string.settings_match_system
    SettingsSubtitleKind.RetentionWindows -> retentionSubtitleRes(storageInstalled)
    SettingsSubtitleKind.NotificationState -> notificationStateRes(notificationState)
    SettingsSubtitleKind.AlertChannelsState ->
        if (alertChannelsActive) R.string.settings_alert_channels_on else R.string.settings_alert_channels_off
    SettingsSubtitleKind.StorageState ->
        storageRowSubtitleRes(SettingsContent.storageRowState(storageInstalled))
}
