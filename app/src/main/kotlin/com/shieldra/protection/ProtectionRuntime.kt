package com.shieldra.protection

import android.content.Context
import android.util.Log
import com.shieldra.app.ShieldraApp
import com.shieldra.runtime.MotionMonitoringStartDecision
import com.shieldra.runtime.MotionRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app-process handle for the protection guards: where the UI reads whether protection is armed,
 * and how arming or disarming reaches the service, the countdown and the stored flag (A-33). It
 * holds no Android state of its own — the single source of truth is the stored `armed` flag, which boot
 * recovery also reads, so what the UI shows and what a reboot restores can never disagree.
 *
 * Every write of that flag is mirrored to device-protected storage ([BootFlags]) in the same breath,
 * because a phone that restarts and stays locked cannot read the real one — and the guards have to be
 * able to tell the owner that the reboot interrupted them (audit E-2).
 */
object ProtectionRuntime {
    private val armedState = MutableStateFlow(false)

    /** The only Flow a screen renders the armed/disarmed state from. */
    val armed: StateFlow<Boolean> = armedState.asStateFlow()

    /** Re-reads the stored flag; called when a protection screen appears. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val armed = ProtectionSettingsStore(app).load().armed
        armedState.value = armed
        // The mirror catches up here too, so an arm made before BootFlags existed is not lost forever.
        runCatching { BootFlags(app).sync(armed) }
    }

    /**
     * Arms protection: the permissions and device-admin consent are the screen's job and must be in
     * place first. This records the armed flag, starts the service (which begins the battery/SIM
     * guards), and starts the countdown if the owner enabled it.
     *
     * The SIM baseline is taken from the cards actually in the phone at this moment (audit §5.11):
     * arming is the owner saying "watch for anything other than these", so a baseline left over from
     * a card they replaced months ago must not turn an honest re-arm into a permanent alarm.
     *
     * A-43: Motion is started here, beside the lock and SIM guards, when the owner left it on — it is
     * no longer a separate opt-in a second start button has to remember. A refusal (no notification
     * permission, no storage) is logged and leaves arming intact; the dashboard states the reason,
     * and an unanswered notification permission takes the same route the dashboard's Start does.
     */
    fun arm(context: Context) {
        val app = context.applicationContext
        val store = ProtectionSettingsStore(app)
        store.setArmed(true)
        runCatching { BootFlags(app).sync(true) }
        runCatching { SimGuard(app).rebaseline() }
        ProtectionService.arm(app)
        // The patrol is armed alongside the guards, not by them: it is the only thing that can notice
        // the service stopped stamping checks (audit development أ).
        runCatching { GuardWatchdog.schedule(app) }
        if (store.load().countdownEnabled) CountdownScheduler.start(app)
        if (store.load().motionEnabled) startMotion(app)
        armedState.value = true
    }

    /** Starts the Motion guard when the platform facts allow it; the claim itself stays the service's. */
    private fun startMotion(app: Context) {
        val storageInstalled = (app as? ShieldraApp)?.installedStorage != null
        when (val decision = MotionRuntime.startMonitoring(app, storageInstalled)) {
            MotionMonitoringStartDecision.MayStartMonitoring -> Unit
            MotionMonitoringStartDecision.AskForNotificationPermission ->
                Log.i(TAG, "protection is armed; the Motion guard waits for the notification permission")
            is MotionMonitoringStartDecision.DoNotStartMonitoring ->
                Log.w(TAG, "protection is armed but the Motion guard did not start: ${decision.reason}")
        }
    }

    /**
     * Disarms everything: clears the flag, stops the service, cancels the countdown, and forgets the
     * captures that were still waiting. The last part is the point — a queued request to photograph
     * whoever holds the phone must not outlive the owner's decision to stop watching.
     */
    fun disarm(context: Context) {
        val app = context.applicationContext
        ProtectionSettingsStore(app).setArmed(false)
        ProtectionService.stop(app)
        CountdownScheduler.cancel(app)
        // A-43: the Motion guard stops with the guards it belongs to, and a grace response still
        // waiting must not siren or lock a phone the owner just chose to stop watching.
        MotionRuntime.stopMonitoring(app)
        runCatching { TableModeResponder.cancel(app) }
        runCatching {
            // The owner chose silence; a patrol that kept waking to report it would be a defect.
            GuardWatchdog.cancel(app)
            BootFlags(app).sync(false)
            PendingCaptures(app).clear()
            PendingCaptureReceiver.cancel(app)
        }
        armedState.value = false
    }

    /**
     * Records the owner's Motion-guard choice without touching the other guards (A-43). The dashboard
     * and the settings screen show the same guard, so a live start or stop there must also decide
     * what the next arming and the next reboot do.
     */
    fun setMotionEnabled(context: Context, enabled: Boolean) {
        val store = ProtectionSettingsStore(context.applicationContext)
        store.save(store.load().copy(motionEnabled = enabled))
    }

    /** The owner's safe zones (A-36). */
    fun zones(context: Context): List<SafeZone> =
        ProtectionSettingsStore(context.applicationContext).safeZones()

    fun geofenceEnabled(context: Context): Boolean =
        ProtectionSettingsStore(context.applicationContext).load().geofenceEnabled

    fun setGeofenceEnabled(context: Context, enabled: Boolean) {
        val store = ProtectionSettingsStore(context.applicationContext)
        store.save(store.load().copy(geofenceEnabled = enabled))
    }

    /**
     * Marks the phone's current position as a safe zone. The fix is taken inside the protection
     * package (group-c/A-34), so the UI never touches location itself. Returns false if no fix.
     */
    suspend fun addCurrentLocationAsZone(
        context: Context,
        name: String,
        radiusMeters: Double = SafeZone.DEFAULT_RADIUS_M,
    ): Boolean {
        val app = context.applicationContext
        val fix = LocationFix(app).current() ?: return false
        val store = ProtectionSettingsStore(app)
        val zones = store.safeZones().toMutableList()
        val label = name.ifBlank { "Zone ${zones.size + 1}" }
        zones.add(SafeZone(label, GeofenceLogic.Zone(fix.latitude, fix.longitude, radiusMeters)))
        store.setSafeZones(zones)
        return true
    }

    fun removeZone(context: Context, name: String) {
        val store = ProtectionSettingsStore(context.applicationContext)
        store.setSafeZones(store.safeZones().filterNot { it.name == name })
    }

    /**
     * Applies changed guard settings and re-arms the countdown to match. The armed flag is read back
     * from storage instead of taken from [settings]: a screen's snapshot can be older than the arm
     * made from its own top button, and a Save must never decide whether protection runs (A-44).
     */
    fun applySettings(context: Context, settings: ProtectionSettings) {
        val app = context.applicationContext
        val store = ProtectionSettingsStore(app)
        store.save(settings)
        val armed = store.load().armed
        runCatching { BootFlags(app).sync(armed) }
        if (armed && settings.countdownEnabled) {
            CountdownScheduler.start(app)
        } else {
            CountdownScheduler.cancel(app)
        }
        // Arming from the settings screen has to arm the patrol with it, and turning protection off has
        // to stop the patrol asking about a guard the owner switched off on purpose.
        runCatching { if (armed) GuardWatchdog.schedule(app) else GuardWatchdog.cancel(app) }
        if (!armed) {
            runCatching {
                PendingCaptures(app).clear()
                PendingCaptureReceiver.cancel(app)
            }
        }
    }

    private const val TAG: String = "ProtectionRuntime"
}
