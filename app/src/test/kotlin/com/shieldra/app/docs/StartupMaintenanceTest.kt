package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The startup maintenance report has to arrive after it is produced, not before.
 *
 * `ShieldraApp.checkEvidenceRotation` runs on a plain thread started from `onCreate`: it sweeps expired
 * evidence, then runs the rotation check. Both write their outcome to a field. The storage screen read
 * that field once, while the navigation host was being built — which is inside the same `onCreate`
 * window, before either write could have happened. So the one screen that reports a failed key rotation
 * rendered "not finished on this run yet" for the rest of the process, over a rotation that had in fact
 * already failed. The claim was not false; it was frozen at the moment it was least useful.
 *
 * The fix is a state flow the app publishes as each step finishes, and a screen that collects it. This
 * test pins both halves, because either half alone reproduces the defect: a flow nobody collects, or a
 * collector that snapshots a plain field.
 */
class StartupMaintenanceTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun source(relative: String): String = File(root, "app/src/main/kotlin/$relative").readText()

    @Test
    fun p3_3_the_app_publishes_what_the_maintenance_thread_learned() {
        val app = source("com/shieldra/app/ShieldraApp.kt")
        assertTrue(
            app.contains("MutableStateFlow"),
            "ShieldraApp still only writes plain fields, so a screen can read them before they are written",
        )
        assertTrue(
            app.contains("startupMaintenance"),
            "ShieldraApp publishes no collectable maintenance value",
        )
        // The value has to be published per step, not once at the end: a rotation that takes a minute
        // must not hide the retention sweep that already finished.
        val publishes = Regex("maintenanceState\\.value\\s*=").findAll(app).count()
        assertTrue(
            publishes >= 2,
            "the maintenance thread must publish after each step (found $publishes publish(es))",
        )
    }

    @Test
    fun p3_3_the_storage_screen_collects_it_instead_of_snapshotting_a_field() {
        val host = source("com/shieldra/app/navigation/ShieldraNavHost.kt")
        assertTrue(
            host.contains("startupMaintenance.collectAsState()"),
            "the navigation host never collects the published maintenance value",
        )
        assertFalse(
            host.contains("lastRotationReport") || host.contains("lastRotationFailure"),
            "the host still snapshots the rotation outcome at host-build time, which is before it exists",
        )
    }

    @Test
    fun p3_3_a_rotation_that_failed_still_reaches_the_screen() {
        val app = source("com/shieldra/app/ShieldraApp.kt")
        // Both outcomes are carried in the published value; dropping the failure to a log line would
        // leave the screen showing the last good report over a broken key clock.
        assertTrue(app.contains("rotationFailure"), "the published value drops the rotation failure")
        assertTrue(app.contains("rotationReport"), "the published value drops the rotation report")
        assertTrue(app.contains("retentionReport"), "the published value drops the retention report")
    }

    /**
     * One place holds each outcome. Keeping the old public fields beside the flow left two sources for
     * the same fact, and the stale one was still reachable — that is the defect this whole change is
     * about, so they are gone, in the app and in the storage object that held the second copy.
     */
    @Test
    fun p4_1_the_outcome_has_one_source_only() {
        val app = source("com/shieldra/app/ShieldraApp.kt")
        for (gone in listOf("lastRotationReport", "lastRotationFailure", "lastRetentionReport", "lastRetentionFailure")) {
            assertFalse(app.contains(gone), "ShieldraApp still exposes $gone beside the published value")
        }
        val storage = source("com/shieldra/storage/LocalStorageFactory.kt")
        for (gone in listOf("lastRetentionReport", "lastRetentionFailure")) {
            assertFalse(storage.contains(gone), "LocalShieldraStorage still exposes a second copy of $gone")
        }
    }
}
