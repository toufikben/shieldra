package com.shieldra.app

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.navigation.OnboardingState
import com.shieldra.app.navigation.PreferencesOnboardingCompletionRecord
import com.shieldra.app.navigation.ShieldraNavHost
import com.shieldra.app.presentation.screen.lock.AppLockScreen
import com.shieldra.app.settings.AppLockPolicy
import com.shieldra.app.settings.AppLockSession
import com.shieldra.app.settings.AppSettings

/** A FragmentActivity (still a ComponentActivity) because the owner check uses BiometricPrompt. */
class MainActivity : FragmentActivity() {

    /** True while the app lock (A-39) is in front of the app. */
    private val locked = mutableStateOf(false)

    /** When the app last went to the background, on the monotonic clock; null while it is in front. */
    private var backgroundedAtMs: Long? = null

    /** Builds the activity's resources in the owner's chosen language (or the system's). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.load(this)
        // A new process puts the lock up first, including when Android restores the app after killing it;
        // a recreate inside the same process (rotation, language change) keeps it unlocked.
        locked.value = AppLockPolicy.lockOnColdStart(AppSettings.appLockEnabled(this)) && !AppLockSession.unlocked
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
                Box {
                    ShieldraNavHost(
                        startDestination = OnboardingState.startDestination(onboarding.isCompleted()),
                        onOnboardingCompleted = onboarding::markCompleted,
                    )
                    if (locked.value) {
                        AppLockScreen(
                            activity = this@MainActivity,
                            onUnlocked = {
                                AppLockSession.unlocked = true
                                locked.value = false
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAtMs = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (AppLockPolicy.shouldLockOnReturn(
                enabled = AppSettings.appLockEnabled(this),
                backgroundedAtMs = backgroundedAtMs,
                nowMs = SystemClock.elapsedRealtime(),
            )
        ) {
            locked.value = true
        }
        backgroundedAtMs = null
    }
}
