package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionGuardDefaultsTest {

    @Test
    fun `the motion guard is on by default like the SIM guard`() {
        assertTrue(ProtectionSettings().motionEnabled)
    }

    // A-33 restored: a fresh install is ready to hear the thief without the owner finding a switch —
    // the guard is on and waits for the owner's two-attempt default.
    @Test
    fun `the lock guard is on by default at the two-attempt threshold`() {
        val defaults = ProtectionSettings()

        assertTrue(defaults.lockEnabled)
        assertEquals(FailedUnlockCounter.DEFAULT_THRESHOLD, defaults.lockThreshold)
        assertEquals(2, defaults.lockThreshold)
    }
}
