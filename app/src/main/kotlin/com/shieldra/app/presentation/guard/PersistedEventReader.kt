package com.shieldra.app.presentation.guard

import androidx.annotation.PluralsRes
import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.app.presentation.model.EventDetailUiModel
import com.shieldra.app.presentation.model.EventType
import com.shieldra.app.presentation.model.EventUiModel
import com.shieldra.app.presentation.model.LocationKind
import com.shieldra.app.presentation.model.LocationUiModel
import com.shieldra.domain.EventState
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.SecurityEventType
import java.time.Duration
import java.time.Instant

/**
 * Projects persisted [SecurityEvent] rows into presentation models.
 *
 * Every field is derived from a real persisted value; nothing here invents a
 * photo, a location, a battery level, or a delivery receipt. All display text
 * is supplied already localized so this class stays framework-free and the
 * Arabic build renders the same real data.
 */
class PersistedEventReader(
    private val storageInstalled: Boolean,
    private val loadRecent: (limit: Int) -> List<SecurityEvent>,
    private val labels: PersistedEventLabels = PersistedEventLabels(),
    /**
     * Direct lookup by id. When it is supplied an event is found however old it is; without it the
     * reader falls back to scanning the newest [LOOKUP_WINDOW] rows, which is what pure tests use.
     */
    private val loadOne: ((eventId: String) -> SecurityEvent?)? = null,
) {
    fun readRecent(limit: Int, now: Instant): List<EventUiModel> {
        val events = readPersisted(limit) ?: return emptyList()
        return events.map { it.toUiModel(now) }
    }

    fun readOne(eventId: String, now: Instant): EventDetailUiModel? {
        if (!storageInstalled) return null
        val direct = loadOne
        if (direct != null) {
            return runCatching { direct(eventId) }.getOrNull()?.toDetailUiModel(now)
        }
        val events = readPersisted(LOOKUP_WINDOW) ?: return null
        return events.firstOrNull { it.id.value.toString() == eventId }?.toDetailUiModel(now)
    }

    private fun readPersisted(limit: Int): List<SecurityEvent>? {
        if (!storageInstalled) return null
        return runCatching { loadRecent(limit) }.getOrNull()
    }

    private fun SecurityEvent.toUiModel(now: Instant): EventUiModel = EventUiModel(
        id = id.value.toString(),
        type = persistedEventTypeOf(this.type),
        title = labels.titleFor(this.type),
        subtitle = labels.stateLabel(state),
        timestampLabel = labels.ageLabel(now, createdAt),
        // A recorded attempt is the stronger fact; without one the label follows the state.
        deliveryStatus = recordedDeliveryStatus() ?: state.toDeliveryStatus(),
        hasPhoto = false,
        hasLocation = false,
    )

    private fun SecurityEvent.toDetailUiModel(now: Instant): EventDetailUiModel = EventDetailUiModel(
        id = id.value.toString(),
        type = persistedEventTypeOf(this.type),
        title = labels.titleFor(this.type),
        timestampLabel = labels.ageLabel(now, createdAt),
        statusLabel = labels.stateLabel(state),
        // A-7's durable count, shown only for the Panic incidents that carry one.
        activationCount = panicActivationCount,
        batteryPercent = null,
        hasPhoto = false,
        location = LocationUiModel(kind = LocationKind.Unavailable),
        // Only attempts the delivery path really recorded (A-32); none recorded, none shown.
        delivery = deliveryReceipts(),
    )

    private companion object {
        /** An event list is bounded well below this, so a bounded lookup suffices. */
        const val LOOKUP_WINDOW = 200
    }
}

/**
 * The History type a persisted row belongs to, read off the row's own guard. A row that names no
 * guard stays [EventType.Other] rather than borrowing Panic's filter and icon, and because the
 * reader's labels carry no type, no wording passed in can retype a row.
 */
internal fun persistedEventTypeOf(type: SecurityEventType): EventType = when (type) {
    SecurityEventType.MOTION_CONFIRMED_EVENT -> EventType.Motion
    SecurityEventType.PANIC_ACTIVATION -> EventType.Panic
    SecurityEventType.CONFIRMED_SECURITY_EVENT -> EventType.Other
}

/**
 * Localized display text. [ageLabel] is injected so the caller owns the
 * "x ago" wording; [stateLabel] states what actually happened to the event.
 */
data class PersistedEventLabels(
    val title: String = "Security event",
    val stateLabels: Map<EventState, String> = emptyMap(),
    /** A guard-specific title per stored type; a type with none keeps the generic [title]. */
    val typeTitles: Map<SecurityEventType, String> = emptyMap(),
    val ageLabel: (now: Instant, createdAt: Instant) -> String = { now, createdAt ->
        val parts = ageParts(now, createdAt)
        when (parts) {
            is AgeParts.Days -> "${parts.value}d ago"
            is AgeParts.Hours -> "${parts.value}h ago"
            is AgeParts.Minutes -> "${parts.value}m ago"
            is AgeParts.Seconds -> "${parts.value}s ago"
        }
    },
) {
    fun stateLabel(state: EventState): String = stateLabels[state] ?: state.name

    fun titleFor(type: SecurityEventType): String = typeTitles[type] ?: title
}

internal fun EventState.toDeliveryStatus(): DeliveryStatus = when (this) {
    EventState.DELIVERED -> DeliveryStatus.Delivered
    EventState.DELIVERING -> DeliveryStatus.Delivering
    EventState.DEFERRED -> DeliveryStatus.Deferred
    EventState.FAILED_FINAL -> DeliveryStatus.Failed
    EventState.EXPIRED -> DeliveryStatus.Expired
    EventState.READY -> DeliveryStatus.Ready
    EventState.INITIATED, EventState.COLLECTING -> DeliveryStatus.NotAttempted
}

/**
 * The label resource for a persisted state. A state that performed no delivery
 * is never labelled as sending or delivered, so history cannot read a stored
 * INITIATED event as a notification that was never attempted.
 */
internal fun eventStateLabelRes(state: EventState): Int = when (state) {
    EventState.INITIATED -> com.shieldra.app.R.string.status_recorded
    EventState.COLLECTING -> com.shieldra.app.R.string.status_collecting
    EventState.READY -> com.shieldra.app.R.string.status_ready
    EventState.DELIVERING -> com.shieldra.app.R.string.status_sending
    EventState.DELIVERED -> com.shieldra.app.R.string.status_delivered
    EventState.DEFERRED -> com.shieldra.app.R.string.status_retrying
    EventState.FAILED_FINAL -> com.shieldra.app.R.string.status_failed
    EventState.EXPIRED -> com.shieldra.app.R.string.status_expired
}

/**
 * The quantity resource holding the wording for an age tier (Q-K-4, F-K11).
 *
 * The tiers used to be four fixed-form strings, which is how the owner's phone read
 * "1 hours ago": a fixed template cannot decline a number, so the count is now
 * resolved through the locale's own plural forms.
 */
@PluralsRes
internal fun ageQuantityRes(parts: AgeParts): Int = when (parts) {
    is AgeParts.Days -> com.shieldra.app.R.plurals.event_age_days
    is AgeParts.Hours -> com.shieldra.app.R.plurals.event_age_hours
    is AgeParts.Minutes -> com.shieldra.app.R.plurals.event_age_minutes
    is AgeParts.Seconds -> com.shieldra.app.R.plurals.event_age_seconds
}

/** Coarse age of an event, so the caller owns all localized wording. */
sealed interface AgeParts {
    /** The count the wording must decline, whatever tier it landed in. */
    val value: Long

    data class Days(override val value: Long) : AgeParts
    data class Hours(override val value: Long) : AgeParts
    data class Minutes(override val value: Long) : AgeParts
    data class Seconds(override val value: Long) : AgeParts
}

internal fun ageParts(now: Instant, createdAt: Instant): AgeParts {
    val elapsed = Duration.between(createdAt, now)
    if (elapsed.isNegative) return AgeParts.Seconds(0)
    val seconds = elapsed.seconds
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> AgeParts.Days(days)
        hours > 0 -> AgeParts.Hours(hours)
        minutes > 0 -> AgeParts.Minutes(minutes)
        else -> AgeParts.Seconds(seconds)
    }
}
