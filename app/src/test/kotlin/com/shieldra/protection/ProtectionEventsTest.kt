package com.shieldra.protection

import com.shieldra.domain.EventRepository
import com.shieldra.domain.GuardSeverity
import com.shieldra.domain.InMemoryEventRepository
import com.shieldra.domain.RepositoryResult
import com.shieldra.domain.SecurityEvent
import com.shieldra.domain.SecurityEventType
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtectionEventsTest {
    @Test
    fun rejected_event_persistence_is_not_reported_as_an_open_incident() {
        val base = InMemoryEventRepository()
        val repository = object : EventRepository by base {
            override fun save(event: SecurityEvent): RepositoryResult =
                RepositoryResult.Rejected("injected storage failure")
        }

        val binding = ProtectionEvents(repository).openOrBind(
            SecurityEventType.BATTERY_SIGNAL,
            grouped = false,
            GuardSeverity.MEDIUM,
        )

        assertNull(binding)
        assertTrue(base.listRecent(10).isEmpty())
    }

    @Test
    fun applied_event_persistence_returns_the_real_incident() {
        val repository = InMemoryEventRepository()

        val binding = ProtectionEvents(repository).openOrBind(
            SecurityEventType.BATTERY_SIGNAL,
            grouped = false,
            GuardSeverity.MEDIUM,
        )

        assertNotNull(binding)
        assertTrue(binding.isNew)
        assertNotNull(repository.find(binding.event.id))
    }
}
