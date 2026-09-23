package com.shieldra.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtectionStateEngineTest {
    private val start = Instant.parse("2026-09-22T20:00:00Z")
    private var now = start
    private val clock = Clock { now }
    private val identities = IdentityProvider { EventId.from(UUID.randomUUID()) }

    @Test
    fun one_lock_failure_is_signal_only_and_two_within_window_confirm() {
        val engine = DefaultProtectionStateEngine()
        val first = engine.evaluate(lockSignal(start), clock, identities)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, first.confirmation.status)
        assertNull(first.event)

        now = start.plusSeconds(60)
        val second = engine.evaluate(lockSignal(now), clock, identities)
        assertEquals(GuardConfirmationStatus.CONFIRMED, second.confirmation.status)
        assertEquals(GuardSeverity.HIGH, second.confirmation.severity)
        assertNotNull(second.event)
        assertEquals(GuardSeverity.HIGH, second.event?.severity)
    }

    @Test
    fun lock_sequence_resets_after_ten_minutes_and_same_sequence_is_cooled_down() {
        val engine = DefaultProtectionStateEngine()
        engine.evaluate(lockSignal(start), clock, identities)
        now = start.plusSeconds(60)
        val confirmed = engine.evaluate(lockSignal(now), clock, identities)
        assertEquals(GuardConfirmationStatus.CONFIRMED, confirmed.confirmation.status)

        now = start.plusSeconds(180)
        val cooled = engine.evaluate(lockSignal(now), clock, identities)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, cooled.confirmation.status)

        now = start.plusSeconds(11 * 60)
        val afterReset = engine.evaluate(lockSignal(now), clock, identities)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, afterReset.confirmation.status)
    }

    @Test
    fun lock_attempt_at_exact_two_minute_boundary_confirms() {
        val engine = DefaultProtectionStateEngine()
        engine.evaluate(lockSignal(start), clock, identities)
        now = start.plus(ApprovedGuardRules.lockConfirmationWindow)

        val result = engine.evaluate(lockSignal(now), clock, identities)

        assertEquals(GuardConfirmationStatus.CONFIRMED, result.confirmation.status)
        assertEquals(GuardSeverity.HIGH, result.confirmation.severity)
    }

    @Test
    fun lock_attempt_at_exact_five_minute_boundary_starts_a_new_sequence() {
        val engine = DefaultProtectionStateEngine()
        engine.evaluate(lockSignal(start), clock, identities)
        now = start.plusSeconds(60)
        engine.evaluate(lockSignal(now), clock, identities)
        now = start.plusSeconds(60).plus(ApprovedGuardRules.lockCooldown)

        val result = engine.evaluate(lockSignal(now), clock, identities)

        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, result.confirmation.status)
    }

    @Test
    fun lock_attempt_at_exact_ten_minute_boundary_does_not_reuse_old_sequence() {
        val engine = DefaultProtectionStateEngine()
        engine.evaluate(lockSignal(start), clock, identities)
        now = start.plus(ApprovedGuardRules.lockCounterResetWindow)

        val result = engine.evaluate(lockSignal(now), clock, identities)

        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, result.confirmation.status)
        assertNull(result.event)
    }

    @Test
    fun lock_guard_rejects_wrong_observation_shape_without_creating_an_event() {
        val engine = DefaultProtectionStateEngine()
        val result = engine.evaluate(
            GuardSignal(
                kind = GuardKind.LOCK,
                observedAt = start,
                observation = GuardObservation.PanicActivated,
            ),
            clock,
            identities,
        )

        assertEquals(GuardConfirmationStatus.NOT_A_SECURITY_EVENT, result.confirmation.status)
        assertNull(result.event)
    }

    @Test
    fun motion_requires_valid_quality_and_approved_persistence_or_repetition() {
        val engine = DefaultProtectionStateEngine()
        val noisy = engine.evaluate(
            GuardSignal(
                GuardKind.MOTION,
                start,
                GuardObservation.Motion(Duration.ofSeconds(5), 1, SensorQuality.NOISY),
            ),
            clock,
            identities,
        )
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, noisy.confirmation.status)

        val persistent = engine.evaluate(
            GuardSignal(
                GuardKind.MOTION,
                start,
                GuardObservation.Motion(ApprovedGuardRules.motionStrongDuration, 0, SensorQuality.VALID),
            ),
            clock,
            identities,
        )
        assertEquals(GuardConfirmationStatus.CONFIRMED, persistent.confirmation.status)
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

    private fun lockSignal(at: Instant): GuardSignal =
        GuardSignal(GuardKind.LOCK, at, GuardObservation.FailedPinAttempt)
}
