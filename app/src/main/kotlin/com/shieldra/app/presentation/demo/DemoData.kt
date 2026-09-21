package com.shieldra.app.presentation.demo

import com.shieldra.app.presentation.model.*

/** Static data for previews and the intentionally demo-only Phase 3 shell. */
object DemoData {
    val guards = listOf(
        GuardUiModel(GuardKind.Lock, "Lock Guard", "Active · 2 attempts", GuardStatus.Active),
        GuardUiModel(GuardKind.Motion, "Motion Guard", "Monitoring", GuardStatus.Active),
        GuardUiModel(GuardKind.Sim, "SIM Guard", "Monitored", GuardStatus.Active),
        GuardUiModel(GuardKind.Panic, "Panic", "Ready", GuardStatus.Active),
    )

    val recentEvents = listOf(
        EventUiModel("evt_1", EventType.FailedUnlock, "Failed unlock attempt", "Attempt 2", "Today · 14:32", DeliveryStatus.Delivered, true, true),
        EventUiModel("evt_2", EventType.Motion, "Motion detected", "Movement after stillness", "Today · 13:15", DeliveryStatus.Deferred, true, false),
    )

    val dashboard = DashboardUiModel(ProtectionVisualState.Protected, 12L, guards, recentEvents, 84, "Wi-Fi", true, true)
    val dashboardEmpty = dashboard.copy(recentEvents = emptyList())
    val dashboardSuspicious = dashboard.copy(protectionState = ProtectionVisualState.Suspicious, lastCheckSeconds = 4L)
    val dashboardDisabled = dashboard.copy(
        protectionState = ProtectionVisualState.Disabled,
        guards = guards.map { it.copy(status = GuardStatus.Disabled) },
    )

    val eventDetail = EventDetailUiModel(
        id = "evt_8a3f...d92",
        type = EventType.FailedUnlock,
        title = "Failed unlock attempt",
        attemptNumber = 2,
        timestampLabel = "Today · 14:32:08",
        batteryPercent = 84,
        hasPhoto = true,
        location = LocationUiModel(LocationKind.Current, 36.7538, 3.0588, 8, 4, "GPS"),
        delivery = listOf(
            ChannelReceiptUiModel(ChannelId.Email, DeliveryStatus.Delivered, "14:32"),
            ChannelReceiptUiModel(ChannelId.WhatsApp, DeliveryStatus.Failed, "Invalid recipient"),
            ChannelReceiptUiModel(ChannelId.Telegram, DeliveryStatus.Delivered, "14:32"),
        ),
    )

    val eventDetailLastKnown = eventDetail.copy(
        location = LocationUiModel(LocationKind.LastKnown, 36.7538, 3.0588, 45, 7200, "Fused"),
    )

    val premium = PremiumUiModel(
        priceLabel = "9.99",
        features = listOf(
            PremiumFeatureUiModel("Multiple delivery channels", "Email · WhatsApp · Telegram"),
            PremiumFeatureUiModel("Battery Emergency", "Queue evidence when battery is low"),
            PremiumFeatureUiModel("Extended history", "90 days · 500 events"),
            PremiumFeatureUiModel("Advanced delivery receipts"),
            PremiumFeatureUiModel("No ads"),
        ),
        freeTierSummary = "Free includes: Lock Guard · Motion Guard · SIM Guard · Panic · Email delivery.",
    )

    val settingsRows = listOf(
        SettingsRowUiModel("protection", "Protection", "Lock Guard · Guards", SettingsIcon.Security, section = SettingsSection.Security),
        SettingsRowUiModel("delivery", "Delivery channels", "Email · WhatsApp · Telegram", SettingsIcon.Delivery, section = SettingsSection.Delivery),
        SettingsRowUiModel("app_lock", "App lock", "Require authentication", SettingsIcon.Lock, true, SettingsSection.SecurityApp),
        SettingsRowUiModel("theme", "Theme", "Match system", SettingsIcon.Theme, section = SettingsSection.Appearance),
        SettingsRowUiModel("language", "Language", icon = SettingsIcon.Language, section = SettingsSection.Appearance),
        SettingsRowUiModel("storage", "Storage", "12 events · 3.2 MB", SettingsIcon.Storage, section = SettingsSection.PrivacyData),
        SettingsRowUiModel("retention", "Retention policy", "7 days", SettingsIcon.Retention, true, SettingsSection.PrivacyData),
        SettingsRowUiModel("about", "About Shieldra", icon = SettingsIcon.Info, section = SettingsSection.About),
        SettingsRowUiModel("help", "Help", icon = SettingsIcon.Help, section = SettingsSection.About),
    )
}
