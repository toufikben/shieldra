package com.shieldra.app.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockPolicyTest {

    @Test
    fun `a cold start locks only when the lock is on`() {
        assertTrue(AppLockPolicy.lockOnColdStart(true))
        assertFalse(AppLockPolicy.lockOnColdStart(false))
    }

    @Test
    fun `a return is never locked when the lock is off`() {
        assertFalse(AppLockPolicy.shouldLockOnReturn(enabled = false, backgroundedAtMs = 0, nowMs = 10_000_000))
    }

    @Test
    fun `no recorded departure means nothing to re-lock`() {
        assertFalse(AppLockPolicy.shouldLockOnReturn(enabled = true, backgroundedAtMs = null, nowMs = 5_000))
    }

    @Test
    fun `returning inside the grace stays unlocked`() {
        assertFalse(AppLockPolicy.shouldLockOnReturn(true, backgroundedAtMs = 1_000, nowMs = 1_000 + 29_999))
    }

    @Test
    fun `returning after the grace locks`() {
        assertTrue(AppLockPolicy.shouldLockOnReturn(true, backgroundedAtMs = 1_000, nowMs = 1_000 + AppLockPolicy.GRACE_MS))
    }

    @Test
    fun `a clock that went backwards locks instead of trusting it`() {
        assertTrue(AppLockPolicy.shouldLockOnReturn(true, backgroundedAtMs = 9_000, nowMs = 2_000))
    }
}
