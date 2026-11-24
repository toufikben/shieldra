package com.shieldra.app.presentation.guard

import com.shieldra.app.R
import com.shieldra.app.presentation.model.ProtectionVisualState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P3-3 part one: "armed" and "guarding" are two different facts, and the dashboard must not merge them.
 *
 * The armed flag is a stored boolean. The guards are a running foreground service. Between the two there
 * is a real window the owner can be inside: the phone has just booted and is still locked, so
 * `BootReceiver` has stamped the restart and stopped — Android does not let a boot receiver start the
 * camera-type service before the first unlock, and it does not deliver the unlocked-boot broadcast until
 * that unlock happens. The dashboard read only the flag and therefore painted the shield `Protected`,
 * which is the one claim the phone cannot make in that window: nothing is sampling the SIM, the battery
 * or the accelerometer yet.
 *
 * The honest answer is a third state: armed, and waiting for the phone to finish starting. It is not
 * `Disabled` (the owner did arm it, and the setting is intact) and it is not `Protected` (nothing runs).
 * `ProtectionVisualState.Attention` is exactly that state — the app's own word for "something the owner
 * has to know before trusting this screen".
 */
class ArmedReadinessTest {

    @Test
    fun p3_3_armed_with_no_running_guards_is_its_own_state() {
        assertEquals(ArmedReadiness.NotArmed, ArmedReadiness.decide(armed = false, guardsRunning = false))
        assertEquals(ArmedReadiness.ArmedAndRunning, ArmedReadiness.decide(armed = true, guardsRunning = true))
        assertEquals(ArmedReadiness.ArmedWaitingToStart, ArmedReadiness.decide(armed = true, guardsRunning = false))
        // A service running with the flag off cannot happen through the app's own paths, but if it is
        // ever seen the shield must still read as running rather than flap to a false "off".
        assertEquals(ArmedReadiness.ArmedAndRunning, ArmedReadiness.decide(armed = false, guardsRunning = true))
    }

    @Test
    fun p3_3_only_a_running_service_may_paint_the_shield_protected() {
        assertEquals(
            ProtectionVisualState.Protected,
            ArmedReadiness.ArmedAndRunning.protectionVisualState(),
        )
        assertEquals(
            ProtectionVisualState.Attention,
            ArmedReadiness.ArmedWaitingToStart.protectionVisualState(),
        )
    }

    @Test
    fun p3_3_the_waiting_window_says_why_in_the_owner_s_words() {
        assertEquals(R.string.dashboard_armed_waiting_unlock, ArmedReadiness.ArmedWaitingToStart.noteRes)
        assertNull(ArmedReadiness.ArmedAndRunning.noteRes, "a running service has nothing to explain")
        assertNull(ArmedReadiness.NotArmed.noteRes, "an unarmed build is explained by its own disclosure line")
    }

    /**
     * P3-3 part two: a rotation that failed is the moment the app is most likely to lose evidence it
     * promised to keep, and the outcome was written to a field nobody rendered. The storage screen is
     * where the owner reads what this phone really holds, so both the last run and a run that could not
     * even start belong there — in both locales, since an Arabic phone must not be told in English.
     */
    @Test
    fun p3_3_the_storage_screen_reports_the_rotation_it_ran() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val screen = File(
            main,
            "kotlin/com/shieldra/app/presentation/screen/settings/SettingsInfoScreens.kt",
        ).readText()
        for (token in listOf("rotationReport", "rotationFailure", "storage_rotation_last", "storage_rotation_failed")) {
            assertTrue(screen.contains(token), "StorageScreen never mentions $token")
        }
        for (locale in listOf("values", "values-ar")) {
            val text = File(main, "res/$locale/strings.xml").readText()
            for (key in listOf("storage_rotation_last", "storage_rotation_failed", "storage_rotation_not_run")) {
                assertTrue(text.contains("name=\"$key\""), "$locale is missing $key")
            }
        }
    }
}
