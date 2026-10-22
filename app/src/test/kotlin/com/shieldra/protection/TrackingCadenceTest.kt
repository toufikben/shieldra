package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackingCadenceTest {

    @Test
    fun `healthy battery uses the base interval`() {
        assertEquals(2 * 60_000L, TrackingCadence.intervalMs(2, batteryPercent = 80, charging = false))
    }

    @Test
    fun `low battery doubles the gap`() {
        assertEquals(2 * 2 * 60_000L, TrackingCadence.intervalMs(2, batteryPercent = 25, charging = false))
    }

    @Test
    fun `critical battery quadruples the gap`() {
        assertEquals(2 * 4 * 60_000L, TrackingCadence.intervalMs(2, batteryPercent = 10, charging = false))
    }

    @Test
    fun `charging ignores a low level and keeps the base interval`() {
        assertEquals(3 * 60_000L, TrackingCadence.intervalMs(3, batteryPercent = 8, charging = true))
    }

    @Test
    fun `unknown battery keeps the base interval`() {
        assertEquals(60_000L, TrackingCadence.intervalMs(1, batteryPercent = null, charging = false))
    }

    @Test
    fun `base interval below one is floored to one minute`() {
        assertEquals(60_000L, TrackingCadence.intervalMs(0, batteryPercent = 90, charging = false))
    }

    @Test
    fun `boundary at the low threshold widens`() {
        assertEquals(2 * 60_000L, TrackingCadence.intervalMs(1, batteryPercent = TrackingCadence.LOW_PERCENT, charging = false))
    }

    @Test
    fun `boundary at the critical threshold uses the critical factor`() {
        assertEquals(4 * 60_000L, TrackingCadence.intervalMs(1, batteryPercent = TrackingCadence.CRITICAL_PERCENT, charging = false))
    }
}
