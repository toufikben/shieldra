package com.shieldra.protection

import com.shieldra.domain.SecurityEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A-33 (owner report 2026-11-08): every wrong attempt at or past the threshold is captured, but only
 * one message speaks for an unlock session. The rule is pure so this cannot regress unnoticed.
 */
class LockAlertPolicyTest {

    @Test
    fun the_threshold_attempt_in_a_new_incident_announces() {
        assertTrue(
            LockAlertPolicy.announces(
                type = SecurityEventType.LOCK_CONFIRMED_EVENT,
                incidentIsNew = true,
                failedAttempts = 2,
                threshold = 2,
            ),
        )
    }

    @Test
    fun later_attempts_in_the_same_incident_stay_silent() {
        for (attempt in 3..8) {
            assertFalse(
                LockAlertPolicy.announces(
                    type = SecurityEventType.LOCK_CONFIRMED_EVENT,
                    incidentIsNew = false,
                    failedAttempts = attempt,
                    threshold = 2,
                ),
                "attempt $attempt must not send another signal for the same incident",
            )
        }
    }

    @Test
    fun a_burst_of_guesses_in_one_incident_produces_exactly_one_signal() {
        // The owner's report (2026-11-08): the signal arrived after every wrong attempt. One burst,
        // one incident, one message — the later attempts still capture their evidence silently.
        val announced = (2..9).count { attempt ->
            LockAlertPolicy.announces(
                type = SecurityEventType.LOCK_CONFIRMED_EVENT,
                incidentIsNew = attempt == 2,
                failedAttempts = attempt,
                threshold = 2,
            )
        }

        assertEquals(1, announced, "a burst of eight wrong attempts must produce one signal, not eight")
    }

    @Test
    fun a_new_incident_after_the_grouping_window_announces_again() {
        assertTrue(
            LockAlertPolicy.announces(
                type = SecurityEventType.LOCK_CONFIRMED_EVENT,
                incidentIsNew = true,
                failedAttempts = 7,
                threshold = 2,
            ),
        )
    }

    @Test
    fun a_fresh_session_that_joins_an_open_incident_still_announces_at_its_threshold() {
        // The owner mistyped, unlocked correctly, then mistyped again inside ten minutes: the History
        // row may still be the same incident, but this is a new attempt session and one message is due.
        assertTrue(
            LockAlertPolicy.announces(
                type = SecurityEventType.LOCK_CONFIRMED_EVENT,
                incidentIsNew = false,
                failedAttempts = 2,
                threshold = 2,
            ),
        )
    }

    @Test
    fun the_admin_removal_tamper_always_announces() {
        assertTrue(
            LockAlertPolicy.announces(
                type = SecurityEventType.LOCK_CONFIRMED_EVENT,
                incidentIsNew = false,
                failedAttempts = null,
                threshold = 2,
            ),
        )
    }

    @Test
    fun a_lock_threshold_of_one_announces_its_first_attempt_only() {
        assertTrue(
            LockAlertPolicy.announces(
                type = SecurityEventType.LOCK_CONFIRMED_EVENT,
                incidentIsNew = true,
                failedAttempts = 1,
                threshold = 1,
            ),
        )
        assertFalse(
            LockAlertPolicy.announces(
                type = SecurityEventType.LOCK_CONFIRMED_EVENT,
                incidentIsNew = false,
                failedAttempts = 2,
                threshold = 1,
            ),
        )
    }

    @Test
    fun a_threshold_outside_the_supported_range_is_clamped_like_the_counter() {
        assertTrue(
            LockAlertPolicy.announces(
                type = SecurityEventType.LOCK_CONFIRMED_EVENT,
                incidentIsNew = false,
                failedAttempts = FailedUnlockCounter.MAX_THRESHOLD,
                threshold = 99,
            ),
        )
        assertFalse(
            LockAlertPolicy.announces(
                type = SecurityEventType.LOCK_CONFIRMED_EVENT,
                incidentIsNew = false,
                failedAttempts = FailedUnlockCounter.MAX_THRESHOLD + 1,
                threshold = 99,
            ),
        )
    }

    @Test
    fun non_lock_guards_keep_their_existing_alerting() {
        val others = SecurityEventType.entries.filterNot { it == SecurityEventType.LOCK_CONFIRMED_EVENT }
        for (type in others) {
            assertTrue(
                LockAlertPolicy.announces(
                    type = type,
                    incidentIsNew = false,
                    failedAttempts = 5,
                    threshold = 2,
                ),
                "${type.name} must not be silenced by the lock-alert policy",
            )
        }
    }
}
