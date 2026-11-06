package com.shieldra.app.presentation.guard

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectionScoreTest {

    private fun inputs(
        armed: Boolean = true,
        motion: Boolean = false,
        sim: Boolean = false,
        batteryGuard: Boolean = false,
        camera: Boolean = false,
        location: Boolean = false,
        notifications: Boolean = false,
        batteryExempt: Boolean = false,
        channel: Boolean = false,
    ) = ProtectionScore.Inputs(
        armed = armed,
        motionMonitoring = motion,
        simEnabled = sim,
        batteryEnabled = batteryGuard,
        cameraGranted = camera,
        locationGranted = location,
        notificationsReady = notifications,
        batteryExempt = batteryExempt,
        anyChannelActive = channel,
    )

    @Test
    fun `everything on and granted scores 100`() {
        assertEquals(
            100,
            ProtectionScore.score(
                inputs(
                    motion = true, sim = true, batteryGuard = true,
                    camera = true, location = true, notifications = true,
                    batteryExempt = true, channel = true,
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
                    motion = true, sim = true, batteryGuard = true,
                    camera = true, location = true, notifications = true,
                    batteryExempt = true, channel = true,
                ),
            ),
        )
    }

    @Test
    fun `armed but nothing configured scores 0`() {
        assertEquals(0, ProtectionScore.score(inputs()))
    }

    @Test
    fun `a motion guard with camera and a live channel sums its weights`() {
        assertEquals(
            ProtectionScore.W_MOTION + ProtectionScore.W_CAMERA + ProtectionScore.W_CHANNEL,
            ProtectionScore.score(inputs(motion = true, camera = true, channel = true)),
        )
    }

    @Test
    fun `a live channel alone is worth its weight`() {
        assertEquals(ProtectionScore.W_CHANNEL, ProtectionScore.score(inputs(channel = true)))
    }

    @Test
    fun `the weights still add up to a full hundred`() {
        assertEquals(
            100,
            ProtectionScore.W_MOTION + ProtectionScore.W_SIM + ProtectionScore.W_BATTERY_GUARD +
                ProtectionScore.W_CHANNEL + ProtectionScore.W_CAMERA + ProtectionScore.W_LOCATION +
                ProtectionScore.W_NOTIFICATIONS + ProtectionScore.W_BATTERY_EXEMPT,
        )
    }
}
