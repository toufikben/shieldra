package com.shieldra.app.presentation.guard

import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.app.presentation.model.EventDetailUiModel
import com.shieldra.app.presentation.model.EventType
import com.shieldra.app.presentation.model.EventUiModel
import com.shieldra.app.presentation.model.LocationKind
import com.shieldra.app.presentation.model.LocationUiModel
import com.shieldra.domain.EventState
import com.shieldra.domain.SecurityEvent
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
) {
    fun readRecent(limit: Int, now: Instant): List<EventUiModel> {
        val events = readPersisted(limit) ?: return emptyList()
        return events.map { it.toUiModel(now) }
    }

    fun readOne(eventId: String, now: Instant): EventDetailUiModel? {
        val events = readPersisted(LOOKUP_WINDOW) ?: return null
        return events.firstOrNull { it.id.value.toString() == eventId }?.toDetailUiModel(now)
    }

    private fun readPersisted(limit: Int): List<SecurityEvent>? {
        if (!storageInstalled) return null
        return runCatching { loadRecent(limit) }.getOrNull()
    }

    private fun SecurityEvent.toUiModel(now: Instant): EventUiModel = EventUiModel(
        id = id.value.toString(),
        type = labels.type,
        title = labels.title,
        subtitle = labels.stateLabel(state),
        timestampLabel = labels.ageLabel(now, createdAt),
        deliveryStatus = state.toDeliveryStatus(),
        hasPhoto = false,
        hasLocation = false,
    )

    private fun SecurityEvent.toDetailUiModel(now: Instant): EventDetailUiModel = EventDetailUiModel(
        id = id.value.toString(),
        type = labels.type,
        title = labels.title,
        timestampLabel = labels.ageLabel(now, createdAt),
        batteryPercent = null,
        hasPhoto = false,
        location = LocationUiModel(kind = LocationKind.Unavailable),
        delivery = emptyList(),
    )

    private companion object {
        /** An event list is bounded well below this, so a bounded lookup suffices. */
        const val LOOKUP_WINDOW = 200
    }
}

/**
 * Localized display text. [ageLabel] is injected so the caller owns the
 * "x ago" wording; [stateLabel] states what actually happened to the event.
 */
data class PersistedEventLabels(
    val type: EventType = EventType.Panic,
    val title: String = "Security event",
    val stateLabels: Map<EventState, String> = emptyMap(),
    val ageLabel: (now: Instant, createdAt: Instant) -> String = { now, createdAt ->
        defaultAgeLabel(now, createdAt)
    },
) {
    fun stateLabel(state: EventState): String = stateLabels[state] ?: state.name
}

internal fun EventState.toDeliveryStatus(): DeliveryStatus = when (this) {
    EventState.DELIVERED -> DeliveryStatus.Delivered
    EventState.DELIVERING -> DeliveryStatus.Delivering
    EventState.DEFERRED -> DeliveryStatus.Deferred
    EventState.FAILED_FINAL -> DeliveryStatus.Failed
    EventState.EXPIRED -> DeliveryStatus.Expired
    EventState.READY -> DeliveryStatus.Ready
    EventState.INITIATED, EventState.COLLECTING -> DeliveryStatus.Skipped
}

/** Coarse age of an event, so the caller owns all localized wording. */
sealed interface AgeParts {
    data class Days(val value: Long) : AgeParts
    data class Hours(val value: Long) : AgeParts
    data class Minutes(val value: Long) : AgeParts
    data class Seconds(val value: Long) : AgeParts
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
