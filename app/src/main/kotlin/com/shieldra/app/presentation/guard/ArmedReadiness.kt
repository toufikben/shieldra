package com.shieldra.app.presentation.guard

import androidx.annotation.StringRes
import com.shieldra.app.R
import com.shieldra.app.presentation.model.ProtectionVisualState

/**
 * P3-3: whether the guards are really running, as opposed to whether the owner once armed them.
 *
 * The dashboard used to answer the second question and draw the first answer's shield: the armed flag
 * is a stored boolean, so `protectionArmed` painted [ProtectionVisualState.Protected] even when no
 * service existed. There is a real window where that is false. A phone that reboots while armed comes
 * back locked; `BootReceiver` receives `ACTION_LOCKED_BOOT_COMPLETED`, stamps the restart in
 * device-protected storage and stops, because Android refuses to let a boot receiver start a
 * camera-type foreground service before the first unlock and does not deliver `ACTION_BOOT_COMPLETED`
 * until that unlock happens. Until then nothing samples the SIM, the battery or the accelerometer, and
 * a "Protected" shield is the one claim the phone cannot make.
 *
 * [ArmedWaitingToStart] is that window, said out loud. It is not [ProtectionVisualState.Disabled] —
 * the owner did arm protection and the setting survives — and it is not `Protected` — nothing runs.
 * `Attention` is the app's existing word for "read this before you trust the screen".
 */
enum class ArmedReadiness {
    /** Protection is off; the dashboard's own disclosure line explains what that means. */
    NotArmed,

    /** Armed and the protection service is live in this process. */
    ArmedAndRunning,

    /** Armed, but no service is running: the phone has not finished starting, or a vendor killed it. */
    ArmedWaitingToStart,
    ;

    /** The shield this state may paint. Only a live service earns `Protected`. */
    fun protectionVisualState(): ProtectionVisualState = when (this) {
        ArmedAndRunning -> ProtectionVisualState.Protected
        ArmedWaitingToStart -> ProtectionVisualState.Attention
        NotArmed -> ProtectionVisualState.Disabled
    }

    /**
     * The sentence this state owes the owner, or null when the dashboard's disclosure line already
     * says everything true about it.
     */
    @get:StringRes
    val noteRes: Int?
        get() = when (this) {
            ArmedWaitingToStart -> R.string.dashboard_armed_waiting_unlock
            ArmedAndRunning, NotArmed -> null
        }

    companion object {
        /**
         * [guardsRunning] comes from `ProtectionService.isLive`, which is the service's own account of
         * itself, not the stored switch. A running service with the flag off is reported as running:
         * the phone is being watched, and hiding that would be the worse lie.
         */
        fun decide(armed: Boolean, guardsRunning: Boolean): ArmedReadiness = when {
            guardsRunning -> ArmedAndRunning
            armed -> ArmedWaitingToStart
            else -> NotArmed
        }
    }
}
