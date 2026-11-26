package com.shieldra.protection

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The part of a snatch the owner feels (A-46): a vibration pattern that runs for the whole ten-minute
 * proof window, and a short pulse at every escalating send.
 *
 * The pattern is handed to the platform once and then cancelled by a delayed message, so it keeps
 * going while the app is doing other work; a process that dies stops it by dying, which is still a
 * finite outcome rather than a phone buzzing until the battery is gone. All calls are best-effort:
 * a device without a vibrator, or one whose owner removed the permission, simply hears the rest of
 * the alert.
 */
object SnatchAlerts {

    private val handler = Handler(Looper.getMainLooper())

    /** A long, insistent pattern for the proof window: two seconds of signal in every three. */
    private val PROOF_PATTERN = longArrayOf(0L, 600L, 400L, 600L, 1_200L, 400L)

    fun proofWindow(context: Context) {
        val vibrator = vibrator(context) ?: return
        runCatching {
            vibrator.vibrate(VibrationEffect.createWaveform(PROOF_PATTERN, 0))
        }
        handler.postDelayed({ runCatching { vibrator.cancel() } }, SnatchPolicy.PROOF_WINDOW_MS)
    }

    /** One escalation send: a couple of seconds, not another ten minutes. */
    fun pulse(context: Context) {
        val vibrator = vibrator(context) ?: return
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(2_500L, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    fun stop(context: Context) {
        handler.removeCallbacksAndMessages(null)
        vibrator(context)?.let { runCatching { it.cancel() } }
    }

    private fun vibrator(context: Context): Vibrator? {
        val app = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
