package com.shieldra.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Batch F background-execution contract (owner directive F2/F7).
 *
 * These tests exist before the contract does, and they assert the separation the
 * amendment requires: an execution layer that is *running* is not a guard that is
 * *detecting*, and neither one is an event, a persistence, or a notification.
 */
class BackgroundExecutionControllerTest {

    /** Records what the platform layer was actually asked to do. */
    private class Recorder {
        var bringUps = 0
        var tearDowns = 0
        var throwOnBringUp = false

        fun bringUp() {
            if (throwOnBringUp) throw IllegalStateException("the platform refused")
            bringUps += 1
        }

        fun tearDown() {
            tearDowns += 1
        }
    }

    private fun controller(
        recorder: Recorder,
        missing: Set<Prerequisite> = emptySet(),
    ) = BackgroundExecutionController(
        missingPrerequisites = { missing },
        bringUp = { recorder.bringUp() },
        tearDown = { recorder.tearDown() },
    )

    // F7.1 — start from a valid context.
    @Test
    fun start_with_every_prerequisite_present_brings_the_layer_up_once() {
        val recorder = Recorder()
        val execution = controller(recorder)

        val result = execution.start()

        assertEquals(StartResult.Started, result)
        assertEquals(ExecutionPhase.Running, execution.phase)
        assertEquals(1, recorder.bringUps)
    }

    // F7.2 — duplicate start is idempotent.
    @Test
    fun starting_twice_reports_already_running_and_never_brings_the_layer_up_twice() {
        val recorder = Recorder()
        val execution = controller(recorder)
        execution.start()

        val second = execution.start()

        assertEquals(StartResult.AlreadyRunning, second)
        assertEquals(1, recorder.bringUps)
    }

    // F7.3 — duplicate stop is idempotent.
    @Test
    fun stopping_twice_reports_already_stopped_and_never_tears_the_layer_down_twice() {
        val recorder = Recorder()
        val execution = controller(recorder)
        execution.start()

        assertEquals(StopResult.Stopped, execution.stop())
        assertEquals(StopResult.AlreadyStopped, execution.stop())
        assertEquals(1, recorder.tearDowns)
        assertEquals(ExecutionPhase.Stopped, execution.phase)
    }

    // F7.4 — a missing prerequisite fails honestly instead of pretending.
    @Test
    fun start_with_a_missing_prerequisite_is_refused_and_nothing_is_brought_up() {
        val recorder = Recorder()
        val execution = controller(recorder, missing = setOf(Prerequisite.DetectorAvailable))

        val result = execution.start()

        assertEquals(StartResult.PrerequisitesMissing(setOf(Prerequisite.DetectorAvailable)), result)
        assertEquals(ExecutionPhase.Stopped, execution.phase)
        assertEquals(0, recorder.bringUps)
    }

    // F7.5 — a failed start leaves the layer stopped, and nothing else.
    @Test
    fun a_failing_start_leaves_the_layer_stopped_and_carries_the_real_reason() {
        val recorder = Recorder().apply { throwOnBringUp = true }
        val execution = controller(recorder)

        val result = execution.start()

        assertTrue(result is StartResult.Failed)
        assertEquals("the platform refused", (result as StartResult.Failed).reason)
        assertEquals(ExecutionPhase.Stopped, execution.phase)
    }

    // F7.6 — process recreation must not fabricate an active session.
    @Test
    fun a_recreated_controller_reports_stopped_not_monitoring() {
        val recorder = Recorder()
        val execution = controller(recorder)
        execution.start()

        val afterRecreation = controller(recorder).restoredFromPreviousProcess()

        assertEquals(ExecutionPhase.Stopped, afterRecreation.phase)
        assertEquals(
            MonitoringClaim.NoExecution,
            afterRecreation.monitoringClaim(detectorRunning = true),
        )
        // The restarted process asked the platform for nothing.
        assertEquals(1, recorder.bringUps)
    }

    // F7.7 — no detector means no active guard, whatever the execution layer does.
    @Test
    fun a_running_execution_layer_without_a_detector_is_not_active_monitoring() {
        val execution = controller(Recorder())
        execution.start()

        assertEquals(
            MonitoringClaim.ExecutionWithoutDetector,
            execution.monitoringClaim(detectorRunning = false),
        )
        assertFalse(execution.monitoringClaim(detectorRunning = false).claimsProtection)
    }

    @Test
    fun only_a_detector_on_a_running_layer_may_claim_active_monitoring() {
        val execution = controller(Recorder())
        execution.start()

        assertTrue(execution.monitoringClaim(detectorRunning = true).claimsProtection)
    }

    @Test
    fun stopping_after_a_failed_start_is_idempotent() {
        val recorder = Recorder().apply { throwOnBringUp = true }
        val execution = controller(recorder)
        execution.start()

        assertEquals(StopResult.AlreadyStopped, execution.stop())
        assertEquals(0, recorder.tearDowns)
    }
}
