package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals

class RemainingTimeTest {
    private fun f(ms: Long) = RemainingTime.format(ms, "d", "h", "min", "<1 min")

    @Test
    fun `minutes round up so the notice never shows zero while time is left`() {
        assertEquals("1 min", f(1))
        assertEquals("10 min", f(10 * 60_000L))
        assertEquals("10 min", f(9 * 60_000L + 1))
    }

    @Test
    fun `hours and days use the two largest units`() {
        assertEquals("9 h 58 min", f((9 * 60 + 58) * 60_000L))
        assertEquals("2 h", f(2 * 3_600_000L))
        assertEquals("1 d 3 h", f((24 + 3) * 3_600_000L + 5 * 60_000L))
        assertEquals("7 d", f(7 * 86_400_000L))
    }

    @Test
    fun `nothing left reads as under a minute`() {
        assertEquals("<1 min", f(0))
        assertEquals("<1 min", f(-5))
    }
}
