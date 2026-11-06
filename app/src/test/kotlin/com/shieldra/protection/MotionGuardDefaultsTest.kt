package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertTrue

class MotionGuardDefaultsTest {

    @Test
    fun `the motion guard is on by default like the SIM guard`() {
        assertTrue(ProtectionSettings().motionEnabled)
    }
}
