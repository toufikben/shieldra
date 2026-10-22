package com.shieldra.app.presentation.guard

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectionScoreTest {

    private fun inputs(
        armed: Boolean = true,
        lock: Boolean = false,
        motion: Boolean = false,
        sim: Boolean = false,
        batteryGuard: Boolean = false,
        camera: Boolean = false,
        location: Boolean = false,
        notifications: Boolean = false,
        deviceAdmin: Boolean = false,
        batteryExempt: Boolean = false,
        channel: Boolean = false,
    ) = ProtectionScore.Inputs(
        armed = armed,
        lockEnabled = lock,
        motionMonitoring = motion,
        simEnabled = sim,
        batteryEnabled = batteryGuard,
        cameraGranted = camera,
        locationGranted = location,
        notificationsReady = notifications,
        deviceAdminActive = deviceAdmin,
        batteryExempt = batteryExempt,
        anyChannelActive = channel,
    )

    @Test
    fun `everything on and granted scores 100`() {
        assertEquals(
            100,
            ProtectionScore.score(
                inputs(
                    lock = true, motion = true, sim = true, batteryGuard = true,
                    camera = true, location = true, notifications = true,
                    deviceAdmin = true, batteryExempt = true, channel = true,
                ),
            ),
        )
    }

    @Test
    fun `disarmed scores 0 even with every toggle on`() {
        assertEquals(
            0,
            ProtectionScore.score(
                inputs(
                    armed = false,
                    lock = true, motion = true, sim = true, batteryGuard = true,
                    camera = true, location = true, notifications = true,
                    deviceAdmin = true, batteryExempt = true, channel = true,
                ),
            ),
        )
    }

    @Test
    fun `armed but nothing configured scores 0`() {
        assertEquals(0, ProtectionScore.score(inputs()))
    }

    @Test
    fun `a lock guard with camera and a live channel sums its weights`() {
        assertEquals(
            ProtectionScore.W_LOCK + ProtectionScore.W_CAMERA + ProtectionScore.W_CHANNEL,
            ProtectionScore.score(inputs(lock = true, camera = true, channel = true)),
        )
    }

    @Test
    fun `a live channel alone is worth its weight`() {
        assertEquals(ProtectionScore.W_CHANNEL, ProtectionScore.score(inputs(channel = true)))
    }
}
