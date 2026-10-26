package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastCheckTest {

    @Test
    fun `never checked is null`() {
        assertNull(LastCheck.secondsSince(0L, 5_000L))
    }

    @Test
    fun `whole seconds since the last check`() {
        assertEquals(12L, LastCheck.secondsSince(1_000L, 13_900L))
    }

    @Test
    fun `just now is zero`() {
        assertEquals(0L, LastCheck.secondsSince(5_000L, 5_000L))
    }

    @Test
    fun `a clock that went backwards is not a negative age`() {
        assertNull(LastCheck.secondsSince(10_000L, 4_000L))
    }
}
