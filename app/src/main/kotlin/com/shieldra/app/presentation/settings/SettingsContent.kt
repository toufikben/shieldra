package com.shieldra.app.presentation.settings

import androidx.annotation.StringRes
import com.shieldra.app.R
import com.shieldra.app.presentation.model.SettingsIcon
import com.shieldra.app.presentation.model.SettingsSection
import com.shieldra.app.settings.AppLanguage
import com.shieldra.app.settings.ThemeMode
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
    ProtectionState,
    AppLockNotEnforced,
    AppLockState,
    ThemeValue,
    LanguageValue,
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
    ProtectionSetup,
    Health,
    SafeZones,
    AppLock,
    Theme,
    Language,
    Storage,
    Retention,
    About,
    Help,
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

    /**
     * A-33/A-34: arming the protection guards. Q-K-9 removed the old "Protection" row because no
     * guard existed to switch on; one does now (Lock, SIM, battery, countdown behind a real
     * foreground service), so the row is a setting again, not a claim.
     */
    const val PROTECTION_ROW_ID: String = "protection"

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
        // Protection & alerts: arm the guards, set where alerts leave (with the notification
        // permission in the same place), read what is really protecting the phone, mark safe zones.
        SettingsRowSpec(
            PROTECTION_ROW_ID,
            R.string.protection_title,
            SettingsIcon.Lock,
            SettingsSection.Notifications,
            subtitle = SettingsSubtitleKind.ProtectionState,
        ),
        SettingsRowSpec(
            ALERT_CHANNELS_ROW_ID,
            R.string.settings_alerts_and_notifications,
            SettingsIcon.Notifications,
            SettingsSection.Notifications,
            subtitle = SettingsSubtitleKind.AlertChannelsState,
        ),
        SettingsRowSpec(HEALTH_ROW_ID, R.string.health_title, SettingsIcon.Health, SettingsSection.Notifications),
        SettingsRowSpec(SAFE_ZONES_ROW_ID, R.string.safe_zones_title, SettingsIcon.Zones, SettingsSection.Notifications),
        // Data & privacy: what is stored, for how long.
        SettingsRowSpec(
            STORAGE_ROW_ID,
            R.string.settings_storage,
            SettingsIcon.Storage,
            SettingsSection.PrivacyData,
            subtitle = SettingsSubtitleKind.StorageState,
        ),
        SettingsRowSpec(
            RETENTION_ROW_ID,
            R.string.settings_retention,
            SettingsIcon.Retention,
            SettingsSection.PrivacyData,
            subtitle = SettingsSubtitleKind.RetentionWindows,
        ),
        // Security: the owner's screen lock in front of the app (A-39).
        SettingsRowSpec(
            APP_LOCK_ROW_ID,
            R.string.settings_app_lock,
            SettingsIcon.Lock,
            SettingsSection.SecurityApp,
            subtitle = SettingsSubtitleKind.AppLockState,
        ),
        SettingsRowSpec(
            THEME_ROW_ID,
            R.string.settings_theme,
            SettingsIcon.Theme,
            SettingsSection.Appearance,
            subtitle = SettingsSubtitleKind.ThemeValue,
        ),
        SettingsRowSpec(
            LANGUAGE_ROW_ID,
            R.string.settings_language,
            SettingsIcon.Language,
            SettingsSection.Appearance,
            subtitle = SettingsSubtitleKind.LanguageValue,
        ),
        SettingsRowSpec(ABOUT_ROW_ID, R.string.settings_about_shieldra, SettingsIcon.Info, SettingsSection.About),
        SettingsRowSpec(HELP_ROW_ID, R.string.settings_help, SettingsIcon.Help, SettingsSection.About),
    )

    const val HEALTH_ROW_ID: String = "health"
    const val SAFE_ZONES_ROW_ID: String = "safe_zones"
    const val STORAGE_ROW_ID: String = "storage"
    const val RETENTION_ROW_ID: String = "retention"
    const val APP_LOCK_ROW_ID: String = "app_lock"
    const val THEME_ROW_ID: String = "theme"
    const val LANGUAGE_ROW_ID: String = "language"
    const val ABOUT_ROW_ID: String = "about"
    const val HELP_ROW_ID: String = "help"

    fun rowIds(): List<String> = rows.map { it.id }

    fun targetOf(id: String): SettingsRowTarget = when (id) {
        NOTIFICATION_ROW_ID -> SettingsRowTarget.NotificationSetup
        ALERT_CHANNELS_ROW_ID -> SettingsRowTarget.AlertChannels
        PROTECTION_ROW_ID -> SettingsRowTarget.ProtectionSetup
        HEALTH_ROW_ID -> SettingsRowTarget.Health
        SAFE_ZONES_ROW_ID -> SettingsRowTarget.SafeZones
        APP_LOCK_ROW_ID -> SettingsRowTarget.AppLock
        THEME_ROW_ID -> SettingsRowTarget.Theme
        LANGUAGE_ROW_ID -> SettingsRowTarget.Language
        STORAGE_ROW_ID -> SettingsRowTarget.Storage
        RETENTION_ROW_ID -> SettingsRowTarget.Retention
        ABOUT_ROW_ID -> SettingsRowTarget.About
        HELP_ROW_ID -> SettingsRowTarget.Help
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
    protectionArmed: Boolean = false,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    language: AppLanguage = AppLanguage.SYSTEM,
    appLockEnabled: Boolean = false,
): Int? = when (kind) {
    SettingsSubtitleKind.None -> null
    SettingsSubtitleKind.AppLockNotEnforced -> appLockSubtitleRes()
    SettingsSubtitleKind.AppLockState ->
        if (appLockEnabled) R.string.settings_app_lock_on else R.string.settings_app_lock_off
    SettingsSubtitleKind.ThemeValue -> when (themeMode) {
        ThemeMode.SYSTEM -> R.string.settings_match_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
    SettingsSubtitleKind.LanguageValue -> when (language) {
        AppLanguage.SYSTEM -> R.string.settings_match_system
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.ARABIC -> R.string.language_arabic
    }
    SettingsSubtitleKind.MatchSystem -> R.string.settings_match_system
    SettingsSubtitleKind.RetentionWindows -> retentionSubtitleRes(storageInstalled)
    SettingsSubtitleKind.NotificationState -> notificationStateRes(notificationState)
    SettingsSubtitleKind.AlertChannelsState ->
        if (alertChannelsActive) R.string.settings_alert_channels_on else R.string.settings_alert_channels_off
    SettingsSubtitleKind.ProtectionState ->
        if (protectionArmed) R.string.protection_state_armed else R.string.protection_state_disarmed
    SettingsSubtitleKind.StorageState ->
        storageRowSubtitleRes(SettingsContent.storageRowState(storageInstalled))
}
