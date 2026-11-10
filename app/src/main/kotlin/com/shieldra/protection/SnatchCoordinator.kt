package com.shieldra.protection

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.shieldra.delivery.AlertKind
import com.shieldra.delivery.AlertRuntime
import com.shieldra.domain.SecurityEventType

/**
 * Runs one snatch from the moment the Motion guard confirms it until its owner proves ownership, the
 * phone reaches a safe zone, or six hours have passed (A-46, owner decision 2026-11-10).
 *
 * The owner's sequence, applied literally:
 *  1. the screen locks at once ([DeviceScreenLock]) — no photo and no fix at that second, because a
 *     thief running with the phone is not a subject worth photographing;
 *  2. ten minutes of a public demand notice, a repeating vibration and a loud alarm, in which the
 *     owner proves ownership by unlocking the phone or by pressing the stop button in the app;
 *  3. proof clears the whole incident and hands the periodic countdown back its chosen interval;
 *  4. no proof is a theft: the ladder escalates on its own and sends full evidence at the owner's
 *     cadence (five or ten minutes), a wrong PIN only accelerating it;
 *  5. inside a fresh safe zone the ladder pauses instead of sending — the phone came home — and
 *     resumes the moment it leaves again;
 *  6. at or below ten percent battery, unplugged, the slowest cadence wins (the 10% agreement).
 *
 * Everything durable lives in [SnatchStore] (device-protected), so the incident survives the process
 * being killed and the phone being restarted. The tick itself is [SnatchReceiver]'s exact alarm, which
 * fires with no service and no activity alive.
 */
object SnatchCoordinator {
    private const val TAG = "SnatchCoordinator"

    /** How long the siren is allowed to sound per burst; the vibration carries the rest of the window. */
    private const val SIREN_MS = 30_000L

    /** After a locked boot the phone is still locked: give the owner time to unlock before the ladder. */
    private const val LOCKED_BOOT_TICK_MS = 60_000L

    /**
     * How often a tick before the first unlock keeps the incident alive. Those ticks cannot read the
     * credential-protected settings and cannot capture anything, so they only re-post the demand and
     * wait; the first unlock after a reboot is proof and ends the incident anyway.
     */
    private const val LOCKED_STORAGE_TICK_MS = 10 * 60_000L

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Starts the snatch response for a Motion the guard just confirmed, or joins the incident already
     * open. Returns true when the snatch owns this event from here on — the caller must then not take
     * its own capture, because the first evidence belongs to the ladder, not to the second of the theft.
     *
     * Inside a fresh safe zone it refuses: that Motion is the owner at home, and throwing a lock screen
     * and an alarm over their own phone is the nuisance the safe-zone policy exists to remove.
     */
    @Synchronized
    fun begin(context: Context, eventId: String): Boolean {
        val app = context.applicationContext
        val settings = ProtectionSettingsStore(app).load()
        if (!settings.armed || !settings.snatchLockEnabled) return false
        if (ProtectionRuntime.quietInSafeZone(app)) return false
        val store = SnatchStore(app)
        val existing = store.read()
        if (existing.active) {
            // A second movement joins the open incident. The one thing worth retrying is a lock that
            // could not happen the first time — an administrator granted mid-incident starts working
            // here instead of at the next theft.
            if (existing.lockedAtMs == 0L && DeviceScreenLock.now(app) == DeviceScreenLock.Outcome.LOCKED) {
                store.write(existing.copy(lockedAtMs = System.currentTimeMillis()))
                SnatchAlerts.proofWindow(app)
            }
            return true
        }
        val nowMs = System.currentTimeMillis()
        val locked = DeviceScreenLock.now(app) == DeviceScreenLock.Outcome.LOCKED
        val incident = SnatchPolicy.begin(eventId, nowMs, locked)
        store.write(incident)
        SnatchDemand.show(app, incident, settings)
        if (locked) SnatchAlerts.proofWindow(app)
        // The periodic signal and the theft alert are two different messages. The countdown is the
        // owner's own heartbeat and has no business narrating a snatch; proof starts it again below.
        runCatching { CountdownScheduler.cancel(app) }
        // One text line for the owner's channels now: no fix, no photo, just the fact. The evidence
        // starts when the ten minutes say this is a theft (owner decision: location at t=0 is noise).
        runCatching { AlertRuntime.alert(app, AlertKind.MOTION, eventId) }
        soundAlarm(app)
        SnatchReceiver.schedule(app, incident.nextDueAtMs)
        Log.w(
            TAG,
            "a snatch was confirmed: the screen ${if (locked) "was locked" else "could not be locked yet"}; " +
                "ownership is due within ${SnatchPolicy.PROOF_WINDOW_MS / 60_000L} minutes",
        )
        return true
    }

    /**
     * The ladder's own tick: [SnatchReceiver]'s exact alarm, every few minutes and with no process
     * guaranteed. It proves ownership if the phone was unlocked, respects the safe zone, and otherwise
     * escalates — capturing the photo and the fix onto the event the Motion guard created.
     */
    @Synchronized
    fun onTick(context: Context) {
        val app = context.applicationContext
        val store = SnatchStore(app)
        val incident = store.read()
        if (!incident.active) {
            SnatchReceiver.cancel(app)
            SnatchDemand.hide(app)
            return
        }
        val nowMs = System.currentTimeMillis()
        if (SnatchPolicy.expired(incident, nowMs)) {
            // The cap is judged from the incident alone, so it ends a snatch on a still-locked phone
            // too — the teardown never needs the owner's private settings.
            Log.i(TAG, "the snatch reached its six-hour ceiling: the ladder stops here")
            end(app, restartCountdown = true)
            return
        }
        val settings = runCatching { ProtectionSettingsStore(app).load() }.getOrNull()
        if (settings == null) {
            // Before the phone's first unlock its private settings cannot be opened, but the incident
            // lives in device-protected storage and still exists. Keep it alive and re-post the demand
            // with what a locked phone can read: the three public languages, no owner's note yet. No
            // escalation happens from here — capturing needs the unlocked storage, and the first
            // unlock after a reboot is proof that closes the incident in BootReceiver.
            SnatchAlerts.stop(app)
            runCatching { SnatchDemand.show(app, incident, ProtectionSettings()) }
            SnatchReceiver.schedule(app, nowMs + LOCKED_STORAGE_TICK_MS)
            Log.i(TAG, "the snatch is waiting for the first unlock: the demand is back, nothing is sent")
            return
        }
        if (!settings.armed || !settings.snatchLockEnabled) {
            end(app, restartCountdown = false)
            return
        }
        if (SnatchPolicy.unlockedSince(incident, nowMs, keyguardLocked(app))) {
            confirmOwner(app, "the phone was unlocked after the lock")
            return
        }
        var current = incident
        if (current.lockedAtMs == 0L) {
            if (DeviceScreenLock.now(app) == DeviceScreenLock.Outcome.LOCKED) {
                current = current.copy(lockedAtMs = nowMs)
                store.write(current)
                SnatchAlerts.proofWindow(app)
            }
        }
        val intervalMs = SnatchPolicy.intervalMs(
            settings.snatchIntervalMinutes,
            BatteryLevel.percent(app),
            BatteryLevel.isCharging(app),
        )
        // A phone that came home is the owner who missed the notices; nothing is sent from inside a
        // zone, and the countdown in the demand waits without losing the incident (owner decision
        // 2026-11-08). The proof vibration stops because the phone is in its owner's own hands again.
        if (ProtectionRuntime.quietInSafeZone(app)) {
            SnatchAlerts.stop(app)
            val paused = SnatchPolicy.postpone(current, nowMs, intervalMs)
            store.write(paused)
            SnatchDemand.show(app, paused, settings)
            SnatchReceiver.schedule(app, paused.nextDueAtMs)
            Log.i(TAG, "the snatch is paused: the phone is inside a safe zone")
            return
        }
        // A tick copied from before the proof window (a reboot, a resumed alarm) must not escalate
        // early: the owner keeps the full ten minutes.
        val proofDueAt = current.startedAtMs + SnatchPolicy.PROOF_WINDOW_MS
        if (current.phase == SnatchPolicy.Phase.AWAITING_PROOF && nowMs < proofDueAt) {
            SnatchReceiver.schedule(app, proofDueAt)
            return
        }
        escalate(app, current, settings, intervalMs, reason = "the proof window ended")
    }

    /**
     * The wrong-PIN accelerator: a thief who tries the lock while a snatch is open does not wait for
     * the ladder's next rung. Returns true when the attempt was taken here, so the caller does not
     * stack a second capture on the same second.
     */
    @Synchronized
    fun escalateNow(context: Context): Boolean {
        val app = context.applicationContext
        val store = SnatchStore(app)
        val incident = store.read()
        if (!incident.active) return false
        val settings = ProtectionSettingsStore(app).load()
        if (!settings.armed || !settings.snatchLockEnabled) {
            end(app, restartCountdown = false)
            return false
        }
        // At home a wrong PIN is the owner fumbling, not a thief: the safe-zone rule keeps its evidence
        // path exactly as it was and the snatch stays paused.
        if (ProtectionRuntime.quietInSafeZone(app)) {
            val intervalMs = SnatchPolicy.intervalMs(
                settings.snatchIntervalMinutes,
                BatteryLevel.percent(app),
                BatteryLevel.isCharging(app),
            )
            val paused = SnatchPolicy.postpone(incident, System.currentTimeMillis(), intervalMs)
            store.write(paused)
            SnatchDemand.show(app, paused, settings)
            SnatchReceiver.schedule(app, paused.nextDueAtMs)
            return false
        }
        val intervalMs = SnatchPolicy.intervalMs(
            settings.snatchIntervalMinutes,
            BatteryLevel.percent(app),
            BatteryLevel.isCharging(app),
        )
        escalate(app, incident, settings, intervalMs, reason = "a wrong unlock while the snatch was open")
        return true
    }

    /** The owner pressed the app's stop button: proof, exactly like unlocking with their credential. */
    @Synchronized
    fun confirmOwner(context: Context, reason: String) {
        val app = context.applicationContext
        if (!SnatchStore(app).read().active) return
        end(app, restartCountdown = true)
        runCatching { TableModeResponder.cancel(app) }
        Log.i(TAG, "ownership was proven ($reason): the snatch is closed and the countdown is back")
    }

    /**
     * Called when the app comes to the foreground. It is not proof on its own — but if the incident
     * locked the phone successfully and the keyguard is now gone, the phone's own credential was used,
     * which is exactly the proof the owner described.
     */
    fun confirmOwnerIfUnlocked(context: Context) {
        val app = context.applicationContext
        val incident = SnatchStore(app).read()
        if (!incident.active) return
        if (SnatchPolicy.unlockedSince(incident, System.currentTimeMillis(), keyguardLocked(app))) {
            confirmOwner(app, "the app came forward on an unlocked phone")
        }
    }

    /** A disarm, or the owner switching the snatch guard off: the incident ends and stays ended. */
    fun cancel(context: Context) {
        end(context.applicationContext, restartCountdown = false)
    }

    fun isActive(context: Context): Boolean = SnatchStore(context.applicationContext).read().active

    /**
     * After a locked boot the phone is still at its lock screen: the demand is re-posted as far as a
     * locked phone allows (the owner's private note and contact wait for the first unlock), and one
     * tick is set, so the ladder resumes even if nobody unlocks. A tick that cannot open the private
     * settings keeps the incident alive and re-posts the public demand every ten minutes. The alarm
     * carries nothing secret and the receiver is direct-boot aware, which is what makes this reachable
     * before the owner's credential exists.
     */
    fun resumeLockedBoot(context: Context) {
        val app = context.applicationContext
        val incident = SnatchStore(app).read()
        if (!incident.active) return
        runCatching { SnatchDemand.show(app, incident, ProtectionSettingsStore(app).load()) }
        SnatchReceiver.schedule(app, System.currentTimeMillis() + LOCKED_BOOT_TICK_MS)
        Log.w(TAG, "a snatch survived the reboot: the ladder is re-armed for after the first unlock")
    }

    /**
     * The normal boot broadcast is delivered only after the phone's credential was entered, so a
     * restarted phone that got this far has its owner in front of it. That is proof, and the countdown
     * comes back with it.
     */
    fun confirmOwnerAfterBoot(context: Context) {
        val app = context.applicationContext
        if (!SnatchStore(app).read().active) return
        confirmOwner(app, "the phone was restarted and unlocked")
    }

    private fun escalate(
        app: Context,
        incident: SnatchPolicy.Incident,
        settings: ProtectionSettings,
        intervalMs: Long,
        reason: String,
    ) {
        val nowMs = System.currentTimeMillis()
        val escalated = SnatchPolicy.escalate(incident, nowMs, intervalMs)
        SnatchStore(app).write(escalated)
        SnatchDemand.show(app, escalated, settings)
        val eventId = escalated.eventId
        val captured = eventId != null &&
            ProtectionService.requestCaptureForEvent(app, eventId, SecurityEventType.MOTION_CONFIRMED_EVENT)
        if (!captured) {
            // The camera was not reachable; the message still leaves, and it says so.
            runCatching { AlertRuntime.alert(app, AlertKind.MOTION, eventId) }
        }
        soundAlarm(app)
        SnatchAlerts.pulse(app)
        SnatchReceiver.schedule(app, escalated.nextDueAtMs)
        Log.w(
            TAG,
            "the snatch escalated ($reason): send ${escalated.ticks}, next in ${intervalMs / 60_000L} minutes, " +
                if (captured) "evidence requested" else "text only",
        )
    }

    private fun soundAlarm(app: Context) {
        runCatching { Siren.start(app) }
        handler.postDelayed({ runCatching { Siren.stop(app) } }, SIREN_MS)
    }

    private fun end(app: Context, restartCountdown: Boolean) {
        SnatchStore(app).clear()
        runCatching { SnatchDemand.hide(app) }
        runCatching { SnatchAlerts.stop(app) }
        runCatching { Siren.stop(app) }
        runCatching { SnatchReceiver.cancel(app) }
        // The settings may be unreadable when this runs on a still-locked phone (the six-hour cap or a
        // disarm before the first unlock); the teardown above must not depend on them. Without them no
        // countdown is restarted, which is right: the countdown's own schedule belongs to an unlocked
        // phone and will be re-armed by the boot resume after the owner's credential.
        val settings = runCatching { ProtectionSettingsStore(app).load() }.getOrNull()
        if (restartCountdown && settings != null && settings.armed && settings.countdownEnabled) {
            runCatching { CountdownScheduler.start(app) }
        }
    }

    private fun keyguardLocked(context: Context): Boolean = runCatching {
        (context.applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
            ?.isKeyguardLocked
            ?: true
    }.getOrDefault(true)
}

/**
 * The snatch ladder's clock (A-46). One exact alarm, set for the moment the incident says is due and
 * re-set by every tick. Not exported: it only receives the alarm SHIELDRA itself scheduled. Direct-boot
 * aware so a phone restarted with a snatch open can be reached at its lock screen.
 */
class SnatchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TICK) return
        val appContext = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                SnatchCoordinator.onTick(appContext)
            } catch (error: Exception) {
                Log.e(TAG, "the snatch tick failed: ${error.message}", error)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "SnatchReceiver"
        const val ACTION_TICK = "com.shieldra.protection.SNATCH_TICK"
        private const val REQUEST = 20_025

        fun schedule(context: Context, atMs: Long) {
            val app = context.applicationContext
            val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
            val fireAt = atMs.coerceAtLeast(System.currentTimeMillis() + 5_000L)
            val operation = pending(app)
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation)
                }
            } catch (error: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, operation)
            }
        }

        fun cancel(context: Context) {
            context.applicationContext.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
        }

        private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            REQUEST,
            Intent(context.applicationContext, SnatchReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
