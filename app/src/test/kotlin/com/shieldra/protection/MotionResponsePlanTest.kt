package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionResponsePlanTest {

    @Test
    fun `a silent capture sends evidence without noise`() {
        val plan = MotionResponsePlan.of(ProtectionSettings.MotionResponse.SILENT)
        assertFalse(plan.sirenNow)
        assertFalse(plan.waitForOwnerUnlock)
        assertFalse(plan.sirenAfterGrace)
    }

    @Test
    fun `a loud siren sounds at once`() {
        val plan = MotionResponsePlan.of(ProtectionSettings.MotionResponse.SIREN)
        assertTrue(plan.sirenNow)
        assertFalse(plan.waitForOwnerUnlock)
        assertFalse(plan.sirenAfterGrace)
    }

    @Test
    fun `grace waits for the owner unlock before the siren`() {
        val plan = MotionResponsePlan.of(ProtectionSettings.MotionResponse.GRACE_THEN_SIREN)
        assertTrue(plan.waitForOwnerUnlock)
        assertFalse(plan.sirenNow)
        assertTrue(plan.sirenAfterGrace)
    }
}
