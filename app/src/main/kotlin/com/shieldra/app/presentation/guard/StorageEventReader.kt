package com.shieldra.app.presentation.guard

import com.shieldra.storage.LocalShieldraStorage

/**
 * Binds the pure [PersistedEventReader] to the installed local storage.
 *
 * A build without installed storage reads nothing at all: there is no
 * speculative open, no second storage instance, and no fallback source. The
 * reader itself is inert in that case, so callers cannot accidentally query a
 * database that was never opened.
 */
object StorageEventReader {
    fun reader(storage: LocalShieldraStorage?, labels: PersistedEventLabels): PersistedEventReader {
        val installed = storage
        return PersistedEventReader(
            storageInstalled = installed != null,
            loadRecent = { limit -> installed?.eventRepository?.listRecent(limit) ?: emptyList() },
            labels = labels,
        )
    }
}
