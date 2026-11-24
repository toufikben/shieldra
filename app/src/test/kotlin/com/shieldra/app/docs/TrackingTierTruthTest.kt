package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The location tab states whether live tracking is on. It read that from the stored setting alone, so a
 * Free owner whose flag was left on (a downgrade, a restore onto a phone that never bought Pro) was told
 * "Live tracking is on" beside the interval and the window — while `ProtectionService.startTrackingIfEnabled`
 * returns at once for a plan that does not allow tracking. Nothing was reported, and the one screen that
 * describes the trail said it was running.
 *
 * This is the same defect the countdown card was fixed for: `periodicSignalCycle` in the navigation host
 * already asks `countdownEnabled && TierGate.allowsCountdown(tier)` before it shows a cycle, and the
 * service asks the same question in `CountdownScheduler.start`. The map row was the remaining place where
 * a stored switch was reported as a running feature without asking whether the plan lets it run.
 *
 * The answer is not to hide the row — the owner should see that the feature exists and is theirs to buy —
 * but the row may not claim it is on when the guard would refuse it.
 */
class TrackingTierTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun source(relative: String): String = File(root, "app/src/main/kotlin/$relative").readText()

    /** The guard really does refuse tracking for a plan that does not allow it. */
    @Test
    fun p4_7_the_service_refuses_tracking_the_plan_locks() {
        val service = source("com/shieldra/protection/ProtectionService.kt")
        assertTrue(
            service.contains("allowsTracking"),
            "the tracking guard no longer asks the plan, so the map row could not be wrong",
        )
    }

    @Test
    fun p4_7_the_map_row_does_not_claim_tracking_the_plan_locks() {
        val host = source("com/shieldra/app/navigation/ShieldraNavHost.kt")
        val start = host.indexOf("composable(ShieldraRoutes.MAP)")
        assertTrue(start > 0, "the map route is gone")
        val body = host.substring(start, (start + 2000).coerceAtMost(host.length))
        assertTrue(
            body.contains("liveTrackingEnabled ="),
            "the map route no longer passes the tracking state",
        )
        // The stored flag alone is what the service refuses. The row has to carry the plan gate too.
        assertFalse(
            Regex("liveTrackingEnabled\\s*=\\s*protectionSettings\\.liveTrackingEnabled\\s*,").containsMatchIn(body),
            "the map route reports the stored switch as a running feature without asking the plan",
        )
        assertTrue(
            body.contains("allowsTracking"),
            "the map route does not gate the tracking claim on the plan the service gates it on",
        )
    }
}
