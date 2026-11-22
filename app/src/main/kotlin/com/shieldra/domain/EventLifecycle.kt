package com.shieldra.domain

/**
 * Production lifecycle for the platform capture and alert stages.
 *
 * Every write is compare-and-set through [EventRepository.updateState]. A stage never manufactures
 * progress: READY requires a persisted evidence reference, and DELIVERED requires a persisted
 * successful delivery receipt. Terminal events are left untouched.
 */
class EventLifecycle(
    private val repository: EventRepository,
) {
    /** Starts or resumes evidence collection for an incident that is still active. */
    fun beginCapture(eventId: EventId) {
        val event = repository.find(eventId) ?: return
        when (event.state) {
            EventState.INITIATED, EventState.DEFERRED ->
                update(event, EventState.COLLECTING)
            else -> Unit
        }
    }

    /** Completes collection only with evidence; an empty capture remains explicitly deferred. */
    fun finishCapture(eventId: EventId) {
        val event = repository.find(eventId) ?: return
        if (event.state != EventState.COLLECTING) return
        if (event.evidenceReferences.isEmpty()) {
            update(
                event,
                EventState.DEFERRED,
                event.metadata + (DEFERRED_STAGE_KEY to PipelineStage.EVIDENCE.name),
            )
        } else {
            update(event, EventState.READY)
        }
    }

    /**
     * Brings text-only events through collection honestly, then enters delivery. An event without
     * evidence passes through DEFERRED rather than claiming READY; a retry resumes from DEFERRED.
     */
    fun beginDelivery(eventId: EventId) {
        val initial = repository.find(eventId) ?: return
        if (initial.state == EventState.INITIATED) update(initial, EventState.COLLECTING)
        if (repository.find(eventId)?.state == EventState.COLLECTING) finishCapture(eventId)

        val event = repository.find(eventId) ?: return
        when (event.state) {
            EventState.READY, EventState.DEFERRED -> update(event, EventState.DELIVERING)
            else -> Unit
        }
    }

    /** Records the real aggregate result after every enabled channel has reported its outcome. */
    fun finishDelivery(eventId: EventId, deferred: Boolean) {
        val event = repository.find(eventId) ?: return
        if (event.state != EventState.DELIVERING) return
        when {
            deferred -> update(
                event,
                EventState.DEFERRED,
                event.metadata + (DEFERRED_STAGE_KEY to PipelineStage.DELIVERY.name),
            )
            event.deliveryInformation.any { it.status == DeliveryStatus.SUCCESS } ->
                update(event, EventState.DELIVERED)
            else -> update(event, EventState.FAILED_FINAL)
        }
    }

    private fun update(
        event: SecurityEvent,
        next: EventState,
        metadata: Map<String, String>? = null,
    ) {
        repository.updateState(event.id, event.state, next, metadata = metadata)
    }

    private companion object {
        const val DEFERRED_STAGE_KEY = "deferredStage"
    }
}
