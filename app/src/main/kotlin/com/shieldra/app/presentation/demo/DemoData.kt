package com.shieldra.app.presentation.demo

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import com.shieldra.app.presentation.model.*

/** Localized static data for the intentionally demo-only Phase 3 shell. */
object DemoData {
    @Composable
    fun guards(): List<GuardUiModel> = listOf(
        GuardUiModel(GuardKind.Lock, stringResource(R.string.demo_lock_guard), pluralStringResource(R.plurals.demo_active_attempts, 2, 2), GuardStatus.Active),
        GuardUiModel(GuardKind.Motion, stringResource(R.string.demo_motion_guard), stringResource(R.string.demo_monitoring), GuardStatus.Active),
        GuardUiModel(GuardKind.Sim, stringResource(R.string.demo_sim_guard), stringResource(R.string.demo_monitored), GuardStatus.Active),
        GuardUiModel(GuardKind.Panic, stringResource(R.string.demo_panic), stringResource(R.string.demo_ready), GuardStatus.Active),
    )

    @Composable
    fun recentEvents(): List<EventUiModel> = listOf(
        EventUiModel("evt_1", EventType.FailedUnlock, stringResource(R.string.demo_failed_unlock), stringResource(R.string.demo_attempt, 2), stringResource(R.string.demo_time_failed_unlock), DeliveryStatus.Delivered, true, true),
        EventUiModel("evt_2", EventType.Motion, stringResource(R.string.demo_motion_detected), stringResource(R.string.demo_movement_after_stillness), stringResource(R.string.demo_time_motion), DeliveryStatus.Deferred, true, false),
    )

    @Composable
    fun dashboard(): DashboardUiModel = DashboardUiModel(
        ProtectionVisualState.Protected,
        12L,
        guards(),
        recentEvents(),
        84,
        stringResource(R.string.demo_network_wifi),
        true,
        true,
    )

    @Composable
    fun eventDetail(): EventDetailUiModel = EventDetailUiModel(
        id = "evt_8a3f...d92",
        type = EventType.FailedUnlock,
        title = stringResource(R.string.demo_failed_unlock),
        attemptNumber = 2,
        timestampLabel = stringResource(R.string.demo_time_detail),
        batteryPercent = 84,
        hasPhoto = true,
        location = LocationUiModel(LocationKind.Current, 36.7538, 3.0588, 8, 4, stringResource(R.string.location_source_gps)),
        delivery = listOf(
            ChannelReceiptUiModel(ChannelId.Email, DeliveryStatus.Delivered, stringResource(R.string.demo_receipt_time)),
            ChannelReceiptUiModel(ChannelId.WhatsApp, DeliveryStatus.Failed, stringResource(R.string.demo_invalid_recipient)),
            ChannelReceiptUiModel(ChannelId.Telegram, DeliveryStatus.Delivered, stringResource(R.string.demo_receipt_time)),
        ),
    )

    @Composable
    fun premium(): PremiumUiModel = PremiumUiModel(
        priceLabel = stringResource(R.string.demo_premium_price),
        features = listOf(
            PremiumFeatureUiModel(stringResource(R.string.premium_feature_channels), stringResource(R.string.premium_feature_channels_detail)),
            PremiumFeatureUiModel(stringResource(R.string.premium_feature_battery), stringResource(R.string.premium_feature_battery_detail)),
            PremiumFeatureUiModel(stringResource(R.string.premium_feature_history), stringResource(R.string.premium_feature_history_detail)),
            PremiumFeatureUiModel(stringResource(R.string.premium_feature_receipts)),
            PremiumFeatureUiModel(stringResource(R.string.premium_feature_no_ads)),
        ),
        freeTierSummary = stringResource(R.string.premium_free_summary),
    )

    @Composable
    fun settingsRows(): List<SettingsRowUiModel> = listOf(
        SettingsRowUiModel("protection", stringResource(R.string.settings_protection), stringResource(R.string.settings_guards_summary), SettingsIcon.Security, section = SettingsSection.Security),
        SettingsRowUiModel("delivery", stringResource(R.string.settings_delivery_channels), stringResource(R.string.demo_delivery_channels), SettingsIcon.Delivery, section = SettingsSection.Delivery),
        SettingsRowUiModel("app_lock", stringResource(R.string.settings_app_lock), stringResource(R.string.settings_require_authentication), SettingsIcon.Lock, true, SettingsSection.SecurityApp),
        SettingsRowUiModel("theme", stringResource(R.string.settings_theme), stringResource(R.string.settings_match_system), SettingsIcon.Theme, section = SettingsSection.Appearance),
        SettingsRowUiModel("language", stringResource(R.string.settings_language), icon = SettingsIcon.Language, section = SettingsSection.Appearance),
        SettingsRowUiModel("storage", stringResource(R.string.settings_storage), stringResource(R.string.settings_storage_summary), SettingsIcon.Storage, section = SettingsSection.PrivacyData),
        SettingsRowUiModel("retention", stringResource(R.string.settings_retention), stringResource(R.string.settings_seven_days), SettingsIcon.Retention, true, SettingsSection.PrivacyData),
        SettingsRowUiModel("about", stringResource(R.string.settings_about_shieldra), icon = SettingsIcon.Info, section = SettingsSection.About),
        SettingsRowUiModel("help", stringResource(R.string.settings_help), icon = SettingsIcon.Help, section = SettingsSection.About),
    )
}
