package com.shieldra.app.presentation

import com.shieldra.app.presentation.model.ChannelId
import com.shieldra.app.presentation.model.DeliveryStatus
import com.shieldra.app.presentation.model.EventType
import com.shieldra.app.presentation.model.EventUiModel
import com.shieldra.app.presentation.model.GuardKind
import com.shieldra.app.presentation.model.HistoryFilterState
import com.shieldra.app.presentation.model.HistoryStatusFilter
import com.shieldra.app.presentation.model.HistoryTypeFilter
import com.shieldra.app.presentation.model.LocationKind
import com.shieldra.app.presentation.model.ProtectionVisualState
import com.shieldra.app.presentation.screen.history.filterHistoryEvents
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

    @Test
    fun history_filters_apply_type_and_delivery_status() {
        val events = listOf(
            EventUiModel("lock", EventType.FailedUnlock, "", "", "", DeliveryStatus.Delivered, false, false),
            EventUiModel("motion", EventType.Motion, "", "", "", DeliveryStatus.Deferred, false, false),
        )

        assertEquals(
            listOf("lock"),
            filterHistoryEvents(
                events,
                HistoryFilterState(HistoryTypeFilter.Lock, HistoryStatusFilter.Delivered),
            ).map { it.id },
        )
        assertEquals(
            emptyList(),
            filterHistoryEvents(
                events,
                HistoryFilterState(HistoryTypeFilter.Panic, HistoryStatusFilter.All),
            ),
        )
    }

    @Test
    fun history_filters_cover_each_declared_type_and_status() {
        val events = listOf(
            EventUiModel("lock", EventType.FailedUnlock, "", "", "", DeliveryStatus.Delivered, false, false),
            EventUiModel("motion", EventType.Motion, "", "", "", DeliveryStatus.Deferred, false, false),
            EventUiModel("sim", EventType.SimChange, "", "", "", DeliveryStatus.Failed, false, false),
            EventUiModel("panic", EventType.Panic, "", "", "", DeliveryStatus.Ready, false, false),
            EventUiModel("battery", EventType.Battery, "", "", "", DeliveryStatus.Skipped, false, false),
        )

        val expectedByType = mapOf(
            HistoryTypeFilter.Lock to "lock",
            HistoryTypeFilter.Motion to "motion",
            HistoryTypeFilter.Sim to "sim",
            HistoryTypeFilter.Panic to "panic",
            HistoryTypeFilter.Battery to "battery",
        )
        expectedByType.forEach { (type, id) ->
            assertEquals(
                listOf(id),
                filterHistoryEvents(events, HistoryFilterState(type, HistoryStatusFilter.All)).map { it.id },
            )
        }

        val expectedByStatus = mapOf(
            HistoryStatusFilter.Delivered to "lock",
            HistoryStatusFilter.Deferred to "motion",
            HistoryStatusFilter.Failed to "sim",
        )
        expectedByStatus.forEach { (status, id) ->
            assertEquals(
                listOf(id),
                filterHistoryEvents(events, HistoryFilterState(HistoryTypeFilter.All, status)).map { it.id },
            )
        }
    }
}
