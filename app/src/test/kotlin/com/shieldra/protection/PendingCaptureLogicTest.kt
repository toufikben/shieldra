package com.shieldra.protection

import com.shieldra.domain.SecurityEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A-33 capture path: the queue that holds a capture which could not run. These rules decide whether a
 * theft produces a photo minutes late, a text-only alert, or nothing at all, so they are tested without
 * Android — encoding, repeats, the size of the queue, and the moment a request stops being worth running.
 */
class PendingCaptureLogicTest {

    private val now = 1_730_000_000_000L

    private fun request(
        type: SecurityEventType = SecurityEventType.SIM_CONFIRMED_EVENT,
        eventId: String? = null,
        at: Long = now,
    ) = PendingCapture(type, eventId, at)

    @Test
    fun a_request_survives_being_written_down_and_read_back() {
        val stored = PendingCapture(SecurityEventType.SIM_CONFIRMED_EVENT, "3f2a", now)

        val read = PendingCaptureLogic.decode(PendingCaptureLogic.encode(stored))

        assertEquals(stored, read)
    }

    @Test
    fun a_request_the_owner_was_already_told_about_keeps_that_fact_through_the_queue() {
        val fresh = request()
        val told = PendingCaptureLogic.told(fresh)

        // The notice mark is what keeps a retry from sending the same bad news every thirty seconds.
        assertTrue(PendingCaptureLogic.owesTheOwnerNotice(fresh))
        assertFalse(PendingCaptureLogic.owesTheOwnerNotice(told))
        assertEquals(told, PendingCaptureLogic.decode(PendingCaptureLogic.encode(told)))
    }

    @Test
    fun an_absent_event_reads_back_as_nothing_rather_than_as_a_guess() {
        val read = PendingCaptureLogic.decode(
            PendingCaptureLogic.encode(request(eventId = null)),
        )

        assertEquals(now, read?.requestedAtEpochMs)
        assertNull(read?.eventId)
    }

    @Test
    fun a_line_this_version_cannot_read_is_dropped_instead_of_being_guessed_at() {
        val sep = "\u0001"
        // Too few fields, a guard this build does not know, a moment that is not a number, nothing at all.
        assertNull(PendingCaptureLogic.decode("GONE_GUARD$sep$sep$now"))
        assertNull(PendingCaptureLogic.decode("NOT_A_GUARD$sep$sep$sep$now"))
        assertNull(PendingCaptureLogic.decode("NOT_A_GUARD$sep$sep$sep" + "later"))
        assertNull(PendingCaptureLogic.decode(""))
    }

    @Test
    fun a_request_left_behind_by_the_previous_build_reads_as_not_yet_reported() {
        // An older build wrote four fields and no notice mark; a queue that survived the update has to
        // be read as it was written, and the honest reading is that nobody had been told about it yet.
        val legacy = PendingCaptureLogic.encode(request()).dropLast(1)

        val read = PendingCaptureLogic.decode(legacy)

        assertEquals(false, read?.announced)
        assertEquals(now, read?.requestedAtEpochMs)
    }

    @Test
    fun the_same_guard_waiting_for_the_same_incident_is_not_queued_twice() {
        val first = request()

        val queue = PendingCaptureLogic.plus(listOf(first), request())

        // One guard's burst is one scene: the request already waiting will photograph it.
        assertEquals(listOf(first), queue)
    }

    @Test
    fun two_guards_that_both_failed_both_wait() {
        val sim = request()
        val battery = request(type = SecurityEventType.BATTERY_SIGNAL, at = now + 1)

        val queue = PendingCaptureLogic.plus(listOf(sim), battery)

        assertEquals(listOf(sim, battery), queue)
    }

    @Test
    fun two_motion_incidents_are_two_requests_because_they_are_two_different_events() {
        val first = request(type = SecurityEventType.MOTION_CONFIRMED_EVENT, eventId = "e1")
        val second = request(type = SecurityEventType.MOTION_CONFIRMED_EVENT, eventId = "e2", at = now + 1)

        val queue = PendingCaptureLogic.plus(listOf(first), second)

        assertEquals(listOf(first, second), queue)
        assertFalse(PendingCaptureLogic.holds(listOf(first), second.type, second.eventId, now))
    }

    @Test
    fun a_full_queue_makes_room_by_losing_the_oldest_wait() {
        val waiting = (0 until PendingCaptureLogic.MAX_PENDING).map {
            request(
                type = if (it % 2 == 0) SecurityEventType.SIM_CONFIRMED_EVENT else SecurityEventType.BATTERY_SIGNAL,
                eventId = "e$it",
                at = now + it,
            )
        }
        val newest = request(type = SecurityEventType.BATTERY_SIGNAL, at = now + 100)

        val queue = PendingCaptureLogic.plus(waiting, newest)

        assertEquals(PendingCaptureLogic.MAX_PENDING, queue.size)
        assertEquals(newest, queue.last())
        assertFalse(queue.contains(waiting.first()))
    }

    @Test
    fun a_request_that_arrived_long_ago_leaves_the_queue_on_the_way_in() {
        val expired = request(at = now - PendingCaptureLogic.STALE_AFTER_MS - 1)
        val fresh = request(type = SecurityEventType.SIM_CONFIRMED_EVENT, at = now)

        val queue = PendingCaptureLogic.plus(listOf(expired), fresh)

        assertEquals(listOf(fresh), queue)
    }

    @Test
    fun only_requests_inside_their_moment_are_run_and_the_earliest_goes_first() {
        val oldest = request(at = now - 60_000)
        val middle = request(type = SecurityEventType.SIM_CONFIRMED_EVENT, at = now - 20_000)
        val tooOld = request(type = SecurityEventType.BATTERY_SIGNAL, at = now - PendingCaptureLogic.STALE_AFTER_MS - 1)

        val due = PendingCaptureLogic.freshOnes(listOf(middle, tooOld, oldest), now)

        assertEquals(listOf(oldest, middle), due)
    }

    @Test
    fun a_request_written_after_the_clock_jumped_forward_is_never_late_but_still_never_run() {
        // A wall clock that went backwards (or a reboot) makes the stored moment lie: it reads as
        // being from the future, and a capture of a moment that has not happened yet is not owed.
        val fromTheFuture = request(at = now + 60_000)

        assertFalse(PendingCaptureLogic.isFresh(fromTheFuture, now))
        assertEquals(emptyList(), PendingCaptureLogic.freshOnes(listOf(fromTheFuture), now))
    }

    @Test
    fun the_boundary_of_the_window_is_still_runnable_and_one_millisecond_past_it_is_not() {
        val atTheEdge = request(at = now - PendingCaptureLogic.STALE_AFTER_MS)
        val justPast = request(type = SecurityEventType.SIM_CONFIRMED_EVENT, at = now - PendingCaptureLogic.STALE_AFTER_MS - 1)

        assertTrue(PendingCaptureLogic.isFresh(atTheEdge, now))
        assertFalse(PendingCaptureLogic.isFresh(justPast, now))
    }

    @Test
    fun a_drain_accounts_for_every_request_so_none_can_simply_disappear() {
        val runnable = request()
        val stale = request(type = SecurityEventType.SIM_CONFIRMED_EVENT, at = now - PendingCaptureLogic.STALE_AFTER_MS - 1)

        val drained = PendingCaptureLogic.split(listOf(runnable, stale), now)

        assertEquals(listOf(runnable), drained.due)
        assertEquals(listOf(stale), drained.expired)
        assertFalse(drained.isEmpty)
    }

    @Test
    fun a_drain_of_an_empty_or_unreadable_queue_is_empty() {
        assertTrue(PendingCaptureLogic.split(emptyList(), now).isEmpty)
        // A line this version cannot read is not due and not expired; the store drops it and the
        // drain reports nothing, so no phantom request is announced to the owner.
        val oneGarbage = listOfNotNull(PendingCaptureLogic.decode("not-a-request"))
        assertTrue(PendingCaptureLogic.split(oneGarbage, now).isEmpty)
    }

    @Test
    fun waiting_holds_only_fresh_requests_for_the_exact_guard_and_incident() {
        val sim = request()
        val waiting = listOf(sim)

        assertTrue(PendingCaptureLogic.holds(waiting, sim.type, null, now))
        assertFalse(PendingCaptureLogic.holds(waiting, sim.type, "some other event", now))
        assertFalse(PendingCaptureLogic.holds(waiting, SecurityEventType.BATTERY_SIGNAL, null, now))
        assertFalse(PendingCaptureLogic.holds(waiting, sim.type, null, now + PendingCaptureLogic.STALE_AFTER_MS + 1))
    }
}
