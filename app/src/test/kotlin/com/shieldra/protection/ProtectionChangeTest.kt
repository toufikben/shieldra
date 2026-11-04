package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtectionChangeTest {

    private val on = ProtectionSettings(armed = true, simEnabled = true, geofenceEnabled = true)

    @Test
    fun `switching a guard off weakens protection`() {
        assertTrue(ProtectionChange.weakens(on, on.copy(simEnabled = false)))
        assertTrue(ProtectionChange.weakens(on, on.copy(geofenceEnabled = false)))
        assertTrue(ProtectionChange.weakens(on, on.copy(motionEnabled = false)))
    }

    @Test
    fun `disarming weakens protection`() {
        assertTrue(ProtectionChange.weakens(on, on.copy(armed = false)))
    }

    @Test
    fun `allowing more failed unlocks weakens protection`() {
        assertTrue(ProtectionChange.weakens(on, on.copy(lockThreshold = on.lockThreshold + 1)))
    }

    @Test
    fun `turning things on or tightening never asks`() {
        val off = ProtectionSettings(armed = false, simEnabled = false)
        assertFalse(ProtectionChange.weakens(off, off.copy(armed = true, simEnabled = true)))
        assertFalse(ProtectionChange.weakens(on, on.copy(lockThreshold = maxOf(1, on.lockThreshold - 1))))
        assertFalse(ProtectionChange.weakens(on, on))
    }
}
