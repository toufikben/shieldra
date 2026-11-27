package com.shieldra.protection

/**
 * The confirmation rule for the owner's remote wipe (P2-3, owner decision 2026-11-27).
 *
 * Erasing the phone cannot be undone, so a single command must never be enough. The rule has three
 * parts, and they exist because each one closes a different way the wrong phone gets erased:
 *
 * - **The first command only opens a window.** `/wipe` answers with one sentence that says what to
 *   send and how long there is to send it. Nothing is erased, so a mistyped or misread command costs
 *   the owner a message, not a phone.
 * - **Only a confirmation inside the window erases.** A late `/wipe confirm` is refused, so a request
 *   made before the owner changed his mind cannot be completed by a message that arrives afterwards.
 * - **Every gate is asked again where it acts.** The window can last ten minutes, and inside it the
 *   owner can turn the switch off, the plan can lapse, protection can be disarmed, and the
 *   administrator power can be removed. All of those are read at the moment of the erase, not when the
 *   request was made.
 *
 * Pure on purpose: the same decisions are unit-tested without a device, and the erase itself lives in
 * one gated place ([DeviceRemoteWipe]) that this rule feeds.
 */
object RemoteWipeRequest {

    /** How long the owner has to confirm. Ten minutes is long enough to find the phone, short enough to expire. */
    const val WINDOW_MS = 10 * 60_000L

    /** The one word that confirms. Published so the sentence the owner is told cannot drift from it. */
    const val CONFIRM_ARGUMENT = "confirm"

    /** The one word that takes the request back. */
    const val CANCEL_ARGUMENT = "cancel"

    /** A request that is waiting for its confirmation. */
    data class Open(val requestedAtMs: Long, val expiresAtMs: Long) {

        /**
         * Whether a confirmation would still be honoured. A clock that jumped backwards does not
         * extend the window: the deadline was written once and is not recomputed.
         */
        fun isOpen(nowMs: Long): Boolean = nowMs >= requestedAtMs && nowMs <= expiresAtMs

        /** How long is left, or null when the window is over. */
        fun remainingMs(nowMs: Long): Long? = if (isOpen(nowMs)) expiresAtMs - nowMs else null
    }

    /** What one command means. */
    sealed interface Decision {
        /** The request opened a window that ends at [expiresAtMs]; nothing is erased. */
        data class Open(val expiresAtMs: Long) : Decision

        /** The confirmation arrived inside an open window and every gate was open: erase. */
        data object Erase : Decision

        /** The window passed before the confirmation: nothing is erased and the request is gone. */
        data object Expired : Decision

        /** A confirmation arrived with no request waiting. */
        data object NothingWaiting : Decision

        /** A gate closed inside the window, so the confirmation is refused. */
        data object GatesClosed : Decision

        /** The owner took the request back. */
        data object Cancel : Decision
    }

    /**
     * Decides what one `/wipe …` command does.
     *
     * @param argument the trimmed, lowercased argument after the verb.
     * @param open the request waiting, or null.
     * @param gatesOpen whether the switch, the plan, the armed flag and the administrator power are all
     *   in place *now*.
     */
    fun decide(
        argument: String,
        open: Open?,
        nowMs: Long,
        gatesOpen: Boolean,
        windowMs: Long = WINDOW_MS,
    ): Decision = when (argument) {
        CANCEL_ARGUMENT -> Decision.Cancel
        CONFIRM_ARGUMENT -> when {
            open == null -> Decision.NothingWaiting
            !open.isOpen(nowMs) -> Decision.Expired
            !gatesOpen -> Decision.GatesClosed
            else -> Decision.Erase
        }
        else -> Decision.Open(nowMs + windowMs)
    }
}
