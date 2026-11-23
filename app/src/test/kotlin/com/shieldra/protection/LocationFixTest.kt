package com.shieldra.protection

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocationFixTest {
    @Test
    fun cached_only_mode_never_requests_a_fresh_background_fix() = runBlocking {
        var freshCalls = 0
        var cachedCalls = 0

        val result = resolveLocation(
            allowFresh = false,
            fresh = {
                freshCalls += 1
                "fresh"
            },
            cached = {
                cachedCalls += 1
                "cached"
            },
        )

        assertEquals("cached", result)
        assertEquals(0, freshCalls)
        assertEquals(1, cachedCalls)
    }

    @Test
    fun cached_only_mode_does_not_turn_a_cache_miss_into_a_fresh_request() = runBlocking {
        var freshCalls = 0

        val result = resolveLocation(
            allowFresh = false,
            fresh = {
                freshCalls += 1
                "fresh"
            },
            cached = { null },
        )

        assertNull(result)
        assertEquals(0, freshCalls)
    }

    @Test
    fun foreground_mode_tries_fresh_then_falls_back_to_the_cache() = runBlocking {
        val calls = mutableListOf<String>()

        val result = resolveLocation(
            allowFresh = true,
            fresh = {
                calls += "fresh"
                null
            },
            cached = {
                calls += "cached"
                "cached"
            },
        )

        assertEquals("cached", result)
        assertEquals(listOf("fresh", "cached"), calls)
    }
}
