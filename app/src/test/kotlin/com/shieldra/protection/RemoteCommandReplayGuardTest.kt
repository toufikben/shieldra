package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * P2-4: a delivered command is acted on once.
 *
 * Telegram redelivers an update whose offset the bot did not confirm, and the poller re-reads its
 * offset after a restart. Both are normal, and both would otherwise run `/alarm` or `/track on` a
 * second time from one message. The guard remembers which updates were already carried out, so the
 * same update is skipped while a genuinely new one still runs.
 */
class RemoteCommandReplayGuardTest {
    @Test
    fun a_fresh_update_is_carried_out() {
        val guard = RemoteCommandReplayGuard()

        assertEquals(true, guard.claim(updateId = 7L))
    }

    @Test
    fun the_same_update_is_never_carried_out_twice() {
        val guard = RemoteCommandReplayGuard()

        guard.claim(updateId = 7L)

        assertEquals(false, guard.claim(updateId = 7L))
        assertEquals(false, guard.claim(updateId = 7L))
    }

    @Test
    fun a_later_update_still_runs() {
        val guard = RemoteCommandReplayGuard()

        guard.claim(updateId = 7L)

        assertEquals(true, guard.claim(updateId = 8L))
    }

    @Test
    fun the_memory_does_not_grow_without_a_bound() {
        val guard = RemoteCommandReplayGuard(capacity = 3)

        (1L..10L).forEach { guard.claim(it) }

        assertEquals(3, guard.rememberedCount())
        // The three most recent are the ones still guarded; the oldest has been forgotten on purpose.
        assertEquals(false, guard.claim(updateId = 10L))
        assertEquals(false, guard.claim(updateId = 8L))
    }
}
