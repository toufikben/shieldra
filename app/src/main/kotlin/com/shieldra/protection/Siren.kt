package com.shieldra.protection

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * A loud alarm for table mode (A-33): when the phone is lifted from where the owner set it down, an
 * option is to scare the thief off. It plays the device's alarm tone on the ALARM stream, looping,
 * and raises that stream to its maximum for the duration — so a silenced ringer does not mute it.
 * It is process-wide and idempotent: starting twice does nothing, and stopping restores the volume.
 *
 * An optional max duration on [start] bounds how long it may sound: when it elapses the siren stops
 * itself, so an escalated or remote-command siren can never loop until the battery dies. Passing
 * null (the default) leaves the stop to the caller.
 */
object Siren {
    private const val TAG = "ShieldraSiren"

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var player: MediaPlayer? = null
    private var previousVolume: Int? = null
    private var autoStop: Runnable? = null

    @Synchronized
    fun start(context: Context, maxMs: Long? = null) {
        if (player != null) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        runCatching {
            audio?.let {
                previousVolume = it.getStreamVolume(AudioManager.STREAM_ALARM)
                it.setStreamVolume(AudioManager.STREAM_ALARM, it.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            }
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                ?: return
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
            scheduleAutoStop(context.applicationContext, maxMs)
        }.onFailure {
            Log.w(TAG, "could not start the siren: ${it.message}")
            stop(context)
        }
    }

    /**
     * Bounds how long the siren may sound: after [maxMs] it stops itself, so an escalated or tamper
     * siren can never loop until the battery dies. A null [maxMs] leaves the stop to the caller,
     * which is how table mode manages its own window.
     */
    private fun scheduleAutoStop(appContext: Context, maxMs: Long?) {
        cancelAutoStop()
        if (maxMs == null || maxMs <= 0L) return
        val runnable = Runnable { stop(appContext) }
        autoStop = runnable
        handler.postDelayed(runnable, maxMs)
    }

    private fun cancelAutoStop() {
        autoStop?.let { handler.removeCallbacks(it) }
        autoStop = null
    }

    @Synchronized
    fun stop(context: Context) {
        cancelAutoStop()
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        previousVolume?.let { volume ->
            runCatching { audio?.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0) }
        }
        previousVolume = null
    }

    val isPlaying: Boolean get() = player != null

    /** Kept for API symmetry on older devices; the alarm stream handles focus on its own. */
    @Suppress("unused")
    private fun sdkGuard(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}
