package com.shieldra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.navigation.OnboardingState
import com.shieldra.app.navigation.PreferencesOnboardingCompletionRecord
import com.shieldra.app.navigation.ShieldraNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // A cold start reads the one recorded fact instead of assuming a first run: on this
            // device the process is reclaimed constantly, and an assumed first run reopened
            // onboarding in front of a phone that already holds real evidence (Q-K-7, A-27).
            val onboarding = remember { PreferencesOnboardingCompletionRecord(application) }
            ShieldraTheme {
                ShieldraNavHost(
                    startDestination = OnboardingState.startDestination(onboarding.isCompleted()),
                    onOnboardingCompleted = onboarding::markCompleted,
                )
            }
        }
    }
}
