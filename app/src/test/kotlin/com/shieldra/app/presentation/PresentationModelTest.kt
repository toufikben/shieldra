package com.shieldra.app.presentation

import com.shieldra.app.presentation.model.ChannelId
import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.app.presentation.model.EventType
import com.shieldra.app.presentation.model.GuardKind
import com.shieldra.app.presentation.model.LocationKind
import com.shieldra.app.presentation.model.ProtectionVisualState
import kotlin.test.Test
import kotlin.test.assertEquals

class PresentationModelTest {
    @Test
    fun phase3_model_enums_cover_frozen_visual_states() {
        assertEquals(
            setOf(ProtectionVisualState.Protected, ProtectionVisualState.Suspicious, ProtectionVisualState.SecurityEvent, ProtectionVisualState.Attention, ProtectionVisualState.Disabled),
            ProtectionVisualState.entries.toSet(),
        )
        assertEquals(setOf(GuardKind.Lock, GuardKind.Motion, GuardKind.Sim, GuardKind.Panic, GuardKind.Battery), GuardKind.entries.toSet())
        assertEquals(setOf(EventType.FailedUnlock, EventType.Motion, EventType.SimChange, EventType.Panic, EventType.Battery), EventType.entries.toSet())
    }

    @Test
    fun phase3_deferred_status_vocabulary_is_explicit() {
        assertEquals(DeliveryStatus.Deferred, DeliveryStatus.entries.first { it.name == "Deferred" })
        assertEquals(LocationKind.LastKnown, LocationKind.entries.first { it.name == "LastKnown" })
        assertEquals(ChannelId.Email, ChannelId.entries.first { it.name == "Email" })
    }
}
