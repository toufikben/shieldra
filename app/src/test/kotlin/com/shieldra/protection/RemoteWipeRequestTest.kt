package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P2-3: the owner's remote wipe, and the three things that must stand between `/wipe` and the phone
 * being erased.
 *
 * The plan (owner decision 2026-11-27): `/wipe` never erases. It opens a short confirmation window and
 * answers with the one sentence that says so. Only `/wipe confirm` inside that window erases, and every
 * gate is asked again at the moment it acts — not when the request was made — because the window can
 * last ten minutes and the owner can change any of them inside it.
 *
 * This test pins the pure decisions. The device call itself is pinned by the truth test beside it.
 */
class RemoteWipeRequestTest {

    private val window = 10 * 60_000L
    private val t0 = 1_000_000L

    @Test
    fun p2_3_the_first_command_opens_a_window_and_does_not_erase() {
        val decision = RemoteWipeRequest.decide(
            argument = "",
            open = null,
            nowMs = t0,
            gatesOpen = true,
            windowMs = window,
        )
        assertEquals(RemoteWipeRequest.Decision.Open(t0 + window), decision)
        assertFalse(decision == RemoteWipeRequest.Decision.Erase, "the first command must never erase")
    }

    @Test
    fun p2_3_a_confirmation_inside_the_window_erases() {
        val open = RemoteWipeRequest.Open(requestedAtMs = t0, expiresAtMs = t0 + window)
        val decision = RemoteWipeRequest.decide(
            argument = "confirm",
            open = open,
            nowMs = t0 + 60_000L,
            gatesOpen = true,
            windowMs = window,
        )
        assertEquals(RemoteWipeRequest.Decision.Erase, decision)
    }

    @Test
    fun p2_3_a_confirmation_after_the_window_does_not_erase() {
        val open = RemoteWipeRequest.Open(requestedAtMs = t0, expiresAtMs = t0 + window)
        val decision = RemoteWipeRequest.decide(
            argument = "confirm",
            open = open,
            nowMs = t0 + window + 1L,
            gatesOpen = true,
            windowMs = window,
        )
        assertEquals(RemoteWipeRequest.Decision.Expired, decision)
    }

    @Test
    fun p2_3_a_confirmation_with_no_request_open_does_not_erase() {
        val decision = RemoteWipeRequest.decide(
            argument = "confirm",
            open = null,
            nowMs = t0,
            gatesOpen = true,
            windowMs = window,
        )
        assertEquals(RemoteWipeRequest.Decision.NothingWaiting, decision)
    }

    @Test
    fun p2_3_a_gate_that_closed_inside_the_window_stops_the_erase() {
        val open = RemoteWipeRequest.Open(requestedAtMs = t0, expiresAtMs = t0 + window)
        val decision = RemoteWipeRequest.decide(
            argument = "confirm",
            open = open,
            nowMs = t0 + 60_000L,
            gatesOpen = false,
            windowMs = window,
        )
        assertEquals(RemoteWipeRequest.Decision.GatesClosed, decision)
    }

    @Test
    fun p2_3_cancel_clears_the_window() {
        assertEquals(RemoteWipeRequest.Decision.Cancel, RemoteWipeRequest.decide(
            argument = "cancel",
            open = RemoteWipeRequest.Open(t0, t0 + window),
            nowMs = t0 + 1_000L,
            gatesOpen = true,
            windowMs = window,
        ))
    }

    @Test
    fun p2_3_an_unknown_argument_never_erases() {
        for (argument in listOf("now", "yes", "CONFIRM NOW", "1", "wipe")) {
            val decision = RemoteWipeRequest.decide(
                argument = argument,
                open = RemoteWipeRequest.Open(t0, t0 + window),
                nowMs = t0 + 1_000L,
                gatesOpen = true,
                windowMs = window,
            )
            assertFalse(
                decision == RemoteWipeRequest.Decision.Erase,
                "the argument \"$argument\" must not be read as a confirmation",
            )
        }
    }

    @Test
    fun p2_3_the_window_is_ten_minutes() {
        assertEquals(10 * 60_000L, RemoteWipeRequest.WINDOW_MS)
    }

    @Test
    fun p2_3_the_confirmation_word_is_published_not_typed_twice() {
        assertEquals("confirm", RemoteWipeRequest.CONFIRM_ARGUMENT)
        assertEquals("cancel", RemoteWipeRequest.CANCEL_ARGUMENT)
    }

    @Test
    fun p2_3_an_open_request_reports_when_it_expires() {
        val open = RemoteWipeRequest.Open(requestedAtMs = t0, expiresAtMs = t0 + window)
        assertTrue(open.isOpen(t0 + window))
        assertFalse(open.isOpen(t0 + window + 1L))
        assertFalse(open.isOpen(t0 - 1L), "a clock that jumped backwards must not keep it open")
        assertNull(RemoteWipeRequest.Open(t0, t0 + window).remainingMs(t0 + window + 1L))
    }
}
