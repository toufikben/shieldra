package com.shieldra.protection

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * G-8/G-9: the setup form keeps its unsaved draft with `rememberSaveable`, which can only store a
 * value the platform can put in a Bundle. This proves the whole settings object crosses that
 * boundary — every field, both enums and the plan — instead of trusting a custom Saver to list two
 * dozen fields correctly.
 */
class ProtectionSettingsSerializableTest {

    @Test
    fun the_whole_settings_object_crosses_a_bundle_boundary() {
        val settings = ProtectionSettings(
            armed = true,
            simEnabled = false,
            batteryThreshold = 17,
            countdownIntervalMs = 90_000,
            motionResponse = ProtectionSettings.MotionResponse.GRACE_THEN_SIREN,
            motionGraceSeconds = 12,
            ownerContact = "owner@example.com",
            lostMessage = "Call me",
            geofenceEnabled = true,
            tier = com.shieldra.billing.Tier.FREE,
        )

        val bytes = ByteArrayOutputStream().use { buffer ->
            ObjectOutputStream(buffer).use { it.writeObject(settings) }
            buffer.toByteArray()
        }
        val restored = ByteArrayInputStream(bytes).use { input ->
            ObjectInputStream(input).use { it.readObject() as ProtectionSettings }
        }

        assertEquals(settings, restored)
    }
}
