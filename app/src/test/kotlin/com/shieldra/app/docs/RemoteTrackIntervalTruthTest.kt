package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P4-31: `/track` in the remote poller reads the interval once and keeps it for the whole stream.
 *
 * `startTrack` computes `trackingIntervalMinutes` before the loop and sleeps that same number of
 * minutes after every pin:
 *
 * ```
 * val intervalMs = ProtectionSettingsStore(app).load().trackingIntervalMinutes.coerceAtLeast(1) * 60_000L
 * while (!Thread.currentThread().isInterrupted && active()) { ... Thread.sleep(intervalMs) }
 * ```
 *
 * The owner's interval is a setting he can change while the stream runs, and the service's own trail
 * (`ProtectionService.scheduleTrackingTick`) recomputes it on every turn for exactly that reason — P4-21
 * added the switch and the plan there, and the cadence has always been re-read because the battery
 * widens it as it drains (`TrackingCadence.intervalMs`). The remote stream is the same promise about
 * the same setting, so an owner who widens or narrows the interval during a `/track on` gets the old
 * one until he sends `/track off` and `/track on` again.
 *
 * Nothing restarts the stream to notice either: `startTrack` refuses while its thread is alive, so the
 * setting is not merely stale in a variable — it is unreachable for the life of that stream.
 */
class RemoteTrackIntervalTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private val poller: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/protection/RemoteCommandPoller.kt").readText()
    }

    private val service: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/protection/ProtectionService.kt").readText()
    }

    /** The `startTrack` body, where the interval is read and then slept on. */
    private fun trackBody(): String {
        val start = poller.indexOf("private fun startTrack(")
        assertTrue(start > 0, "the location stream is gone, so this finding must be re-measured")
        val end = poller.indexOf("\n    @Synchronized\n    private fun stopTrack(", start)
        return poller.substring(start, if (end > start) end else poller.length)
    }

    /** The premise: the service's own trail does re-read the interval on every turn. */
    @Test
    fun p4_31_the_service_trail_recomputes_the_interval_each_turn() {
        val start = service.indexOf("private fun scheduleTrackingTick(")
        assertTrue(start > 0, "the service trail is gone, so this finding must be re-measured")
        val body = service.substring(start, service.indexOf("\n    private fun batteryPercent(", start))
        assertTrue(
            body.contains("trackingIntervalMinutes"),
            "the service trail no longer reads the owner's interval, so this finding must be re-measured",
        )
        assertTrue(
            body.contains("TrackingCadence.intervalMs("),
            "the service trail no longer widens the gap as the battery drains, so this finding must " +
                "be re-measured",
        )
    }

    /** And the remote stream asks for the interval inside its loop, not once before it. */
    @Test
    fun p4_31_the_remote_stream_re_reads_the_interval() {
        val body = trackBody()
        val loopStart = body.indexOf("while (!Thread.currentThread().isInterrupted")
        assertTrue(loopStart > 0, "the stream's loop is gone, so this finding must be re-measured")
        val inside = body.substring(loopStart)
        assertTrue(
            inside.contains("trackingIntervalMinutes"),
            "the remote location stream reads the owner's interval once, before its loop, so changing " +
                "the interval during /track on has no effect until the owner stops the stream and " +
                "starts it again",
        )
    }
}
