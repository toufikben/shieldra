package com.shieldra.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shieldra.app.ShieldraApp
import com.shieldra.delivery.AlertKind
import com.shieldra.delivery.AlertLocation
import com.shieldra.delivery.AlertRuntime
import com.shieldra.domain.EventId
import com.shieldra.domain.EventRepository
import com.shieldra.domain.GuardSeverity
import com.shieldra.domain.SecurityEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The owner-started foreground service that holds the camera and location capability for every
 * guard (amendment A-34). It is started once from the dashboard (type camera|location); after that
 * a background trigger — a wrong unlock, a SIM change, the countdown — hands its capture to the
 * already-running instance, which is how camera and location keep working in the background without
 * a forbidden background foreground-service start on Android 12+/14+.
 *
 * On a capture it takes the photo and the fix in parallel, stores the photo as encrypted evidence,
 * records (or joins) the matching incident, and hands a fully-built alert to [AlertRuntime] — which
 * sends it silently through the owner's channels. Nothing is shown to whoever holds the phone; the
 * only notice is the neutral ongoing one Android requires of any foreground service.
 */
class ProtectionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val camera by lazy { CameraCapture(applicationContext) }
    private val location by lazy { LocationFix(applicationContext) }
    private val busy = AtomicBoolean(false)
    private var promoted = false
    private val battery by lazy { BatteryBreathLogic(mainThreshold = { ProtectionSettingsStore(applicationContext).load().batteryThreshold }) }
    private var batteryReceiver: android.content.BroadcastReceiver? = null
    private val simGuard by lazy { SimGuard(applicationContext) }
    private var lastSimVerdict: SimGuardLogic.Verdict = SimGuardLogic.Verdict.Normal
    private val remoteCommands by lazy { RemoteCommandPoller(applicationContext) }
    private val trackingHandler = Handler(Looper.getMainLooper())
    private var trackingTicksLeft = 0

    /**
     * After a confirmed intrusion, follows the phone (A-33 next stage): a location ping every few
     * minutes for a while, so the owner sees where the thief goes. Location only, to spare the
     * battery during the trail; it stops when protection is disarmed or the window ends.
     */
    private fun startTrackingIfEnabled(eventId: EventId, kind: AlertKind) {
        val settings = ProtectionSettingsStore(applicationContext).load()
        if (!settings.liveTrackingEnabled || !com.shieldra.billing.TierGate.allowsTracking(settings.tier)) return
        val intervalMinutes = settings.trackingIntervalMinutes.coerceAtLeast(1)
        trackingTicksLeft = (settings.trackingDurationMinutes / intervalMinutes).coerceIn(1, 120)
        scheduleTrackingTick(eventId, kind)
    }

    private fun scheduleTrackingTick(eventId: EventId, kind: AlertKind) {
        // Recomputed each tick: as the battery runs down the gap widens, so the trail lasts longer.
        val baseMinutes = ProtectionSettingsStore(applicationContext).load().trackingIntervalMinutes
        val delayMs = TrackingCadence.intervalMs(baseMinutes, batteryPercent(), batteryCharging())
        trackingHandler.postDelayed({
            if (!ProtectionSettingsStore(applicationContext).load().armed) return@postDelayed
            scope.launch {
                val fix = runCatching { location.current() }.getOrNull()
                if (fix != null) {
                    val base = AlertRuntime.snapshot(applicationContext, AlertKind.TRACKING, eventId.value.toString())
                    AlertRuntime.alert(
                        applicationContext,
                        base.copy(occurredAt = Instant.now(), location = fix, evidenceAttempted = false, photoEvidenceId = null),
                    )
                }
            }
            if (--trackingTicksLeft > 0) scheduleTrackingTick(eventId, kind)
        }, delayMs)
    }

    private fun batteryPercent(): Int? = runCatching {
        getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrNull()?.takeIf { it in 0..100 }

    private fun batteryCharging(): Boolean = runCatching {
        getSystemService(BatteryManager::class.java)?.isCharging
    }.getOrNull() ?: false

    /** Re-checks the SIM/network state and alerts once on each transition into an abnormal state. */
    private fun checkSim() {
        if (!ProtectionSettingsStore(applicationContext).load().simEnabled) return
        val verdict = simGuard.check() ?: return
        if (verdict == lastSimVerdict) return
        lastSimVerdict = verdict
        if (verdict != SimGuardLogic.Verdict.Normal) {
            capture(SecurityEventType.SIM_CONFIRMED_EVENT, null)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        liveInstance = this
    }

    /** Watches the battery while protection runs; a crossed level fires one "last breath" capture. */
    private fun startBatteryGuard() {
        if (batteryReceiver != null) return
        if (!ProtectionSettingsStore(applicationContext).load().batteryEnabled) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return
                val percent = level * 100 / scale
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
                if (battery.onLevel(percent, charging) is BatteryBreathLogic.Verdict.Breath) {
                    capture(SecurityEventType.BATTERY_SIGNAL, null)
                }
                // A battery tick is a cheap, frequent moment to also re-read the SIM state.
                checkSim()
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            batteryReceiver = receiver
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                remoteCommands.stop()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                liveInstance = null
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (!promote()) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                startBatteryGuard()
                simGuard.captureBaselineIfNeeded()
                checkSim()
                // Remote control over the owner's Telegram, when they turned it on (A-33 next stage).
                ProtectionSettingsStore(applicationContext).load().let { s ->
                    if (s.remoteCommandsEnabled && com.shieldra.billing.TierGate.allowsRemote(s.tier)) {
                        remoteCommands.start()
                    }
                }
                val kindName = intent?.getStringExtra(EXTRA_KIND)
                if (kindName != null) {
                    val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
                    val failedAttempts = intent.getIntExtra(EXTRA_FAILED_ATTEMPTS, -1).takeIf { it >= 0 }
                    capture(SecurityEventType.valueOf(kindName), failedAttempts)
                }
            }
        }
        // Armed monitoring stays up so later background triggers reach a live, camera-capable instance.
        return START_STICKY
    }

    override fun onDestroy() {
        if (liveInstance === this) liveInstance = null
        trackingHandler.removeCallbacksAndMessages(null)
        remoteCommands.stop()
        batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
        batteryReceiver = null
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    /** Runs one capture off the main thread; a second request while one is in flight is dropped. */
    private fun capture(type: SecurityEventType, failedAttempts: Int?) {
        if (!busy.compareAndSet(false, true)) {
            Log.d(TAG, "capture already in progress; dropping the duplicate")
            return
        }
        scope.launch {
            val wakeLock = acquireWakeLock()
            try {
                val storage = (applicationContext as? ShieldraApp)?.installedStorage
                val repository = storage?.eventRepository ?: return@launch
                val grouped = type == SecurityEventType.LOCK_CONFIRMED_EVENT || type == SecurityEventType.SIM_CONFIRMED_EVENT
                val binding = ProtectionEvents(repository).openOrBind(type, grouped, severityFor(type))
                captureStoreDispatch(storage, repository, binding.event.id, alertKindFor(type), failedAttempts)
                // A new intrusion incident starts the live trail (if the owner turned it on).
                val intrusion = type == SecurityEventType.LOCK_CONFIRMED_EVENT ||
                    type == SecurityEventType.MOTION_CONFIRMED_EVENT ||
                    type == SecurityEventType.SIM_CONFIRMED_EVENT
                if (binding.isNew && intrusion) startTrackingIfEnabled(binding.event.id, alertKindFor(type))
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                busy.set(false)
            }
        }
    }

    /** Captures the configured media (front photo, optional back photo and audio), stores each as
     *  evidence on [eventId], and sends the alert with their ids and the fix. */
    private suspend fun captureStoreDispatch(
        storage: com.shieldra.storage.LocalShieldraStorage,
        repository: EventRepository,
        eventId: EventId,
        kind: AlertKind,
        failedAttempts: Int?,
    ) {
        val settings = ProtectionSettingsStore(applicationContext).load()
        val tier = settings.tier
        // The free plan sends a location only; the paid plan adds photos and audio (owner's split).
        val wantsPhoto = com.shieldra.billing.TierGate.allowsPhoto(tier)
        val wantsBack = settings.dualCameraEnabled && com.shieldra.billing.TierGate.allowsBackCamera(tier)
        val wantsAudio = settings.audioEnabled && com.shieldra.billing.TierGate.allowsAudio(tier)
        val media = supervisorScope {
            val audioJob = async {
                if (wantsAudio) runCatching { AudioCapture(applicationContext).record(settings.audioSeconds) }.getOrNull() else null
            }
            val fixJob = async { runCatching { location.current() }.getOrNull() }
            // Two cameras cannot open at once on most phones, so capture front then back in sequence.
            val front = if (wantsPhoto) runCatching { camera.capture(front = true) }.getOrNull() else null
            val back = if (wantsBack) runCatching { camera.capture(front = false) }.getOrNull() else null
            CapturedMedia(front, back, audioJob.await()?.first, fixJob.await())
        }
        val frontId = storeEvidence(storage, repository, eventId, media.front)
        val backId = storeEvidence(storage, repository, eventId, media.back)
        val audioId = storeEvidence(storage, repository, eventId, media.audio)
        dispatchAlert(eventId, kind, media.fix, frontId, backId, audioId, failedAttempts)
    }

    private class CapturedMedia(
        val front: ByteArray?,
        val back: ByteArray?,
        val audio: ByteArray?,
        val fix: AlertLocation?,
    )

    private fun storeEvidence(
        storage: com.shieldra.storage.LocalShieldraStorage,
        repository: EventRepository,
        eventId: EventId,
        bytes: ByteArray?,
    ): String? {
        if (bytes == null) return null
        val id = "cap_" + UUID.randomUUID().toString().replace("-", "")
        val written = runCatching { storage.evidenceFiles.write(id, bytes) }.map { id }.getOrNull() ?: return null
        runCatching { repository.recordEvidence(eventId, com.shieldra.domain.EvidenceReference(written)) }
        return written
    }

    /**
     * Captures a photo and a fix for an event that already exists (the Motion guard created it) and
     * sends the alert for it — no second event. Used only while the service is live (armed), which is
     * what keeps the camera working in the background.
     */
    private fun captureForExisting(eventIdStr: String, kind: AlertKind) {
        val eventId = runCatching { EventId.from(java.util.UUID.fromString(eventIdStr)) }.getOrNull() ?: return
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            val wakeLock = acquireWakeLock()
            try {
                val storage = (applicationContext as? ShieldraApp)?.installedStorage
                val repository = storage?.eventRepository ?: return@launch
                captureStoreDispatch(storage, repository, eventId, kind, failedAttempts = null)
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                busy.set(false)
            }
        }
    }

    private fun dispatchAlert(
        eventId: EventId,
        kind: AlertKind,
        fix: AlertLocation?,
        evidenceId: String?,
        backEvidenceId: String?,
        audioEvidenceId: String?,
        failedAttempts: Int?,
    ) {
        val base = AlertRuntime.snapshot(applicationContext, kind, eventId.value.toString())
        AlertRuntime.alert(
            applicationContext,
            base.copy(
                occurredAt = Instant.now(),
                location = fix,
                photoEvidenceId = evidenceId,
                photoBackEvidenceId = backEvidenceId,
                audioEvidenceId = audioEvidenceId,
                evidenceAttempted = true,
                failedAttempts = failedAttempts,
            ),
        )
    }

    private fun promote(): Boolean {
        if (promoted) return true
        val notice = buildNotice()
        val type = foregroundType()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type != 0) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notice, type)
            } else {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notice, 0)
            }
            promoted = true
            true
        } catch (error: Exception) {
            Log.w(TAG, "could not promote protection service: ${error.message}")
            false
        }
    }

    private fun foregroundType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var type = 0
        if (hasPermission(android.Manifest.permission.CAMERA)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasPermission(android.Manifest.permission.RECORD_AUDIO)) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return type
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun buildNotice(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(com.shieldra.app.R.string.notification_channel_protection),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply { setShowBadge(false) },
            )
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.shieldra.app.R.drawable.ic_shield_notification)
            .setContentTitle(getString(com.shieldra.app.R.string.notification_protection_title))
            .setContentText(getString(com.shieldra.app.R.string.notification_protection_text))
            .setOngoing(true)
            .apply { pending?.let { setContentIntent(it) } }
            .build()
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? = runCatching {
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
        power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shieldra:capture").apply {
            setReferenceCounted(false)
            acquire(30_000L)
        }
    }.getOrNull()

    private fun severityFor(type: SecurityEventType): GuardSeverity = when (type) {
        SecurityEventType.COUNTDOWN_SIGNAL, SecurityEventType.BATTERY_SIGNAL -> GuardSeverity.MEDIUM
        else -> GuardSeverity.CRITICAL
    }

    private fun alertKindFor(type: SecurityEventType): AlertKind = when (type) {
        SecurityEventType.LOCK_CONFIRMED_EVENT -> AlertKind.LOCK
        SecurityEventType.SIM_CONFIRMED_EVENT -> AlertKind.SIM
        SecurityEventType.BATTERY_SIGNAL -> AlertKind.BATTERY
        SecurityEventType.COUNTDOWN_SIGNAL -> AlertKind.COUNTDOWN
        SecurityEventType.MOTION_CONFIRMED_EVENT -> AlertKind.MOTION
        SecurityEventType.PANIC_ACTIVATION -> AlertKind.PANIC
        SecurityEventType.CONFIRMED_SECURITY_EVENT -> AlertKind.PANIC
    }

    companion object {
        private const val TAG = "ProtectionService"
        private const val CHANNEL_ID = "shieldra_protection"
        private const val NOTIFICATION_ID = 20_003

        const val ACTION_ARM = "com.shieldra.protection.ARM"
        const val ACTION_CAPTURE = "com.shieldra.protection.CAPTURE"
        const val ACTION_STOP = "com.shieldra.protection.STOP"
        const val EXTRA_KIND = "kind"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_FAILED_ATTEMPTS = "failed_attempts"

        @Volatile
        private var liveInstance: ProtectionService? = null

        /** True while a camera-capable instance exists, so a background trigger can reach it. */
        val isLive: Boolean get() = liveInstance != null

        /**
         * Captures a photo and a fix for an already-created event (the Motion guard's) and sends it,
         * when the service is live. Returns true if a live instance took it; false means protection is
         * not armed, so the caller keeps its own text-only path.
         */
        fun captureForEvent(eventId: String, kind: AlertKind): Boolean {
            val live = liveInstance ?: return false
            live.captureForExisting(eventId, kind)
            return true
        }

        /** Starts (or keeps) the persistent service. Called from the dashboard and from boot resume. */
        fun arm(context: Context) {
            val intent = Intent(context, ProtectionService::class.java).setAction(ACTION_ARM)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ProtectionService::class.java).setAction(ACTION_STOP))
        }

        /**
         * Asks for one capture of [type]. A live instance takes it directly; otherwise we try to
         * start the service with the request, which a background caller may be denied — the event is
         * still recorded by the instance when one is running.
         */
        fun requestCapture(context: Context, type: SecurityEventType, failedAttempts: Int? = null) {
            val live = liveInstance
            if (live != null) {
                live.capture(type, failedAttempts)
                return
            }
            val intent = Intent(context, ProtectionService::class.java)
                .setAction(ACTION_CAPTURE)
                .putExtra(EXTRA_KIND, type.name)
                .apply { failedAttempts?.let { putExtra(EXTRA_FAILED_ATTEMPTS, it) } }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
