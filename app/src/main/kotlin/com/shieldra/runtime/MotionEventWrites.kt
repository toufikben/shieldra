package com.shieldra.runtime

import com.shieldra.domain.MotionConfirmation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The in-process fact that Motion just wrote an event row.
 *
 * It exists because the running monitor writes events nobody pressed anything for, so an event list
 * keyed only on UI actions keeps showing a stale query result until the app restarts. The token is
 * one counter: the service raises it after a real write, a screen keys its reload on it, and that
 * is the whole mechanism — no bus, no polling, no timer, and no second copy of any monitoring state.
 * It is process-local because that is all the monitor is: a write from a process that is not running
 * has no list left to invalidate.
 */
object MotionEventWrites {
    private val writes = MutableStateFlow(0L)

    /** The only Flow a screen may key an event reload on. */
    val state: StateFlow<Long> = writes.asStateFlow()

    /**
     * Only a newly persisted event invalidates anything. A movement that joined the open incident
     * changed no row, a signal stored nothing, and a refused write stored nothing either — bumping
     * the token for those would reload a query whose answer cannot have moved.
     */
    fun record(confirmation: MotionConfirmation) {
        if (confirmation is MotionConfirmation.Recorded) writes.update { it + 1L }
    }
}
