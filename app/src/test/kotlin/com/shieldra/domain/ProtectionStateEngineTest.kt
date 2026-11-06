package com.shieldra.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtectionStateEngineTest {
    private val start = Instant.parse("2026-09-22T20:00:00Z")
    private var now = start
    private val clock = Clock { now }
    private val identities = IdentityProvider { EventId.from(UUID.randomUUID()) }

    @Test
    fun motion_requires_valid_quality_and_approved_persistence_or_repetition() {
        val engine = DefaultProtectionStateEngine()
        val noisy = engine.evaluate(
            GuardSignal(
                GuardKind.MOTION,
                start,
                GuardObservation.Motion(
                    duration = Duration.ofSeconds(5),
                    suspiciousOccurrences = 1,
                    sensorQuality = SensorQuality.NOISY,
                ),
            ),
            clock,
            identities,
        )
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, noisy.confirmation.status)

        val persistent = engine.evaluate(
            GuardSignal(
                GuardKind.MOTION,
                start,
                GuardObservation.Motion(
                    duration = ApprovedGuardRules.motionStrongDuration,
                    suspiciousOccurrences = 0,
                    sensorQuality = SensorQuality.VALID,
                ),
            ),
            clock,
            identities,
        )
        assertEquals(GuardConfirmationStatus.CONFIRMED, persistent.confirmation.status)
    }

    @Test
    fun motion_repetition_requires_two_valid_occurrences_within_ten_seconds() {
        val engine = DefaultProtectionStateEngine()
        val confirmed = engine.evaluate(
            GuardSignal(
                GuardKind.MOTION,
                start,
                GuardObservation.Motion(
                    duration = Duration.ZERO,
                    suspiciousOccurrences = 2,
                    suspiciousOccurrencesWithin = ApprovedGuardRules.motionRepetitionWindow,
                    sensorQuality = SensorQuality.VALID,
                ),
            ),
            clock,
            identities,
        )
        val outsideWindow = engine.evaluate(
            GuardSignal(
                GuardKind.MOTION,
                start,
                GuardObservation.Motion(
                    duration = Duration.ZERO,
                    suspiciousOccurrences = 2,
                    suspiciousOccurrencesWithin = ApprovedGuardRules.motionRepetitionWindow.plusNanos(1),
                    sensorQuality = SensorQuality.VALID,
                ),
            ),
            clock,
            identities,
        )

        assertEquals(GuardConfirmationStatus.CONFIRMED, confirmed.confirmation.status)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, outsideWindow.confirmation.status)
    }

    @Test
    fun sim_and_panic_are_immediate_confirmed_events() {
        val engine = DefaultProtectionStateEngine()
        val sim = engine.evaluate(
            GuardSignal(GuardKind.SIM, start, GuardObservation.SimSubscriptionChanged),
            clock,
            identities,
        )
        val panic = engine.evaluate(
            GuardSignal(GuardKind.PANIC, start, GuardObservation.PanicActivated),
            clock,
            identities,
        )
        assertEquals(GuardSeverity.HIGH, sim.confirmation.severity)
        assertEquals(GuardSeverity.CRITICAL, panic.confirmation.severity)
        assertTrue(sim.event != null && panic.event != null)
    }

    @Test
    fun battery_observation_never_confirms_without_an_approved_anomaly_rule() {
        val engine = DefaultProtectionStateEngine()
        val result = engine.evaluate(
            GuardSignal(
                GuardKind.BATTERY,
                start,
                GuardObservation.BatteryPowerState(abnormal = true),
            ),
            clock,
            identities,
        )
        assertEquals(GuardConfirmationStatus.NOT_A_SECURITY_EVENT, result.confirmation.status)
        assertNull(result.event)
    }

    @Test
    fun a_wrong_observation_shape_is_refused_without_creating_an_event() {
        val engine = DefaultProtectionStateEngine()
        val result = engine.evaluate(
            GuardSignal(
                kind = GuardKind.SIM,
                observedAt = start,
                observation = GuardObservation.PanicActivated,
            ),
            clock,
            identities,
        )

        assertEquals(GuardConfirmationStatus.NOT_A_SECURITY_EVENT, result.confirmation.status)
        assertNull(result.event)
    }
}
