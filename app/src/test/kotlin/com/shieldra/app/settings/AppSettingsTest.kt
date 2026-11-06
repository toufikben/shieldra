package com.shieldra.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSettingsTest {

    @Test
    fun `system theme follows the system, forced themes ignore it`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun `an unknown or missing stored theme falls back to system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("PURPLE"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("DARK"))
    }

    @Test
    fun `an unknown or missing stored language falls back to system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStored(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStored("KLINGON"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromStored("ARABIC"))
    }

    @Test
    fun `only a chosen language carries a locale tag`() {
        assertNull(AppLanguage.SYSTEM.tag)
        assertEquals("en", AppLanguage.ENGLISH.tag)
        assertEquals("ar", AppLanguage.ARABIC.tag)
    }

    // The language the owner actually reads on a "match system" build. The rule is Android's own:
    // a shipped translation wins, anything else falls to the default resources — English.
    @Test
    fun `the system language is used when shipped and English when it is not`() {
        assertEquals(AppLanguage.ARABIC, AppLanguage.shownFor("ar"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.shownFor("ar-EG"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.shownFor("AR-dz"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.shownFor("en-GB"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.shownFor("fr-FR"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.shownFor("ur"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.shownFor(null))
    }
}
