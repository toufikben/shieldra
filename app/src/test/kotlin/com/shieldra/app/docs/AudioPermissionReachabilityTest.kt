package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The owner can switch on "Short audio clip" and watch it stay silent for the life of the install.
 *
 * `AudioCapture.record` opens with
 * `if (checkSelfPermission(RECORD_AUDIO) != PERMISSION_GRANTED) return null` — it records nothing
 * without the microphone permission, which is the right fail-closed shape. The problem is that
 * nothing in the app ever asks for it: `ProtectionPermissions` carries camera, location, phone,
 * notifications, the battery exemption and exact alarms, and no microphone; `ShieldraNavHost` has a
 * launcher call for each of those and none for `RECORD_AUDIO`; and `ProtectionSetupScreen` renders a
 * `PermissionRow` per entry, so no row exists to tap either.
 *
 * The result is an evidence claim with no path to the evidence. The switch is not even free — it sits
 * behind the Pro plan, so the owner pays for an audio clip the app has already been denied the
 * microphone to record, and the manifest declares `RECORD_AUDIO` and `FOREGROUND_SERVICE_MICROPHONE`
 * for a permission that can never be granted.
 *
 * The test pins the whole reachability chain: a permission the manifest declares, a state read from
 * the platform, a row that shows it, and a launcher that asks for it.
 */
class AudioPermissionReachabilityTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun main(relative: String): String = File(root, "app/src/main/$relative").readText()

    /** The manifest really declares it, so the ask is not refused for a permission that was never declared. */
    @Test
    fun p4_10_the_manifest_declares_the_microphone() {
        val manifest = main("AndroidManifest.xml")
        assertTrue(
            manifest.contains("android.permission.RECORD_AUDIO"),
            "the manifest no longer declares RECORD_AUDIO, so no audio evidence can ever be recorded",
        )
        assertTrue(
            manifest.contains("FOREGROUND_SERVICE_MICROPHONE"),
            "the service would be stopped with a SecurityException on Android 14 while recording",
        )
    }

    /** The recorder really refuses without the permission: this is the fact that makes the gap bite. */
    @Test
    fun p4_10_the_recorder_still_refuses_without_the_permission() {
        val capture = main("kotlin/com/shieldra/protection/AudioCapture.kt")
        assertTrue(
            capture.contains("RECORD_AUDIO") && capture.contains("return@withContext null"),
            "AudioCapture stopped checking RECORD_AUDIO; if it no longer refuses, this test's premise is gone",
        )
    }

    /** The protection screen carries the microphone state, a row to see it and a caller that asks. */
    @Test
    fun p4_10_the_owner_can_actually_grant_the_microphone() {
        val permissions = main(
            "kotlin/com/shieldra/app/presentation/screen/settings/ProtectionSetupScreen.kt",
        )
        assertTrue(
            permissions.contains("val microphone: Boolean"),
            "ProtectionPermissions has no microphone field, so the screen cannot know the state of the " +
                "one permission audio evidence needs",
        )
        assertTrue(
            permissions.contains("onGrantMicrophone"),
            "ProtectionSetupScreen takes no microphone grant callback, so no row can ask for it",
        )
        assertTrue(
            permissions.contains("R.string.protection_perm_microphone"),
            "no row is rendered for the microphone, so the owner has nothing to tap",
        )

        val navHost = main("kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt")
        assertTrue(
            navHost.contains("microphone = has(Manifest.permission.RECORD_AUDIO)"),
            "the host never reads the microphone permission, so the row would show a state nobody measured",
        )
        assertTrue(
            navHost.contains("onGrantMicrophone = {") &&
                navHost.contains("Manifest.permission.RECORD_AUDIO"),
            "the host never launches a request for RECORD_AUDIO, so the permission can never be granted",
        )
    }

    /** Both languages name the row: the locale gate would reject a one-sided string. */
    @Test
    fun p4_10_the_row_is_named_in_both_languages() {
        for (locale in listOf("values", "values-ar")) {
            val text = File(root, "app/src/main/res/$locale/strings.xml").readText()
            assertTrue(
                text.contains("name=\"protection_perm_microphone\""),
                "$locale has no protection_perm_microphone, so the row cannot be drawn there",
            )
        }
    }
}
