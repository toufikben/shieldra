package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P4-18: the device-admin role has no name of its own.
 *
 * The owner grants the role from the Protection setup checklist through
 * `ACTION_ADD_DEVICE_ADMIN`, and the screen that asks him to — the platform's own
 * "Activate device admin?" dialog — prints the label the *receiver* declares. A receiver
 * that declares none inherits the application label, so the dialog asks the owner to
 * activate "Shieldra" and never says what the role is for.
 *
 * The proof that a name was intended is in the resources: `device_admin_label`
 * ("SHIELDRA protection") and `device_admin_description` are written in both languages,
 * the description is handed to the platform request in `ShieldraNavHost`, and the label
 * is referenced by nothing at all — no Kotlin file, no manifest attribute, no layout.
 * It was written to name the role and then the role was declared without it.
 *
 * The test pins both halves: the copy exists in both languages with no format argument to
 * fill, and the receiver the platform reads carries it as its `android:label`.
 */
class DeviceAdminLabelTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun main(relative: String): String = File(root, "app/src/main/$relative").readText()

    private fun locale(name: String): String =
        File(root, "app/src/main/res/$name/strings.xml").readText()

    /** The name is written, in both languages, as a plain role name. */
    @Test
    fun p4_18_both_languages_carry_the_role_label() {
        for (name in listOf("values", "values-ar")) {
            val text = locale(name)
            assertTrue(
                text.contains("name=\"device_admin_label\""),
                "$name has no device_admin_label, so the role has nothing to be called",
            )
            assertTrue(
                Regex("""name="device_admin_label">[^<]*</string>""").containsMatchIn(text),
                "$name's device_admin_label carries a format argument, so it cannot serve as a name",
            )
        }
    }

    /**
     * The receiver the platform reads declares the label, so the activation dialog says what
     * the role is for instead of borrowing the application's name.
     */
    @Test
    fun p4_18_the_receiver_declares_the_role_label() {
        val manifest = main("AndroidManifest.xml")
        val receiver = manifest.substringAfter("com.shieldra.protection.LockGuardReceiver")
            .substringBefore("</receiver>")
        assertTrue(
            receiver.contains("android.app.device_admin"),
            "the LockGuardReceiver no longer declares the device-admin meta-data, so this finding " +
                "must be re-measured",
        )
        assertTrue(
            receiver.contains("@string/device_admin_label"),
            "the device-admin receiver declares no android:label, so the platform's activation " +
                "dialog shows the application name and never says what the role is for",
        )
    }

    /** And the description the platform prints beside it is the one the app hands over. */
    @Test
    fun p4_18_the_description_still_reaches_the_request() {
        val host = main("kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt")
        assertTrue(
            host.contains("R.string.device_admin_description"),
            "the device-admin request no longer carries a description, so the activation dialog " +
                "explains nothing",
        )
        assertFalse(
            host.contains("R.string.device_admin_label"),
            "the label is now fetched into the request as well; the platform reads it from the " +
                "receiver declaration, so a second copy is a second source of truth",
        )
    }
}
