package com.shieldra.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignalTest {
    @Test
    fun signal_preserves_typed_fields_and_context() {
        val timestamp = Instant.parse("2026-09-20T22:00:00Z")
        val signal = Signal(
            type = SignalType.MOTION,
            strength = SignalStrength.MEDIUM,
            timestamp = timestamp,
            context = mapOf("source" to "test"),
        )

        assertEquals(SignalType.MOTION, signal.type)
        assertEquals(SignalStrength.MEDIUM, signal.strength)
        assertEquals(timestamp, signal.timestamp)
        assertTrue(signal.context.containsKey("source"))
    }

    @Test
    fun signal_strength_has_only_the_declared_values() {
        assertEquals(
            setOf(SignalStrength.STRONG, SignalStrength.MEDIUM, SignalStrength.WEAK),
            SignalStrength.entries.toSet(),
        )
    }
}
