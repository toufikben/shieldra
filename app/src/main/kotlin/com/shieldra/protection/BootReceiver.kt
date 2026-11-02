package com.shieldra.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shieldra.delivery.AlertKind
import com.shieldra.delivery.AlertRuntime

/**
 * Brings protection back after a reboot (amendment A-33 / A-10 relaxation, audit E-2).
 *
 * A thief who power-cycles the phone to shake off tracking finds the guards running again: this
 * receiver is direct-boot aware, so the system delivers the two broadcasts it listens for, and the
 * armed flag it acts on is read from device-protected storage — the only storage a restarted, still
 * locked phone can open.
 *
 * The two halves of the job are deliberately different, because Android makes them different:
 *
 * - **Before the first unlock** ([Intent.ACTION_LOCKED_BOOT_COMPLETED]) the phone cannot open its own
 *   database, cannot decrypt the delivery coordinates, and on Android 14+ a boot receiver is refused
 *   outright if it starts a foreground service of the camera type. Nothing that could photograph or
 *   reach the owner is possible here, so this half records the fact and goes no further.
 * - **At the first unlock** ([Intent.ACTION_BOOT_COMPLETED]) everything above becomes available again:
 *   the guards are re-armed, the countdown is re-scheduled, and the recorded restart is reported to the
 *   owner once — the message that says the phone restarted while protection was armed, which is the
 *   one thing a thief's reboot should never be allowed to hide.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val lockedBoot = action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        if (!lockedBoot && action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                val flags = BootFlags(appContext)
                if (lockedBoot) {
                    // The only honest move: remember that the phone came back while armed. Reporting it
                    // needs the owner's credential, which is exactly what has not been given yet.
                    val stamped = runCatching { flags.recordReboot() }.getOrDefault(false)
                    Log.i(
                        TAG,
                        if (stamped) {
                            "the phone restarted while protection was armed: the guards cannot run until " +
                                "the owner unlocks it, and the restart will be reported then"
                        } else {
                            "the phone restarted with protection disarmed: nothing to resume"
                        },
                    )
                    return@Thread
                }
                val settings = ProtectionSettingsStore(appContext).load()
                // The stored settings are the truth from here on, so the mirror follows them.
                runCatching { flags.sync(settings.armed) }
                val interruption = runCatching { flags.takeReboot() }.getOrDefault(BootInterruption.Nothing)
                if (settings.armed && interruption is BootInterruption.Interrupted) {
                    Log.e(
                        TAG,
                        "protection was dark for ${interruption.darkMs / 1000L} s after a restart, " +
                            "starting from ${interruption.rebootedAtEpochMs}",
                    )
                    // One message per restart, sent through the owner's configured channels: a reboot is
                    // the first thing a thief does, and the owner deserves to hear about it from the phone.
                    runCatching { AlertRuntime.alert(appContext, AlertKind.RESTART, null) }
                        .onFailure { Log.e(TAG, "the restart could not be reported to the owner: ${it.message}", it) }
                }
                if (settings.armed) ProtectionService.arm(appContext)
                if (settings.countdownEnabled) CountdownScheduler.rescheduleAfterBoot(appContext)
            } catch (error: Exception) {
                Log.e(TAG, "the boot resume failed: ${error.message}", error)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
