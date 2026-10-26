package com.shieldra.app

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.navigation.OnboardingState
import com.shieldra.app.navigation.PreferencesOnboardingCompletionRecord
import com.shieldra.app.navigation.ShieldraNavHost
import com.shieldra.app.settings.AppSettings

/** A FragmentActivity (still a ComponentActivity) because the owner check uses BiometricPrompt. */
class MainActivity : FragmentActivity() {

    /** Builds the activity's resources in the owner's chosen language (or the system's). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.load(this)
        setContent {
            // A cold start reads the one recorded fact instead of assuming a first run: on this
            // device the process is reclaimed constantly, and an assumed first run reopened
            // onboarding in front of a phone that already holds real evidence (Q-K-7, A-27).
            val onboarding = remember { PreferencesOnboardingCompletionRecord(application) }
            val themeMode by AppSettings.theme.collectAsState()
            val dark = themeMode.isDark(isSystemInDarkTheme())
            // Keep the status and navigation bar icons readable when the owner forces a theme that
            // differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ShieldraTheme(darkTheme = dark) {
                ShieldraNavHost(
                    startDestination = OnboardingState.startDestination(onboarding.isCompleted()),
                    onOnboardingCompleted = onboarding::markCompleted,
                )
            }
        }
    }
}
