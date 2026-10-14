package com.shieldra.protection

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Records a short audio clip of what is around the phone on a confirmed event (amendment A-35), so
 * the owner can hear the thief or the surroundings. It is for a confirmed security event only, while
 * the owner-started protection service runs; nothing is shown on the phone and the clip is held in
 * memory just long enough to encrypt and send.
 */
class AudioCapture(private val context: Context) {

    /** Records [seconds] of audio as AAC/MP4 and returns its bytes and media type, or null. */
    suspend fun record(seconds: Int = 10): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }
        val file = File(context.cacheDir, "cap_${UUID.randomUUID()}.m4a")
        val recorder = newRecorder() ?: return@withContext null
        val recorded = runCatching {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            delay(seconds.coerceIn(3, 60).toLong() * 1000L)
            true
        }.getOrElse {
            Log.w(TAG, "audio record failed: ${it.message}")
            false
        }
        runCatching { if (recorded) recorder.stop() }
        runCatching { recorder.release() }
        val bytes = if (recorded && file.exists() && file.length() > 0) file.readBytes() else null
        runCatching { file.delete() }
        bytes?.let { it to "audio/mp4" }
    }

    private fun newRecorder(): MediaRecorder? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
    }.getOrNull()

    private companion object {
        const val TAG = "ShieldraAudio"
    }
}
