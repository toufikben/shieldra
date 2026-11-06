package com.shieldra.app.presentation.screen.settings

import com.shieldra.protection.ProtectionSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * G-9: the protection form used to be keyed on the live settings object, so arming from its own top
 * button (or a language switch recreating the activity) wiped every unsaved toggle. These tests pin
 * the two halves of the replacement rule: follow the live settings while untouched, keep the edit
 * once there is one.
 */
class SetupDraftTest {

    private val loaded = ProtectionSettings(motionEnabled = true, motionGraceSeconds = 5)

    @Test
    fun an_untouched_form_follows_the_live_settings() {
        val incoming = loaded.copy(armed = true)

        val after = draftAfterSettingsChange(loaded, loaded, incoming)

        assertEquals(incoming, after.draft)
        assertEquals(incoming, after.openedWith)
    }

    @Test
    fun an_edit_is_not_wiped_when_the_live_settings_change() {
        val edited = loaded.copy(motionGraceSeconds = 20)
        val incoming = loaded.copy(armed = true)

        val after = draftAfterSettingsChange(edited, loaded, incoming)

        assertEquals(edited, after.draft, "an unsaved edit must survive an arming made from the top button")
        assertEquals(loaded, after.openedWith, "the baseline stays the one the edit was made against")
    }

    @Test
    fun a_later_external_change_does_not_take_the_edit_back_to_the_old_values() {
        val edited = loaded.copy(motionGraceSeconds = 20)
        val first = draftAfterSettingsChange(edited, loaded, loaded.copy(armed = true))

        val second = draftAfterSettingsChange(
            first.draft,
            first.openedWith,
            loaded.copy(armed = false, motionGraceSeconds = 9),
        )

        assertEquals(edited, second.draft)
    }
}
