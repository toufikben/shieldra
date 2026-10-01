package com.shieldra.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Batch G / slice G5, correction (ب) of amendment A-18: what the app is allowed to say about Motion
 * monitoring comes from the real Service state and the real adapter registration — never from the
 * fact that the user pressed Start.
 *
 * Batch F already drew this line for the execution layer ([BackgroundExecutionController] cannot
 * know whether a detector runs, so `monitoringClaim` takes that fact as an argument). G5 moves one
 * level up: the two facts now come from outside the process as well as outside the class, and a
 * start that Android refused must render as nothing.
 */
class MotionMonitoringTruthTest {

    @Test
    fun a_service_that_is_not_running_claims_no_execution_however_confident_the_sensor_flag_is() {
        assertEquals(
            MonitoringClaim.NoExecution,
            MotionMonitoringTruth.claim(serviceRunning = false, sensorReporting = true),
            "a reporting sensor with no running service is a state the app must not render as protection",
        )
        assertEquals(MonitoringClaim.NoExecution, MotionMonitoringTruth.claim(serviceRunning = false, sensorReporting = false))
    }

    @Test
    fun a_running_service_holding_no_registered_accelerometer_claims_no_protection() {
        val claim = MotionMonitoringTruth.claim(serviceRunning = true, sensorReporting = false)

        assertEquals(MonitoringClaim.ExecutionWithoutDetector, claim)
        assertFalse(claim.claimsProtection, "an up service that samples nothing is not watching the device")
    }

    @Test
    fun only_a_running_service_with_the_sensor_actually_registered_claims_active_monitoring() {
        val claim = MotionMonitoringTruth.claim(serviceRunning = true, sensorReporting = true)

        assertEquals(MonitoringClaim.ActiveGuardMonitoring, claim)
        assertTrue(claim.claimsProtection, "this is the one combination allowed to say protection")
    }

    @Test
    fun a_start_press_the_platform_refused_claims_nothing() {
        val controller = BackgroundExecutionController(
            bringUp = { throw IllegalStateException("startForeground was refused") },
        )

        val start = controller.start()
        assertIs<StartResult.Failed>(start, "a refusal is a result, not a silent no-op")

        assertEquals(
            MonitoringClaim.NoExecution,
            MotionMonitoringTruth.claim(
                serviceRunning = controller.phase == ExecutionPhase.Running,
                sensorReporting = false,
            ),
            "the request to start is not evidence that anything started",
        )
    }

    @Test
    fun the_truth_stops_claiming_when_the_service_is_torn_down() {
        var registered = false
        val controller = BackgroundExecutionController(
            bringUp = { registered = true },
            tearDown = { registered = false },
        )

        assertEquals(StartResult.Started, controller.start())
        assertEquals(
            MonitoringClaim.ActiveGuardMonitoring,
            MotionMonitoringTruth.claim(controller.phase == ExecutionPhase.Running, registered),
        )

        assertEquals(StopResult.Stopped, controller.stop())
        assertFalse(registered, "the injected teardown is the registration actually ending")
        assertEquals(
            MonitoringClaim.NoExecution,
            MotionMonitoringTruth.claim(controller.phase == ExecutionPhase.Running, registered),
        )
    }

    @Test
    fun a_process_restored_from_a_previous_lifecycle_starts_from_no_claim() {
        val restored = BackgroundExecutionController().restoredFromPreviousProcess()

        assertEquals(
            MonitoringClaim.NoExecution,
            MotionMonitoringTruth.claim(restored.phase == ExecutionPhase.Running, sensorReporting = false),
            "a recreated process holds no service and no registration, so it may claim nothing",
        )
    }
}
