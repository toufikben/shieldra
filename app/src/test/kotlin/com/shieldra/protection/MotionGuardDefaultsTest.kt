package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertTrue

class MotionGuardDefaultsTest {

    @Test
    fun `the motion guard is on by default like the lock and SIM guards`() {
        assertTrue(ProtectionSettings().motionEnabled)
    }
}
