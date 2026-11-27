package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P4-27: switching remote control on while armed starts nothing.
 *
 * `ProtectionService.onStartCommand` is the only place that starts the poller:
 *
 * ```
 * ProtectionSettingsStore(applicationContext).load().let { s ->
 *     if (s.remoteCommandsEnabled && TierGate.allowsRemote(s.tier)) remoteCommands.start()
 * }
 * ```
 *
 * That runs when the service starts. The owner's switch lives on the Protection screen, and saving it
 * goes through `ProtectionRuntime.applySettings`, which handles every other switch that has to take
 * effect on a live service — it re-arms the countdown, schedules or cancels the patrol, schedules the
 * safe-zone tick, cancels the snatch and cancels a waiting Motion response — and does nothing at all
 * about remote control. So an owner who turns the switch on while protection is already armed gets a
 * switch that reads on and a bot that answers nothing, until the service happens to restart (a reboot,
 * or a disarm and re-arm). The screen's own words promise the opposite: "Send commands to your bot
 * while the phone is armed."
 *
 * The off direction is covered by `active()` asking the switch every loop (P4-20), so only the on
 * direction is broken — but a feature that only ever turns off is not a feature.
 */
class RemoteSwitchLiveTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private val runtime: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/protection/ProtectionRuntime.kt").readText()
    }

    private val service: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/protection/ProtectionService.kt").readText()
    }

    /** The `applySettings` body, where a Save reaches a live service. */
    private fun applyBody(): String {
        val start = runtime.indexOf("fun applySettings(")
        assertTrue(start > 0, "the settings applier is gone, so this finding must be re-measured")
        val end = runtime.indexOf("\n    /**", start)
        return runtime.substring(start, if (end > start) end else runtime.length)
    }

    /** The service body that applies the switch, wherever the writer chose to put it. */
    private fun syncBody(): String {
        val start = service.indexOf("fun syncRemoteControl(")
        assertTrue(start > 0, "the live remote-control applier is gone, so this must be re-measured")
        val end = service.indexOf("\n    /**", start)
        return service.substring(start, if (end > start) end else service.length)
    }

    /** The service still gates its own start on the switch and the plan, so this is about Save. */
    @Test
    fun p4_27_the_service_still_starts_the_poller_when_it_starts() {
        val start = service.indexOf("remoteCommands.start()")
        assertTrue(start > 0, "the service no longer starts the poller, so this finding must be re-measured")
        val around = service.substring((start - 400).coerceAtLeast(0), start)
        assertTrue(
            around.contains("remoteCommandsEnabled"),
            "the service no longer gates the poller start on the owner's switch, so this finding must " +
                "be re-measured",
        )
    }

    /** `applySettings` does reach other switches on a live service, so the shape is established. */
    @Test
    fun p4_27_a_save_still_reaches_the_other_live_switches() {
        val body = applyBody()
        assertTrue(
            body.contains("CountdownScheduler.start("),
            "a Save no longer re-arms the countdown, so this finding must be re-measured",
        )
        assertTrue(
            body.contains("GeofenceTicker.schedule("),
            "a Save no longer schedules the safe-zone tick, so this finding must be re-measured",
        )
    }

    /** And it reaches the remote-control switch, which it does not today. */
    @Test
    fun p4_27_a_save_starts_or_stops_remote_control() {
        val body = applyBody()
        assertTrue(
            body.contains("syncRemoteControl("),
            "a Save never reaches remote control, so turning it on while armed shows an on switch " +
                "over a bot that answers nothing until the service restarts",
        )
        val sync = syncBody()
        assertTrue(
            sync.contains("remoteCommandsEnabled") && sync.contains("allowsRemote"),
            "the live switch is applied without asking the owner's switch and the plan, so a free " +
                "plan or a switched-off guard could keep a poller running",
        )
        assertTrue(
            sync.contains("remoteCommands.start()") && sync.contains("remoteCommands.stop()"),
            "the live switch is applied in one direction only, so the owner cannot turn remote " +
                "control off without restarting the service",
        )
    }
}
