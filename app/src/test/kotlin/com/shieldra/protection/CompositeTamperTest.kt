package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeTamperTest {

    @Test
    fun `sim swap with screen off and offline is high risk`() {
        assertTrue(
            CompositeTamper.isHighRisk(
                CompositeTamper.Signals(simAbnormal = true, screenInteractive = false, online = false),
            ),
        )
    }

    @Test
    fun `a normal sim is never high risk`() {
        assertFalse(
            CompositeTamper.isHighRisk(
                CompositeTamper.Signals(simAbnormal = false, screenInteractive = false, online = false),
            ),
        )
    }

    @Test
    fun `sim swap while the screen is on is not high risk`() {
        assertFalse(
            CompositeTamper.isHighRisk(
                CompositeTamper.Signals(simAbnormal = true, screenInteractive = true, online = false),
            ),
        )
    }

    @Test
    fun `sim swap while still online is not high risk`() {
        assertFalse(
            CompositeTamper.isHighRisk(
                CompositeTamper.Signals(simAbnormal = true, screenInteractive = false, online = true),
            ),
        )
    }
}
