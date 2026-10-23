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

/**
 * What the History chip is allowed to say about delivery.
 *
 * [NotAttempted] replaced `Skipped` (F-K10, Q-K-4): a stored event that was recorded and
 * never offered for delivery has not been skipped by anything — no delivery decision was
 * taken at all — and naming it "skipped" stated a decision the row does not carry.
 */
enum class DeliveryStatus {
    Ready, Delivering, Delivered, Deferred, Failed, Expired, NotAttempted
}

/**
 * What the dashboard's network slot actually knows (F-K7, Q-K-4).
 *
 * Nothing in this build reads connectivity, so [Unknown] is the value the dashboard
 * reports today. It is separated from [Offline] because "we did not measure this" and
 * "this is offline" are different claims, and only the second one may use the offline
 * wording.
 */
enum class NetworkStatus {
    Unknown,
    Offline,
    Online,
}

enum class ChannelId { Email, WhatsApp, Telegram }

data class ChannelReceiptUiModel(
    val channel: ChannelId,
    val status: DeliveryStatus,
    val detail: String? = null,
)

/**
 * The guard a History row belongs to. [Other] is where a persisted event of no named guard lands:
 * it shows under "All" and under no filter chip, because filing an unattributed row under another
 * guard's filter would state a fact the row does not carry.
 */
enum class EventType { FailedUnlock, Motion, SimChange, Panic, Battery, Countdown, Other }

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
    /** The persisted state, already localized; null when the source has none to report. */
    val statusLabel: String? = null,
    /** Durable Panic press count (A-7); null for every event that does not carry one. */
    val activationCount: Int? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val hasPhoto: Boolean,
    val location: LocationUiModel,
    val delivery: List<ChannelReceiptUiModel>,
    /** The decrypt-on-demand evidence ids, when the event really carries them. */
    val photoEvidenceId: String? = null,
    val audioEvidenceId: String? = null,
)

data class DashboardUiModel(
    val protectionState: ProtectionVisualState,
    /** Seconds since a check really ran; null when no detector has ever checked. */
    val lastCheckSeconds: Long?,
    val guards: List<GuardUiModel>,
    val recentEvents: List<EventUiModel>,
    val batteryPercent: Int?,
    val networkLabel: String,
    /** F-K7: what the slot really knows about connectivity, not a colour to render it with. */
    val networkStatus: NetworkStatus,
    val showAds: Boolean,
    /** Whether this process really opened local storage; decides the disclosure line. */
    val storageInstalled: Boolean = false,
)

enum class HistoryTypeFilter { All, Lock, Motion, Sim, Panic, Battery }
enum class HistoryStatusFilter { All, Delivered, Deferred, Failed }

data class HistoryFilterState(
    val type: HistoryTypeFilter = HistoryTypeFilter.All,
    val status: HistoryStatusFilter = HistoryStatusFilter.All,
)

enum class SettingsSection { Notifications, Appearance, PrivacyData, SecurityApp, About }
enum class SettingsIcon { Notifications, Lock, Theme, Language, Storage, Retention, Info, Help }

data class SettingsRowUiModel(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: SettingsIcon,
    val requiresAuth: Boolean = false,
    val section: SettingsSection,
)
