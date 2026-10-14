package com.shieldra.protection

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Takes one still frame from the front camera and returns its JPEG bytes, or null if no usable
 * frame could be produced (amendment A-34). It is for a confirmed security event only, started by
 * the owner's own foreground service; nothing about it is shown on the phone.
 *
 * The flow is the one the old app had already debugged on real hardware: a short repeating-request
 * warm-up for auto-exposure/white-balance (some front cameras return a white frame if captured
 * immediately), then one still capture, validated as a real JPEG before it is accepted.
 */
class CameraCapture(private val context: Context) {

    /** Captures from the front camera (the default) or the back camera when [front] is false. */
    suspend fun capture(front: Boolean = true): ByteArray? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        // Two attempts: a transient HAL error on the first often clears on the second.
        repeat(2) { attempt ->
            captureOnce(front)?.let { return it }
            if (attempt == 0) delay(700L)
        }
        return null
    }

    @android.annotation.SuppressLint("MissingPermission") // capture() returns early without CAMERA.
    private suspend fun captureOnce(front: Boolean): ByteArray? = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return@withContext null
        val cameraId = cameraId(manager, front) ?: return@withContext null
        val thread = HandlerThread("shieldra-camera").apply { start() }
        val handler = Handler(thread.looper)
        var device: CameraDevice? = null
        val size = jpegSize(manager, cameraId)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
        val result = CompletableDeferred<ByteArray?>()
        val finalRequested = AtomicBoolean(false)

        reader.setOnImageAvailableListener({ ir ->
            val image = runCatching { ir.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                if (!finalRequested.get()) return@setOnImageAvailableListener // discard warm-up frames
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                if (!result.isCompleted) result.complete(if (isValidJpeg(bytes)) bytes else null)
            } finally {
                image.close()
            }
        }, handler)

        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(reader.surface)
                        set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    }
                    runCatching {
                        camera.createCaptureSession(
                            listOf(reader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    runCatching {
                                        session.setRepeatingRequest(builder.build(), null, handler)
                                        handler.postDelayed({
                                            if (!result.isCompleted) {
                                                finalRequested.set(true)
                                                runCatching { session.capture(builder.build(), null, handler) }
                                                    .onFailure { if (!result.isCompleted) result.complete(null) }
                                            }
                                        }, 1800L)
                                    }.onFailure { if (!result.isCompleted) result.complete(null) }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    if (!result.isCompleted) result.complete(null)
                                }
                            },
                            handler,
                        )
                    }.onFailure { if (!result.isCompleted) result.complete(null) }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    if (!result.isCompleted) result.complete(null)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.w(TAG, "camera error $error")
                    if (!result.isCompleted) result.complete(null)
                }
            }, handler)
        } catch (error: Exception) {
            if (!result.isCompleted) result.complete(null)
        }

        val bytes = withTimeoutOrNull(15_000L) { result.await() }
        runCatching { device?.close() }
        runCatching { reader.close() }
        thread.quitSafely()
        bytes
    }

    private fun cameraId(manager: CameraManager, front: Boolean): String? = runCatching {
        val wanted = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == wanted
        } ?: if (front) manager.cameraIdList.firstOrNull() else null
    }.getOrNull()

    private fun jpegSize(manager: CameraManager, cameraId: String): Size = runCatching {
        manager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.JPEG)
            ?.minByOrNull { kotlin.math.abs(it.width * it.height - 640 * 480) }
            ?: Size(640, 480)
    }.getOrDefault(Size(640, 480))

    private fun isValidJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0].toInt() and 0xFF == 0xFF && bytes[1].toInt() and 0xFF == 0xD8 &&
            bytes[bytes.lastIndex - 1].toInt() and 0xFF == 0xFF && bytes[bytes.lastIndex].toInt() and 0xFF == 0xD9

    private companion object {
        const val TAG = "ShieldraCamera"
    }
}
