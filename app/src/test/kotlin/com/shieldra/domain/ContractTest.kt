package com.shieldra.domain

import com.shieldra.delivery.DeliveryChannel
import com.shieldra.delivery.DeliveryOutcome
import com.shieldra.delivery.DeliveryReceipt
import com.shieldra.evidence.Evidence
import com.shieldra.evidence.EvidenceKind
import com.shieldra.evidence.LocationFreshness
import com.shieldra.evidence.LocationSource
import com.shieldra.evidence.LocationEvidence
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ContractTest {
    private val timestamp = Instant.parse("2026-09-20T22:00:00Z")

    @Test
    fun signal_strength_values_are_closed() {
        assertEquals(
            setOf(SignalStrength.STRONG, SignalStrength.MEDIUM, SignalStrength.WEAK),
            SignalStrength.entries.toSet(),
        )
    }

    @Test
    fun security_event_ids_are_independent() {
        val first = SecurityEvent.create(EventId.from(UUID.randomUUID()), timestamp)
        val second = SecurityEvent.create(EventId.from(UUID.randomUUID()), timestamp)

        assertNotEquals(first.id, second.id)
        assertEquals(SecurityEventType.CONFIRMED_SECURITY_EVENT, first.type)
        assertEquals(EventState.INITIATED, first.state)
    }

    @Test
    fun deferred_can_resume_but_terminal_states_cannot_exit() {
        assertEquals(
            StateTransitionResult.Allowed(EventState.COLLECTING),
            transition(EventState.DEFERRED, EventState.COLLECTING),
        )
        assertEquals(
            StateTransitionResult.Allowed(EventState.FAILED_FINAL),
            transition(EventState.DELIVERING, EventState.FAILED_FINAL),
        )
        assertTrue(transition(EventState.DEFERRED, EventState.DELIVERED) is StateTransitionResult.Rejected)
        assertTrue(transition(EventState.FAILED_FINAL, EventState.READY) is StateTransitionResult.Rejected)
        assertTrue(transition(EventState.DELIVERED, EventState.DEFERRED) is StateTransitionResult.Rejected)
    }

    @Test
    fun location_model_keeps_current_and_last_known_distinct() {
        val current = LocationEvidence(
            freshness = LocationFreshness.CURRENT,
            capturedAt = timestamp,
            age = Age(0),
            accuracyMeters = 8.5,
            source = LocationSource.PLATFORM_PROVIDER,
        )
        val stale = LocationEvidence(
            freshness = LocationFreshness.LAST_KNOWN,
            capturedAt = timestamp.minusSeconds(600),
            age = Age(600),
            accuracyMeters = 30.0,
            source = LocationSource.CACHED_PROVIDER,
        )

        assertNotEquals(current.freshness, stale.freshness)
        assertTrue(stale.age.seconds > 0)
        assertEquals(EvidenceKind.LOCATION, Evidence.Location("location-1", current).kind)
    }

    @Test
    fun delivery_receipt_represents_each_non_network_outcome() {
        val outcomes = DeliveryOutcome.entries.toSet()
        assertEquals(
            setOf(DeliveryOutcome.SUCCESS, DeliveryOutcome.FAILED, DeliveryOutcome.DEFERRED, DeliveryOutcome.SKIPPED),
            outcomes,
        )
        val receipt = DeliveryReceipt(
            channel = DeliveryChannel.EMAIL,
            outcome = DeliveryOutcome.DEFERRED,
            requestedAt = timestamp,
        )
        assertEquals(DeliveryOutcome.DEFERRED, receipt.outcome)
    }

    @Test
    fun authentication_operations_are_explicit_and_independent_of_ui() {
        assertEquals(
            setOf(
                SensitiveOperation.DISABLE_PROTECTION,
                SensitiveOperation.CONFIGURE_DELIVERY_CHANNEL,
                SensitiveOperation.DELETE_SENSITIVE_EVIDENCE,
                SensitiveOperation.CHANGE_SENSITIVE_SETTING,
            ),
            SensitiveOperation.entries.toSet(),
        )
        assertEquals(
            setOf(AuthenticationDecision.REQUIRED, AuthenticationDecision.GRANTED, AuthenticationDecision.DENIED),
            AuthenticationDecision.entries.toSet(),
        )
    }

    @Test
    fun age_rejects_negative_values() {
        assertFailsWith<IllegalArgumentException> { Age(-1) }
    }
}
