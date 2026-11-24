package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P3-3 fixed the shield reading `Protected` from the armed flag alone. It left the other half open: the
 * dashboard asked `ProtectionService.isLive` — a plain getter over a static field — during composition.
 * A getter is not a subscription. Android stops a foreground service for its own reasons (a vendor
 * battery manager, a low-memory kill, the owner swiping it away), and `onDestroy` clears the instance;
 * nothing about that reaches a Compose frame that already ran. The result is the same lie in the
 * opposite direction: the shield keeps saying `Protected`, and the SIM and battery cards keep saying
 * `Active`, after the service that was doing the guarding has gone.
 *
 * The fix is a value the screen can collect, published where the service really learns the answer: set
 * when an instance is created, cleared in `onDestroy`, and cleared again by the branch that decides a
 * start cannot proceed. The dashboard then reads the flow, so a service that dies re-renders as
 * `Attention` without the owner having to leave the screen and come back.
 */
class ProtectionLivenessTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun source(relative: String): String = File(root, "app/src/main/kotlin/$relative").readText()

    @Test
    fun p4_5_the_service_publishes_its_own_liveness() {
        val service = source("com/shieldra/protection/ProtectionService.kt")
        assertTrue(
            service.contains("MutableStateFlow"),
            "ProtectionService publishes nothing collectable about whether it is alive",
        )
        assertTrue(
            Regex("val\\s+live\\s*:").containsMatchIn(service) || service.contains("val live:"),
            "ProtectionService exposes no collectable liveness value",
        )
        // The value has to be written where the fact changes, not computed on demand.
        val writes = Regex("liveState\\.value\\s*=").findAll(service).count()
        assertTrue(writes >= 2, "liveness is not published at both the start and the end of an instance (found $writes)")
        assertTrue(
            service.contains("liveInstance = null"),
            "onDestroy no longer clears the instance, so the process can hold a dead service",
        )
    }

    @Test
    fun p4_5_the_dashboard_collects_it_instead_of_reading_a_getter() {
        val host = source("com/shieldra/app/navigation/ShieldraNavHost.kt")
        assertFalse(
            host.contains("ProtectionService.isLive"),
            "the navigation host still reads liveness as a one-off getter during composition",
        )
        assertTrue(
            host.contains("ProtectionService.live.collectAsState()"),
            "the navigation host does not collect the service's published liveness",
        )
    }
}
