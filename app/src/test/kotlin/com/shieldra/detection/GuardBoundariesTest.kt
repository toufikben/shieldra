package com.shieldra.detection

import com.shieldra.domain.GuardKind
import com.shieldra.domain.GuardObservation
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GuardBoundariesTest {
    private val observedAt = Instant.parse("2026-09-23T07:00:00Z")

    @Test
    fun available_capability_emits_only_a_verified_failed_pin_signal() {
        val result = LockGuardBoundary.verifiedFailedPinCallback(
            capability = LockGuardCapabilityState.AVAILABLE,
            observedAt = observedAt,
        )

        val callback = assertIs<LockGuardBoundaryResult.VerifiedFailedPinCallback>(result)
        assertEquals(GuardKind.LOCK, callback.signal.kind)
        assertEquals(observedAt, callback.signal.observedAt)
        assertEquals(GuardObservation.FailedPinAttempt, callback.signal.observation)
    }

    @Test
    fun unavailable_capability_never_emits_a_lock_signal() {
        LockGuardCapabilityState.entries
            .filterNot { it == LockGuardCapabilityState.AVAILABLE }
            .forEach { state ->
                val result = LockGuardBoundary.verifiedFailedPinCallback(state, observedAt)
                val unavailable = assertIs<LockGuardBoundaryResult.Unavailable>(result)
                assertEquals(state, unavailable.state)
                assertTrue(unavailable.reason.isNotBlank())
            }
    }

    @Test
    fun unavailable_result_cannot_claim_available_capability() {
        val result = LockGuardBoundaryResult.Unavailable(
            state = LockGuardCapabilityState.NOT_DEVICE_ADMIN,
            reason = "Device administrator is not provisioned",
        )

        assertEquals(LockGuardCapabilityState.NOT_DEVICE_ADMIN, result.state)
        assertTrue(result.reason.contains("not provisioned"))
    }
}
