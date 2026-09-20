package com.shieldra.domain

enum class EventState {
    INITIATED,
    COLLECTING,
    READY,
    DELIVERING,
    DELIVERED,
    DEFERRED,
    FAILED_FINAL,
}

sealed interface StateTransitionResult {
    data class Allowed(val state: EventState) : StateTransitionResult

    data class Rejected(
        val from: EventState,
        val to: EventState,
        val reason: String,
    ) : StateTransitionResult
}

fun transition(from: EventState, to: EventState): StateTransitionResult {
    val allowed = when (from) {
        EventState.INITIATED -> setOf(EventState.COLLECTING, EventState.FAILED_FINAL)
        EventState.COLLECTING -> setOf(EventState.READY, EventState.DEFERRED, EventState.FAILED_FINAL)
        EventState.READY -> setOf(EventState.DELIVERING, EventState.DEFERRED, EventState.FAILED_FINAL)
        EventState.DELIVERING -> setOf(EventState.DELIVERED, EventState.DEFERRED, EventState.FAILED_FINAL)
        EventState.DEFERRED -> setOf(EventState.COLLECTING, EventState.READY, EventState.DELIVERING, EventState.FAILED_FINAL)
        EventState.DELIVERED, EventState.FAILED_FINAL -> emptySet()
    }

    return if (to in allowed) {
        StateTransitionResult.Allowed(to)
    } else {
        StateTransitionResult.Rejected(from, to, "Transition is not allowed by the Group C state contract")
    }
}
