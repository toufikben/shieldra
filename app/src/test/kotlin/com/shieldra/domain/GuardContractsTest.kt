package com.shieldra.domain

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GuardContractsTest {
    private val observedAt = Instant.parse("2026-09-22T20:00:00Z")

    @Test
    fun approved_rules_expose_only_the_decided_guard_values() {
        assertEquals(Duration.ofMinutes(2), ApprovedGuardRules.lockConfirmationWindow)
        assertEquals(Duration.ofMinutes(10), ApprovedGuardRules.lockCounterResetWindow)
        assertEquals(Duration.ofMinutes(5), ApprovedGuardRules.lockCooldown)
        assertEquals(Duration.ofSeconds(3), ApprovedGuardRules.motionStrongDuration)
        assertEquals(Duration.ofSeconds(10), ApprovedGuardRules.motionRepetitionWindow)
        assertEquals(2, ApprovedGuardRules.lockAttemptsToConfirm)
        assertEquals(2, ApprovedGuardRules.motionOccurrencesToConfirm)
    }

    @Test
    fun typed_observations_match_only_their_guard_kind() {
        val panic = GuardSignal(GuardKind.PANIC, observedAt, GuardObservation.PanicActivated)
        val wrongShape = GuardSignal(GuardKind.PANIC, observedAt, GuardObservation.FailedPinAttempt)

        assertTrue(panic.isShapeValid())
        assertFalse(wrongShape.isShapeValid())
    }

    @Test
    fun motion_observation_rejects_negative_duration_or_occurrences() {
        assertFailsWith<IllegalArgumentException> {
            GuardObservation.Motion(Duration.ofSeconds(-1), 0, SensorQuality.VALID)
        }
        assertFailsWith<IllegalArgumentException> {
            GuardObservation.Motion(Duration.ZERO, -1, SensorQuality.VALID)
        }
    }

    @Test
    fun confirmed_result_requires_severity_but_signal_only_does_not() {
        val signalOnly = GuardConfirmation(GuardConfirmationStatus.SIGNAL_ONLY)
        assertEquals(null, signalOnly.severity)

        assertFailsWith<IllegalArgumentException> {
            GuardConfirmation(GuardConfirmationStatus.CONFIRMED)
        }
        assertEquals(
            GuardSeverity.CRITICAL,
            GuardConfirmation(GuardConfirmationStatus.CONFIRMED, GuardSeverity.CRITICAL).severity,
        )
    }

    @Test
    fun battery_percentage_is_not_encoded_as_a_security_event_rule() {
        val observation = GuardObservation.BatteryPowerState(abnormal = false)
        val signal = GuardSignal(GuardKind.BATTERY, observedAt, observation)
        val result = GuardConfirmation(GuardConfirmationStatus.NOT_A_SECURITY_EVENT)

        assertTrue(signal.isShapeValid())
        assertEquals(GuardConfirmationStatus.NOT_A_SECURITY_EVENT, result.status)
    }
}
