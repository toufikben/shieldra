package com.shieldra.app.presentation.guard

import com.shieldra.app.presentation.model.GuardKind
import com.shieldra.app.presentation.model.GuardStatus
import com.shieldra.app.presentation.model.ProtectionVisualState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The guard projection must be honest: SHIELDRA has no live sensor detection or
 * background monitoring yet (phase 5 / G3 policy are still blocked), so the UI
 * can never claim a device is "Protected" or that a guard is actively "Active".
 * These tests lock that: the only states that exist are "not running" and
 * "storage present but idle", and neither surfaces a fabricated active guard.
 */
class LockGuardDecisionTest {

    @Test
    fun `storage not installed projects NotActive`() {
        assertEquals(LockGuardState.NotActive, LockGuardDecision.decide(storageInstalled = false))
    }

    @Test
    fun `storage installed projects StorageIdle not Protected`() {
        assertEquals(LockGuardState.StorageIdle, LockGuardDecision.decide(storageInstalled = true))
    }

    @Test
    fun `not active maps to the disabled visual state`() {
        assertEquals(ProtectionVisualState.Disabled, LockGuardDecision.protectionState(LockGuardState.NotActive))
    }

    @Test
    fun `storage idle maps to an attention visual state never protected`() {
        val state = LockGuardDecision.protectionState(LockGuardState.StorageIdle)
        assertEquals(ProtectionVisualState.Attention, state)
        assertFalse(state == ProtectionVisualState.Protected, "must never claim protection without a detector")
    }

    @Test
    fun `every guard kind is covered and none is falsely active`() {
        for (lockState in listOf(LockGuardState.NotActive, LockGuardState.StorageIdle)) {
            val statuses = LockGuardDecision.guardStatuses(lockState)
            assertTrue(
                GuardKind.entries.all { statuses.containsKey(it) },
                "all guard kinds must be projected, was: $statuses",
            )
            assertFalse(
                statuses.values.any { it == GuardStatus.Active || it == GuardStatus.Signal },
                "no guard may be shown as Active/Signal without detection, was: $statuses",
            )
        }
    }

    // Batch F / §31: a "last check" timestamp is evidence that something checked.
    // No detector runs, so the dashboard has no such evidence and must print none.
    @Test
    fun `no state may report a last check because nothing has ever checked`() {
        assertNull(LockGuardDecision.lastCheckSeconds(LockGuardState.NotActive))
        assertNull(LockGuardDecision.lastCheckSeconds(LockGuardState.StorageIdle))
    }
}
