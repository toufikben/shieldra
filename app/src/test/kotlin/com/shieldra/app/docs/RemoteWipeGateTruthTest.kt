package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P2-3: the remote wipe reaches `wipeData` only through the owner's double confirmation.
 *
 * The plan (owner decision 2026-11-27) has three parts, and each one is a claim this test checks
 * against the source rather than trusting the comment above it:
 *
 * 1. `/wipe` alone never erases — it opens a window and answers with one sentence.
 * 2. Only `/wipe confirm` inside the window erases, and every gate (the explicit switch, the plan, the
 *    armed flag, an active administrator) is asked again at the moment it acts.
 * 3. Proof of ownership inside the window cancels it — the same rule that closes a snatch.
 *
 * The device call lives in one place (`DeviceRemoteWipe`), so "is the erase gated" has a single answer
 * to read: no other file may call `wipeData`.
 */
class RemoteWipeGateTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun source(path: String): String =
        File(root, "app/src/main/kotlin/com/shieldra/$path").let { if (it.isFile) it.readText() else "" }

    private val poller: String by lazy { source("protection/RemoteCommandPoller.kt") }
    private val wipe: String by lazy { source("protection/DeviceRemoteWipe.kt") }

    /** All main sources, for the "only one file may call it" claim. */
    private val allSources: List<File> by lazy {
        File(root, "app/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()
    }

    @Test
    fun p2_3_the_erase_is_reachable_from_exactly_one_place() {
        val callers = allSources.filter { file ->
            file.readText().contains("wipeData(") && file.name != "DeviceRemoteWipe.kt"
        }.map { it.name }
        assertTrue(
            callers.isEmpty(),
            "the erase is called outside its one gate: $callers. A second call site is a second " +
                "answer to \"is this confirmed\", and the wrong one is silent",
        )
    }

    @Test
    fun p2_3_the_poller_asks_the_pure_request_before_it_erases() {
        assertTrue(
            poller.contains("RemoteWipeRequest.decide("),
            "the poller no longer asks the confirmation rule, so the wipe path must be re-measured",
        )
    }

    @Test
    fun p2_3_the_poller_has_a_wipe_command() {
        assertTrue(
            Regex(""""/wipe"\s*->""").containsMatchIn(poller),
            "there is no /wipe branch in the poller, so the owner has no remote wipe at all",
        )
    }

    @Test
    fun p2_3_the_gates_are_asked_where_the_erase_happens() {
        // The gates are read inside the erase path, not handed in from the loop's earlier snapshot.
        val start = wipe.indexOf("fun eraseIfConfirmed(")
        assertTrue(start > 0, "the gate is gone, so this finding must be re-measured")
        val body = wipe.substring(start, wipe.length)
        for (gate in listOf("remoteWipeEnabled", "allowsRemoteWipe", "armed")) {
            assertTrue(
                body.contains(gate),
                "the erase path does not ask `$gate`, so a gate that closed inside the ten-minute " +
                    "window still erases the phone",
            )
        }
        assertTrue(
            body.contains("isGranted("),
            "the erase path does not ask whether the administrator power is really held, so it can " +
                "report an erase that the platform refused",
        )
    }

    @Test
    fun p2_3_the_window_is_the_published_one() {
        assertTrue(
            poller.contains("RemoteWipeRequest.WINDOW_MS"),
            "the poller computes its own window instead of using the published one, so the sentence " +
                "the owner is told can disagree with how long he really has",
        )
    }

    @Test
    fun p2_3_proof_of_ownership_cancels_the_window() {
        assertTrue(
            wipe.contains("RemoteWipeStore(app).clear()"),
            "nothing clears an open wipe window, so a phone found by its owner still erases itself " +
                "when the confirmation arrives",
        )
        val coordinator = source("protection/SnatchCoordinator.kt")
        assertTrue(
            coordinator.contains("RemoteWipeStore"),
            "the owner's proof (a successful unlock) does not cancel a wipe window, so unlocking the " +
                "phone he just found does not save it",
        )
    }

    @Test
    fun p2_3_the_switch_is_its_own_setting_not_the_remote_switch() {
        val settings = source("protection/ProtectionSettings.kt")
        assertTrue(
            settings.contains("remoteWipeEnabled"),
            "the wipe has no switch of its own, so turning on remote control silently turns on the " +
                "power to erase the phone",
        )
        val gate = source("billing/TierGate.kt")
        assertTrue(
            gate.contains("allowsRemoteWipe("),
            "the wipe has no plan gate of its own",
        )
    }

    @Test
    fun p2_3_the_declaration_carries_the_wipe_policy_and_says_so() {
        val declaration = File(root, "app/src/main/res/xml/device_admin.xml").readText()
        assertTrue(
            declaration.contains("<wipe-data />"),
            "the declaration does not request the wipe policy, so the platform will refuse the erase",
        )
        assertTrue(
            !Regex("""no wipe""").containsMatchIn(declaration),
            "the declaration still tells the owner SHIELDRA requests no wipe, which is no longer true",
        )
    }
}
