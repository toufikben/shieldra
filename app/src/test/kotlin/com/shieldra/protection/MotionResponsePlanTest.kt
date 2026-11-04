package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionResponsePlanTest {

    @Test
    fun `a silent capture still locks the screen`() {
        val plan = MotionResponsePlan.of(ProtectionSettings.MotionResponse.SILENT)
        assertFalse(plan.sirenNow)
        assertFalse(plan.waitForOwnerUnlock)
        assertTrue(plan.lockAtOnce)
        assertFalse(plan.lockAfterGrace)
    }

    @Test
    fun `a loud siren locks the screen at once`() {
        val plan = MotionResponsePlan.of(ProtectionSettings.MotionResponse.SIREN)
        assertTrue(plan.sirenNow)
        assertFalse(plan.waitForOwnerUnlock)
        assertTrue(plan.lockAtOnce)
    }

    @Test
    fun `grace waits for the owner unlock before the siren and the lock`() {
        val plan = MotionResponsePlan.of(ProtectionSettings.MotionResponse.GRACE_THEN_SIREN)
        assertTrue(plan.waitForOwnerUnlock)
        assertFalse(plan.sirenNow)
        assertFalse(plan.lockAtOnce)
        assertTrue(plan.sirenAfterGrace)
        assertTrue(plan.lockAfterGrace)
    }
}
