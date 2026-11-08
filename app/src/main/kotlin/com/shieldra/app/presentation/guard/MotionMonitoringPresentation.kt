package com.shieldra.app.presentation.guard

import androidx.annotation.StringRes
import com.shieldra.app.R
import com.shieldra.app.presentation.model.GuardStatus
import com.shieldra.runtime.MonitoringClaim

/**
 * The only Motion dashboard projection SHIELDRA may make (amendment A-18, correction (ب)).
 *
 * The input is the claim the running service publishes about itself, so a press that Android refused,
 * a service that came up without registering the accelerometer, and a stopped service all render as
 * the guard not monitoring. [GuardStatus.Signal] is deliberately unreachable here: a signal is a
 * detector's verdict about a movement, and this object projects whether the detector runs at all.
 * It stays free of Android and Compose so the no-overclaiming guarantee is unit-testable, like
 * [LockGuardDecision].
 */
object MotionMonitoringPresentation {

    fun guardStatus(claim: MonitoringClaim): GuardStatus =
        if (claim.claimsProtection) GuardStatus.Active else GuardStatus.Disabled

    @StringRes
    fun subtitleRes(claim: MonitoringClaim): Int =
        if (claim.claimsProtection) {
            R.string.guard_subtitle_motion_active
        } else {
            R.string.guard_subtitle_inactive
        }

    /**
     * What the Protection screen says about Motion while protection is armed (owner report 2026-11-08).
     * A guard that is on but not monitoring used to be invisible there: arming could not start it
     * without the notification permission, and the refusal existed only in the log. The status makes
     * the missing step, and its one action, visible.
     */
    enum class SetupStatus { ACTIVE, NEEDS_NOTIFICATION_PERMISSION, NOT_STARTED }

    fun setupStatus(claim: MonitoringClaim, notificationsGranted: Boolean): SetupStatus = when {
        claim.claimsProtection -> SetupStatus.ACTIVE
        !notificationsGranted -> SetupStatus.NEEDS_NOTIFICATION_PERMISSION
        else -> SetupStatus.NOT_STARTED
    }

    @StringRes
    fun setupTextRes(status: SetupStatus): Int = when (status) {
        SetupStatus.ACTIVE -> R.string.protection_motion_active
        SetupStatus.NEEDS_NOTIFICATION_PERMISSION -> R.string.protection_motion_needs_notifications
        SetupStatus.NOT_STARTED -> R.string.protection_motion_not_started
    }
}
