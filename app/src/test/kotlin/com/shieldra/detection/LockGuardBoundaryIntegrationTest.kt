package com.shieldra.detection

import com.shieldra.domain.Clock
import com.shieldra.domain.DefaultProtectionStateEngine
import com.shieldra.domain.EventId
import com.shieldra.domain.EventState
import com.shieldra.domain.GuardConfirmationStatus
import com.shieldra.domain.GuardSeverity
import com.shieldra.domain.IdentityProvider
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class LockGuardBoundaryIntegrationTest {
    private val firstObservedAt = Instant.parse("2026-09-23T07:00:00Z")
    private var now = firstObservedAt
    private val clock = Clock { now }
    private val identities = IdentityProvider { EventId.from(UUID.randomUUID()) }

    @Test
    fun two_verified_callbacks_flow_to_one_high_lock_event() {
        val engine = DefaultProtectionStateEngine()

        val first = verifiedCallback(firstObservedAt)
        val firstEvaluation = engine.evaluate(first.signal, clock, identities)
        assertEquals(GuardConfirmationStatus.SIGNAL_ONLY, firstEvaluation.confirmation.status)

        val secondObservedAt = firstObservedAt.plusSeconds(60)
        now = secondObservedAt
        val second = verifiedCallback(secondObservedAt)
        val secondEvaluation = engine.evaluate(second.signal, clock, identities)

        assertEquals(GuardConfirmationStatus.CONFIRMED, secondEvaluation.confirmation.status)
        assertEquals(GuardSeverity.HIGH, secondEvaluation.confirmation.severity)
        assertNotNull(secondEvaluation.event)
        assertEquals(EventState.INITIATED, secondEvaluation.event?.state)
    }

    @Test
    fun unavailable_capability_stops_before_the_protection_engine() {
        val result = LockGuardBoundary.verifiedFailedPinCallback(
            capability = LockGuardCapabilityState.PERMISSION_OR_PROVISIONING_REQUIRED,
            observedAt = firstObservedAt,
        )

        val unavailable = assertIs<LockGuardBoundaryResult.Unavailable>(result)
        assertEquals(
            LockGuardCapabilityState.PERMISSION_OR_PROVISIONING_REQUIRED,
            unavailable.state,
        )
    }

    private fun verifiedCallback(observedAt: Instant): LockGuardBoundaryResult.VerifiedFailedPinCallback =
        assertIs<LockGuardBoundaryResult.VerifiedFailedPinCallback>(
            LockGuardBoundary.verifiedFailedPinCallback(
                capability = LockGuardCapabilityState.AVAILABLE,
                observedAt = observedAt,
            ),
        )
}
