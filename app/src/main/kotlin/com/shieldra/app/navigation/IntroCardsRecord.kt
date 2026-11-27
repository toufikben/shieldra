package com.shieldra.app.navigation

import android.content.Context

/**
 * P4-22: whether this install has seen the tour.
 *
 * Its own preference, its own file, and a separate fact from onboarding completion on purpose. The two
 * are answered by different screens at different moments, and one boolean shared between them would let
 * a finished setup be read as a finished tour — the owner would then never see the cards at all.
 *
 * Written only by [markSeen], read only by [isSeen]. Nothing about a security event depends on it, so it
 * lives outside the database exactly like the onboarding flag beside it.
 */
interface IntroCardsRecord {
    fun isSeen(): Boolean

    fun markSeen()
}

/** The preference-backed [IntroCardsRecord]. */
class PreferencesIntroCardsRecord(context: Context) : IntroCardsRecord {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun isSeen(): Boolean = prefs.getBoolean(KEY_INTRO_SEEN, false)

    override fun markSeen() {
        prefs.edit().putBoolean(KEY_INTRO_SEEN, true).apply()
    }

    private companion object {
        const val PREFERENCES_NAME: String = "shieldra_onboarding"
        const val KEY_INTRO_SEEN: String = "intro_cards_seen"
    }
}
