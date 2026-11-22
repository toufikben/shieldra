package com.shieldra.domain

/**
 * Production lifecycle for the platform capture and alert stages.
 *
 * Every write is compare-and-set through [EventRepository.updateState]. A stage never manufactures
 * progress: READY requires a persisted evidence reference, and DELIVERED requires a persisted
 * successful delivery receipt. Terminal events are left untouched. A false result means persistence
 * refused the transition; callers must not treat that stage as durably advanced.
 */
class EventLifecycle(
    private val repository: EventRepository,
) {
    /** Starts or resumes evidence collection for an incident that is still active. */
    fun beginCapture(eventId: EventId): Boolean {
        val event = repository.find(eventId) ?: return false
        return when (event.state) {
            EventState.INITIATED, EventState.DEFERRED ->
                update(event, EventState.COLLECTING) == RepositoryResult.Applied
            EventState.COLLECTING -> true
            else -> false
        }
    }

    /** Completes collection only with evidence; an empty capture remains explicitly deferred. */
    fun finishCapture(eventId: EventId): Boolean {
        val event = repository.find(eventId) ?: return false
        if (event.state != EventState.COLLECTING) return event.state.isTerminal()
        val result = if (event.evidenceReferences.isEmpty()) {
            update(
                event,
                EventState.DEFERRED,
                event.metadata + (DEFERRED_STAGE_KEY to PipelineStage.EVIDENCE.name),
            )
        } else {
            update(event, EventState.READY)
        }
        return result == RepositoryResult.Applied
    }

    /**
     * Brings text-only events through collection honestly, then enters delivery. An event without
     * evidence passes through DEFERRED rather than claiming READY; a retry resumes from DEFERRED.
     */
    fun beginDelivery(eventId: EventId): Boolean {
        val initial = repository.find(eventId) ?: return false
        if (initial.state == EventState.INITIATED &&
            update(initial, EventState.COLLECTING) != RepositoryResult.Applied
        ) {
            return false
        }
        if (repository.find(eventId)?.state == EventState.COLLECTING && !finishCapture(eventId)) {
            return false
        }

        val event = repository.find(eventId) ?: return false
        return when (event.state) {
            EventState.READY, EventState.DEFERRED ->
                update(event, EventState.DELIVERING) == RepositoryResult.Applied
            EventState.DELIVERING -> true
            EventState.DELIVERED, EventState.FAILED_FINAL, EventState.EXPIRED -> true
            else -> false
        }
    }

    /** Records the real aggregate result after every enabled channel has reported its outcome. */
    fun finishDelivery(eventId: EventId, deferred: Boolean): Boolean {
        val event = repository.find(eventId) ?: return false
        if (event.state != EventState.DELIVERING) return event.state.isTerminal()
        val result = when {
            deferred -> update(
                event,
                EventState.DEFERRED,
                event.metadata + (DEFERRED_STAGE_KEY to PipelineStage.DELIVERY.name),
            )
            event.deliveryInformation.any { it.status == DeliveryStatus.SUCCESS } ->
                update(event, EventState.DELIVERED)
            else -> update(event, EventState.FAILED_FINAL)
        }
        return result == RepositoryResult.Applied
    }

    private fun update(
        event: SecurityEvent,
        next: EventState,
        metadata: Map<String, String>? = null,
    ): RepositoryResult = repository.updateState(event.id, event.state, next, metadata = metadata)

    private companion object {
        const val DEFERRED_STAGE_KEY = "deferredStage"
    }
}
