package com.shieldra.app.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the app picks light or dark: follow the system, or force one. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    /** Pure: whether the dark scheme applies given what the system currently prefers. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * The app's display language: the system's, or one the owner picked. The build ships two
 * translations — Arabic and English — and English is also the default resource set, so a system
 * language the app does not ship (French, Urdu, …) reads English. That is Android's own fallback,
 * and [shownFor] states the rule in code so a test can hold it instead of a device.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null), ENGLISH("en"), ARABIC("ar");

    companion object {
        /**
         * The language this build actually renders for a system locale tag. Arabic for any `ar`
         * variant; English for everything else, including a phone whose language has no
         * translation here — the default resources are English, so the two agree by construction.
         */
        fun shownFor(systemTag: String?): AppLanguage =
            if (systemTag?.substringBefore('-')?.lowercase() == "ar") ARABIC else ENGLISH

        fun fromStored(value: String?): AppLanguage = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * The owner's app-level preferences: theme, language and the app lock. They live in app-private
 * preferences (not backed up with evidence) and are the only place these three choices are stored.
 * [theme] is observable so the whole UI recomposes the moment it changes; language is applied the
 * next time the activity's context is built (the caller recreates the activity).
 */
object AppSettings {
    private const val PREFS = "shieldra_app_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_APP_LOCK = "app_lock_enabled"

    private val themeState = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = themeState.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Loads the stored theme into the observable; call once at startup. */
    fun load(context: Context) {
        themeState.value = ThemeMode.fromStored(prefs(context).getString(KEY_THEME, null))
    }

    fun setTheme(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME, mode.name).apply()
        themeState.value = mode
    }

    fun language(context: Context): AppLanguage =
        AppLanguage.fromStored(prefs(context).getString(KEY_LANGUAGE, null))

    fun setLanguage(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    fun appLockEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_APP_LOCK, false)

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_APP_LOCK, enabled).apply()
    }

    /**
     * The context an activity should use so its resources (and layout direction) follow the language
     * the owner will actually read: their explicit choice, or — on "match system" — the system
     * language when this build ships it, English when it does not. The system tag is read from the
     * base context, never from [Locale.getDefault], because that default is exactly what this method
     * wrote on the previous launch and would otherwise make the choice stick forever.
     */
    fun localized(base: Context): Context {
        val locales = base.resources.configuration.locales
        val systemTag = if (locales.size() == 0) null else locales[0].toLanguageTag()
        val chosen = language(base)
        val shown = if (chosen == AppLanguage.SYSTEM) AppLanguage.shownFor(systemTag) else chosen
        val locale = Locale.forLanguageTag(shown.tag ?: return base)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
