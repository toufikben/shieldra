package com.shieldra.protection

/**
 * Remembers which Telegram updates were already carried out (P2-4).
 *
 * Two normal events deliver the same update twice: Telegram resends an update whose offset the bot
 * never confirmed, and the poller resumes from a stored offset after a restart. Without a guard,
 * ` /alarm` or `/track on` would run a second time from one message — the owner sees a siren start
 * again while they are silencing it.
 *
 * The memory is bounded on purpose. A poller that lives for months would otherwise keep every
 * update id it ever saw; only the recent ones can be redelivered, so an old id falling out of the
 * window is not a loss. Ids are kept in insertion order, which is also the order the platform
 * delivered them in.
 *
 * Not thread-safe: one instance is owned by the single poller thread.
 */
class RemoteCommandReplayGuard(private val capacity: Int = DEFAULT_CAPACITY) {
    private val seen = LinkedHashSet<Long>()

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    /** True the first time an update is offered, false for every later offer of the same id. */
    fun claim(updateId: Long): Boolean {
        if (!seen.add(updateId)) return false
        while (seen.size > capacity) {
            val oldest = seen.first()
            seen.remove(oldest)
        }
        return true
    }

    /** How many ids the guard still remembers; used by the tests to prove the memory is bounded. */
    fun rememberedCount(): Int = seen.size

    private companion object {
        /** The platform redelivers a handful of recent updates, never a whole history. */
        const val DEFAULT_CAPACITY = 64
    }
}
