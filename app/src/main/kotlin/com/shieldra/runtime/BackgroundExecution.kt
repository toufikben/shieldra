package com.shieldra.runtime

/**
 * The background execution layer's own state — and nothing more.
 *
 * Batch F builds the substrate a future Guard will run on. It deliberately knows
 * nothing about detectors, Security Events, persistence, or notifications, and
 * nothing here may be read as "SHIELDRA is watching the device": an execution
 * layer that is up is not a guard that is observing (owner directive F2).
 */
enum class ExecutionPhase {
    /** The layer holds no platform resource. This is also the state after a process death. */
    Stopped,

    /** A successful start brought the layer up. Says nothing about detection. */
    Running,
}

/** Something the platform must genuinely provide before the layer may be brought up. */
enum class Prerequisite {
    /** A real detector exists and is bound. No batch up to F has one. */
    DetectorAvailable,

    /** The permission the detector needs has been granted by the user. */
    PermissionGranted,

    /** There is somewhere to persist what the detector finds. */
    StorageInstalled,
}

/** The honest answer to `start()`. A refusal is a result, never a silent no-op. */
sealed interface StartResult {
    /** Prerequisites were present and the platform layer came up. */
    data object Started : StartResult

    /** Start was already effective; nothing was brought up a second time. */
    data object AlreadyRunning : StartResult

    /** Refused, with the missing prerequisites named so the UI can say what is absent. */
    data class PrerequisitesMissing(val missing: Set<Prerequisite>) : StartResult

    /** The platform was asked and failed; [reason] is the real cause, not a guess. */
    data class Failed(val reason: String) : StartResult
}

/** The honest answer to `stop()`. */
sealed interface StopResult {
    data object Stopped : StopResult

    /** Stop was already effective; nothing was torn down a second time. */
    data object AlreadyStopped : StopResult
}

/**
 * What the app is allowed to *say* about protection, given the execution layer and
 * whether a detector is actually bound.
 *
 * [ExecutionWithoutDetector] exists precisely because those two facts are not the
 * same: a started layer with nothing observing must never render as protection.
 * Only [ActiveGuardMonitoring] may claim protection, and no build up to Batch F
 * can produce it.
 */
enum class MonitoringClaim {
    NoExecution,
    ExecutionWithoutDetector,
    ActiveGuardMonitoring;

    /** The single gate every screen must consult before claiming anything is monitored. */
    val claimsProtection: Boolean get() = this == ActiveGuardMonitoring
}

/**
 * Idempotent start/stop for the background execution layer.
 *
 * The platform side is injected ([bringUp], [tearDown]) so this class stays free of
 * Android and of every other layer: it holds no repository, builds no Security Event,
 * and posts no notification. A future foreground service is one implementation of
 * [bringUp], chosen by whichever detector actually needs it (directive F3).
 */
class BackgroundExecutionController(
    private val missingPrerequisites: () -> Set<Prerequisite> = { emptySet() },
    private val bringUp: () -> Unit = {},
    private val tearDown: () -> Unit = {},
) {
    var phase: ExecutionPhase = ExecutionPhase.Stopped
        private set

    /**
     * Brings the layer up once. Repeat calls report [StartResult.AlreadyRunning]
     * without touching the platform again, so two callers cannot double-start it.
     */
    fun start(): StartResult {
        if (phase == ExecutionPhase.Running) return StartResult.AlreadyRunning
        val missing = missingPrerequisites()
        if (missing.isNotEmpty()) return StartResult.PrerequisitesMissing(missing)
        return try {
            bringUp()
            phase = ExecutionPhase.Running
            StartResult.Started
        } catch (error: Exception) {
            StartResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    /** Graceful shutdown. Idempotent, so a second stop is reported, not replayed. */
    fun stop(): StopResult {
        if (phase == ExecutionPhase.Stopped) return StopResult.AlreadyStopped
        tearDown()
        phase = ExecutionPhase.Stopped
        return StopResult.Stopped
    }

    /**
     * A process that was recreated is honest about being stopped: whatever the
     * previous process had was killed with it, and nothing here restarts it.
     */
    fun restoredFromPreviousProcess(): BackgroundExecutionController =
        BackgroundExecutionController(missingPrerequisites, bringUp, tearDown)

    /**
     * The only projection the UI may use. [detectorRunning] comes from a real
     * detector's own state, never from this class — the execution layer cannot
     * know it, which is the point.
     */
    fun monitoringClaim(detectorRunning: Boolean): MonitoringClaim = when {
        phase != ExecutionPhase.Running -> MonitoringClaim.NoExecution
        !detectorRunning -> MonitoringClaim.ExecutionWithoutDetector
        else -> MonitoringClaim.ActiveGuardMonitoring
    }
}
