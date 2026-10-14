package com.shieldra.protection

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app-process handle for the protection guards: where the UI reads whether protection is armed,
 * and how arming or disarming reaches the service, the countdown and the stored flag (A-33). It
 * holds no Android state of its own — the single source of truth is the stored `armed` flag, which
 * boot recovery also reads, so what the UI shows and what a reboot restores can never disagree.
 */
object ProtectionRuntime {
    private val armedState = MutableStateFlow(false)

    /** The only Flow a screen renders the armed/disarmed state from. */
    val armed: StateFlow<Boolean> = armedState.asStateFlow()

    /** Re-reads the stored flag; called when a protection screen appears. */
    fun refresh(context: Context) {
        armedState.value = ProtectionSettingsStore(context.applicationContext).load().armed
    }

    /**
     * Arms protection: the permissions and device-admin consent are the screen's job and must be in
     * place first. This records the armed flag, starts the service (which captures the SIM baseline
     * and begins the battery/SIM guards), and starts the countdown if the owner enabled it.
     */
    fun arm(context: Context) {
        val app = context.applicationContext
        val store = ProtectionSettingsStore(app)
        store.setArmed(true)
        ProtectionService.arm(app)
        if (store.load().countdownEnabled) CountdownScheduler.start(app)
        armedState.value = true
    }

    /** Disarms everything: clears the flag, stops the service, cancels the countdown. */
    fun disarm(context: Context) {
        val app = context.applicationContext
        ProtectionSettingsStore(app).setArmed(false)
        ProtectionService.stop(app)
        CountdownScheduler.cancel(app)
        armedState.value = false
    }

    /** Applies a changed settings object and re-arms the countdown to match it, if armed. */
    fun applySettings(context: Context, settings: ProtectionSettings) {
        val app = context.applicationContext
        val store = ProtectionSettingsStore(app)
        store.save(settings)
        if (settings.armed && settings.countdownEnabled) {
            CountdownScheduler.start(app)
        } else {
            CountdownScheduler.cancel(app)
        }
    }
}
