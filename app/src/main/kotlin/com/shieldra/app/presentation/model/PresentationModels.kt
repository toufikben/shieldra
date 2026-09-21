package com.shieldra.app.presentation.model

enum class ProtectionVisualState {
    Protected,
    Suspicious,
    SecurityEvent,
    Attention,
    Disabled,
}

enum class GuardKind { Lock, Motion, Sim, Panic, Battery }

enum class GuardStatus { Active, Signal, Disabled, Locked }

data class GuardUiModel(
    val kind: GuardKind,
    val title: String,
    val subtitle: String,
    val status: GuardStatus,
    val isPremiumLocked: Boolean = false,
    val enabled: Boolean = true,
)

enum class LocationKind { Current, LastKnown, Unavailable }

data class LocationUiModel(
    val kind: LocationKind,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Int? = null,
    val ageSeconds: Long? = null,
    val source: String? = null,
)

enum class DeliveryStatus {
    Ready, Delivering, Delivered, Deferred, Failed, Expired, Skipped
}

enum class ChannelId { Email, WhatsApp, Telegram }

data class ChannelReceiptUiModel(
    val channel: ChannelId,
    val status: DeliveryStatus,
    val detail: String? = null,
)

enum class EventType { FailedUnlock, Motion, SimChange, Panic, Battery }

data class EventUiModel(
    val id: String,
    val type: EventType,
    val title: String,
    val subtitle: String,
    val timestampLabel: String,
    val deliveryStatus: DeliveryStatus,
    val hasPhoto: Boolean,
    val hasLocation: Boolean,
)

data class EventDetailUiModel(
    val id: String,
    val type: EventType,
    val title: String,
    val attemptNumber: Int? = null,
    val timestampLabel: String,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val hasPhoto: Boolean,
    val location: LocationUiModel,
    val delivery: List<ChannelReceiptUiModel>,
)

data class DashboardUiModel(
    val protectionState: ProtectionVisualState,
    val lastCheckSeconds: Long,
    val guards: List<GuardUiModel>,
    val recentEvents: List<EventUiModel>,
    val batteryPercent: Int?,
    val networkLabel: String,
    val networkOk: Boolean,
    val showAds: Boolean,
)

enum class HistoryTypeFilter { All, Lock, Motion, Sim, Panic, Battery }
enum class HistoryStatusFilter { All, Delivered, Deferred, Failed }

data class HistoryFilterState(
    val type: HistoryTypeFilter = HistoryTypeFilter.All,
    val status: HistoryStatusFilter = HistoryStatusFilter.All,
)

data class PremiumFeatureUiModel(
    val title: String,
    val subtitle: String? = null,
)

data class PremiumUiModel(
    val priceLabel: String?,
    val features: List<PremiumFeatureUiModel>,
    val freeTierSummary: String,
)

enum class SettingsSection { Security, Delivery, Appearance, PrivacyData, SecurityApp, About }
enum class SettingsIcon { Security, Delivery, Lock, Theme, Language, Storage, Retention, Info, Help }

data class SettingsRowUiModel(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: SettingsIcon,
    val requiresAuth: Boolean = false,
    val section: SettingsSection,
)
