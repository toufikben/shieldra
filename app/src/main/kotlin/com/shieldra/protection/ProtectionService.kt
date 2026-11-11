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
    private var shutdownReceiver: android.content.BroadcastReceiver? = null
    private val simGuard by lazy { SimGuard(applicationContext) }
    private var lastSimVerdict: SimGuardLogic.Verdict = SimGuardLogic.Verdict.Normal
    private var networkLostChecks = 0
    private val remoteCommands by lazy { RemoteCommandPoller(applicationContext) }
    private val trackingHandler = Handler(Looper.getMainLooper())
    private var trackingTicksLeft = 0

    // Confirmed-intrusion timestamps within the escalation window; a burst sounds the siren.
    private val intrusionTimes = java.util.concurrent.CopyOnWriteArrayList<Long>()

    private val heartbeatHandler = Handler(Looper.getMainLooper())

    /** Every minute while armed: a real pass over the SIM guard, which also stamps "last check" (A-38). */
    private fun startHeartbeat() {
        heartbeatHandler.removeCallbacksAndMessages(null)
        heartbeatHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!ProtectionSettingsStore(applicationContext).load().armed) {
                    // The owner disarmed, and a stop request can be refused by the platform. Holding a
                    // foreground service that still owns the camera after that would be the app keeping
                    // a capability its owner switched off, so the instance tears itself down.
                    Log.i(TAG, "protection is no longer armed: this instance is standing down")
                    stopSelf()
                    return
                }
                checkSim()
                refreshNoticeNow()
                // The one moment a request that had to wait is guaranteed to be looked at again.
                drainQueue()
                heartbeatHandler.postDelayed(this, HEARTBEAT_MS)
            }
        }, HEARTBEAT_MS)
    }

    /**
     * Arming takes one safe-zone reading at once and hands the rest to the alarm (A-45 D2), which keeps
     * sampling even after this instance is gone. Nothing is scheduled when the owner has no zones or has
     * turned the guard off.
     */
    private fun startSafeZoneSampling() {
        val store = ProtectionSettingsStore(applicationContext)
        val settings = store.load()
        if (!settings.geofenceEnabled || store.safeZones().isEmpty()) {
            GeofenceTicker.cancel(applicationContext)
            ZoneStateStore(applicationContext).clear()
            return
        }
        scope.launch {
            runCatching { sampleSafeZoneNow(applicationContext) }
                .onFailure { Log.w(TAG, "the first safe-zone reading failed: ${it.message}") }
        }
        GeofenceTicker.schedule(applicationContext, settings.geofenceIntervalMinutes)
    }

    /** On a burst of confirmed intrusions within the window, stop being discreet and sound the siren. */
    private fun maybeEscalate() {
        val settings = ProtectionSettingsStore(applicationContext).load()
        if (!settings.escalationEnabled) return
        // Inside a fresh safe zone the owner's own movement is expected, so it neither counts toward a
        // burst nor sounds the alarm (owner decision 2026-11-08). Outside, or with no trusted reading,
        // the burst is counted and the siren sounds exactly as before.
        if (ProtectionRuntime.quietInSafeZone(applicationContext)) return
        val now = System.currentTimeMillis()
        intrusionTimes.add(now)
        intrusionTimes.removeIf { now - it > EscalationPolicy.WINDOW_MS }
        if (EscalationPolicy.shouldEscalate(intrusionTimes.toList(), now, settings.escalationThreshold)) {
            Siren.start(applicationContext)
        }
    }

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
        // A real guard pass is happening: stamp it so the dashboard's "last check" is a fact (A-38).
        ProtectionSettingsStore(applicationContext).markCheck()
        val settings = ProtectionSettingsStore(applicationContext).load()
        if (!settings.simEnabled) return
        val verdict = simGuard.check() ?: return
        // A tunnel or a lift lasts a minute; a thief cutting the network lasts. Only a loss seen on two
        // checks in a row (about two minutes) is reported, so a short dead spot is not an alarm.
        if (verdict == SimGuardLogic.Verdict.NetworkLost) {
            networkLostChecks += 1
            if (networkLostChecks < NETWORK_LOST_CONFIRM_CHECKS) return
        } else {
            networkLostChecks = 0
        }
        if (verdict == lastSimVerdict) return
        lastSimVerdict = verdict
        if (verdict != SimGuardLogic.Verdict.Normal) {
            capture(SecurityEventType.SIM_CONFIRMED_EVENT, null)
            // Composite tamper: a SIM swap with the screen dark and the network cut is the classic
            // way a stolen phone is hidden from recovery — sound the siren at once if the owner
            // opted into escalation, without waiting for a burst to build up. The SIM alert itself
            // above is evidence and always leaves; only this siren yields inside a fresh safe zone
            // (owner decision 2026-11-08), where a loud alarm is the nuisance.
            if (settings.escalationEnabled) {
                val signals = CompositeTamper.Signals(
                    simAbnormal = true,
                    screenInteractive = screenInteractive(),
                    online = online(),
                )
                if (CompositeTamper.isHighRisk(signals) && !ProtectionRuntime.quietInSafeZone(applicationContext)) {
                    Siren.start(applicationContext)
                }
            }
        }
    }

    private fun screenInteractive(): Boolean = runCatching {
        getSystemService(PowerManager::class.java)?.isInteractive
    }.getOrNull() ?: true

    private fun online(): Boolean = runCatching {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return@runCatching true
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrNull() ?: true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        liveInstance = this
    }

    /**
     * A thief who powers the phone off silences every guard, so the owner gets one last message as the
     * shutdown begins (sent best-effort: the system gives the app only a few seconds).
     */
    private fun startShutdownGuard() {
        if (shutdownReceiver != null) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!ProtectionSettingsStore(applicationContext).load().armed) return
                runCatching { com.shieldra.delivery.AlertRuntime.alert(applicationContext, com.shieldra.delivery.AlertKind.SHUTDOWN, null) }
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                android.content.IntentFilter(Intent.ACTION_SHUTDOWN),
                ContextCompat.RECEIVER_EXPORTED,
            )
            shutdownReceiver = receiver
        }
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
                ProtectionSettingsStore(applicationContext).markCheck()
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
                val kindName = intent?.getStringExtra(EXTRA_KIND)
                val eventId = intent?.getStringExtra(EXTRA_EVENT_ID)
                val failedAttempts = intent?.getIntExtra(EXTRA_FAILED_ATTEMPTS, -1)?.takeIf { it >= 0 }
                val requested = kindName?.let { name ->
                    runCatching { SecurityEventType.valueOf(name) }.getOrElse {
                        Log.e(TAG, "an unknown capture request arrived: $name")
                        null
                    }
                }
                if (!promote()) {
                    // A service that cannot become foreground holds no camera, so it has to stop. What
                    // it may not do is stop quietly: the request that woke it goes back to the queue for
                    // the retry alarm, and the owner is told in text, now, that the guard fired without
                    // a photo. That is the difference between a late alert and no alert at all.
                    Log.e(
                        TAG,
                        if (requested == null) {
                            "protection could not promote itself: the guards are armed but nothing is watching"
                        } else {
                            "protection could not promote itself: the $requested capture did not run"
                        },
                    )
                    requested?.let {
                        declareFailure(applicationContext, it, eventId, failedAttempts, "the service could not start in the foreground")
                    }
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                startBatteryGuard()
                startShutdownGuard()
                startSafeZoneSampling()
                startHeartbeat()
                simGuard.captureBaselineIfNeeded()
                checkSim()
                // Remote control over the owner's Telegram, when they turned it on (A-33 next stage).
                ProtectionSettingsStore(applicationContext).load().let { s ->
                    if (s.remoteCommandsEnabled && com.shieldra.billing.TierGate.allowsRemote(s.tier)) {
                        remoteCommands.start()
                    }
                }
                // Newest first: the request that woke this instance is the one still in front of the lens.
                requested?.let { runCapture(it, eventId, failedAttempts) }
                drainQueue()
            }
        }
        // Armed monitoring stays up so later background triggers reach a live, camera-capable instance.
        return START_STICKY
    }

    override fun onDestroy() {
        if (liveInstance === this) liveInstance = null
        trackingHandler.removeCallbacksAndMessages(null)
        // Deliberately no GeofenceTicker.cancel here (A-45): the safe-zone alarm must outlive this
        // instance, because on the owner's skin the service is what dies first and the zone state is
        // what the quiet decision reads. Disarming is what stops it, in ProtectionRuntime.
        heartbeatHandler.removeCallbacksAndMessages(null)
        remoteCommands.stop()
        batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
        batteryReceiver = null
        shutdownReceiver?.let { runCatching { unregisterReceiver(it) } }
        shutdownReceiver = null
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    /**
     * Runs one capture, binding it to the incident [eventId] when the caller already created one (the
     * Motion guard) and opening or joining the incident for [type] otherwise.
     */
    private fun runCapture(type: SecurityEventType, eventId: String?, failedAttempts: Int?) {
        if (eventId != null) captureForExisting(eventId, type) else capture(type, failedAttempts)
    }

    /**
     * Runs the requests that had to wait — for the camera, for the storage, for this instance.
     *
     * Skipped while a capture is in flight: this instance would only hand every request back to the
     * queue because its own camera is open, and the next heartbeat drains it for real.
     */
    private fun drainQueue() {
        if (busy.get()) return
        drainPendingCaptures(applicationContext)
    }

    /**
     * Runs one capture off the main thread. A second request while one is in flight is not dropped:
     * it goes to the queue and the next heartbeat or retry alarm runs it, because a guard that fired
     * and produced nothing is the failure the owner can never see coming.
     */
    private fun capture(type: SecurityEventType, failedAttempts: Int?) {
        if (!busy.compareAndSet(false, true)) {
            Log.i(TAG, "a $type capture waited: the camera was already open")
            queueForRetry(type, eventId = null, failedAttempts, "the camera was busy")
            return
        }
        scope.launch {
            val wakeLock = acquireWakeLock()
            try {
                val storage = (applicationContext as? ShieldraApp)?.installedStorage
                if (storage == null) {
                    // Still recovering rather than broken (the app opens storage off the main thread),
                    // so this one waits for the next drain instead of being lost.
                    Log.e(TAG, "a $type capture waited: no storage is installed")
                    queueForRetry(type, eventId = null, failedAttempts, "the storage was not installed yet")
                    return@launch
                }
                val repository = storage.eventRepository
                val grouped = type == SecurityEventType.LOCK_CONFIRMED_EVENT || type == SecurityEventType.SIM_CONFIRMED_EVENT
                val binding = ProtectionEvents(repository).openOrBind(type, grouped, severityFor(type))
                captureStoreDispatch(storage, repository, binding.event.id, type, failedAttempts, binding.isNew)
                // A new intrusion incident starts the live trail (if the owner turned it on).
                val intrusion = type == SecurityEventType.LOCK_CONFIRMED_EVENT ||
                    type == SecurityEventType.MOTION_CONFIRMED_EVENT ||
                    type == SecurityEventType.SIM_CONFIRMED_EVENT
                if (binding.isNew && intrusion) {
                    startTrackingIfEnabled(binding.event.id, alertKindFor(type))
                    maybeEscalate()
                }
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                busy.set(false)
            }
        }
    }

    /** Captures the configured media (front photo, optional back photo and audio), stores each as
     *  evidence on [eventId], and sends the alert with their ids and the fix when
     *  [LockAlertPolicy] says this capture is the one that speaks for its incident. */
    private suspend fun captureStoreDispatch(
        storage: com.shieldra.storage.LocalShieldraStorage,
        repository: EventRepository,
        eventId: EventId,
        type: SecurityEventType,
        failedAttempts: Int?,
        incidentIsNew: Boolean,
    ) {
        val kind = alertKindFor(type)
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
        val frontId = storeEvidence(storage, repository, eventId, media.front, "FRONT_PHOTO")
        val backId = storeEvidence(storage, repository, eventId, media.back, "BACK_PHOTO")
        val audioId = storeEvidence(storage, repository, eventId, media.audio, "AUDIO")
        // The fix is also kept as an encrypted evidence payload (A-31) so the event detail can show
        // the real recorded position later, not only send it in the alert.
        val locationBytes = media.fix?.let {
            "${it.latitude}|${it.longitude}|${it.accuracyMeters ?: ""}|${it.provider}".toByteArray(Charsets.UTF_8)
        }
        storeEvidence(storage, repository, eventId, locationBytes, "LOCATION")
        // The capture always happened and its evidence is stored; only the message is rationed, so a
        // thief who keeps guessing cannot turn the owner's channel into a stream of signals. A Motion
        // inside a fresh safe zone is the owner's own home or office, so its message is the nuisance
        // the owner removed (2026-11-08): the evidence above stays, the message does not. Every other
        // kind asks LockAlertPolicy alone — a wrong unlock, a SIM swap and a battery last breath are
        // evidence, never noise, and this policy must never be in their path.
        val motionInsideSafeZone = type == SecurityEventType.MOTION_CONFIRMED_EVENT &&
            ProtectionRuntime.quietInSafeZone(applicationContext)
        if (!motionInsideSafeZone &&
            LockAlertPolicy.announces(type, incidentIsNew, failedAttempts, settings.lockThreshold)
        ) {
            dispatchAlert(eventId, kind, media.fix, frontId, backId, audioId, failedAttempts)
        }
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
        kind: String,
    ): String? {
        if (bytes == null) return null
        val id = "cap_" + UUID.randomUUID().toString().replace("-", "")
        val written = runCatching { storage.evidenceFiles.write(id, bytes) }.map { id }.getOrNull() ?: return null
        runCatching { repository.recordEvidence(eventId, com.shieldra.domain.EvidenceReference(written, kind)) }
        return written
    }

    /**
     * Captures a photo and a fix for an event that already exists (the Motion guard created it) and
     * sends the alert for it — no second event. Used only while the service is live (armed), which is
     * what keeps the camera working in the background.
     *
     * @return true when the capture started or was safely queued for a retry, false when neither could
     *   be arranged — only then does the caller fall back to its own text alert, so a request is never
     *   reported as taken and then vanish.
     */
    private fun captureForExisting(eventIdStr: String, type: SecurityEventType): Boolean {
        val eventId = runCatching { EventId.from(java.util.UUID.fromString(eventIdStr)) }.getOrNull()
        if (eventId == null) {
            Log.e(TAG, "a $type capture was asked for something that is not an event id")
            return false
        }
        if (!busy.compareAndSet(false, true)) {
            Log.i(TAG, "a $type capture waited for its own event: the camera was already open")
            return queueForRetry(type, eventIdStr, failedAttempts = null, "the camera was busy")
        }
        scope.launch {
            val wakeLock = acquireWakeLock()
            try {
                val storage = (applicationContext as? ShieldraApp)?.installedStorage
                if (storage == null) {
                    Log.e(TAG, "a $type capture waited for its own event: no storage is installed")
                    queueForRetry(type, eventIdStr, failedAttempts = null, "the storage was not installed yet")
                    return@launch
                }
                captureStoreDispatch(storage, storage.eventRepository, eventId, type, failedAttempts = null, incidentIsNew = true)
            } finally {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                busy.set(false)
            }
        }
        return true
    }

    /**
     * Keeps a capture that could not run while this instance is alive: its own heartbeat comes back for
     * it within a minute, and the retry alarm covers the case where the instance is stopped before then.
     * A request that was already waiting adds nothing, so retries cannot stack.
     *
     * @return whether the request is now queued.
     */
    private fun queueForRetry(
        type: SecurityEventType,
        eventId: String?,
        failedAttempts: Int?,
        reason: String,
    ): Boolean {
        val queued = requeue(applicationContext, type, eventId, failedAttempts, reason)
        if (queued) PendingCaptureReceiver.schedule(applicationContext)
        return queued
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
            // Not a warning to file away: a service Android will not put in the foreground holds no
            // camera and can be stopped at any moment, so the owner has to be able to find this line.
            // The caller stops the instance and hands the request to the queue instead of dropping it.
            Log.e(TAG, "could not promote protection service: ${error.message}", error)
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
                ).apply {
                    setShowBadge(false)
                    // A channel's lock-screen visibility overrides the one set on the notification, so
                    // the secrecy has to be declared here as well. An already-created channel keeps the
                    // settings it was created with, which is why the public version above is the
                    // upgrade-path safety net.
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                },
            )
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        // The notice says which app is holding the camera and, while a countdown runs, when the next
        // signal leaves the phone. That is a map of the owner's defences, and a thief glancing at a
        // locked screen should not get it (audit report §4, the recon leak). VISIBILITY_SECRET keeps
        // the whole notification off the lock screen. The public version is the fallback for a skin
        // that draws a body anyway: it can only ever say the app's name and that the content is
        // hidden, never the title, the camera holder or the countdown.
        val hidden = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.shieldra.app.R.drawable.ic_shield_notification)
            .setContentTitle(getString(com.shieldra.app.R.string.app_name))
            .setContentText(getString(com.shieldra.app.R.string.notification_hidden_body))
            .build()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.shieldra.app.R.drawable.ic_shield_notification)
            .setContentTitle(getString(com.shieldra.app.R.string.notification_protection_title))
            .setContentText(getString(com.shieldra.app.R.string.notification_protection_text))
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setPublicVersion(hidden)
            .apply {
                countdownDeadlineEpochMs()?.let { due ->
                    // Plain text re-posted every heartbeat: some skins do not draw a notification chronometer.
                    setContentText(
                        getString(
                            com.shieldra.app.R.string.notification_next_signal,
                            RemainingTimeText.of(this@ProtectionService, due - System.currentTimeMillis()),
                        ),
                    )
                }
            }
            .apply { pending?.let { setContentIntent(it) } }
            .build()
    }

    /** The stored next-signal time, only while the periodic signal is on and a cycle is running. */
    private fun countdownDeadlineEpochMs(): Long? {
        val store = ProtectionSettingsStore(applicationContext)
        if (!store.load().countdownEnabled) return null
        return store.countdownDeadline()?.epochDeadlineMs?.takeIf { it > System.currentTimeMillis() }
    }

    private fun refreshNoticeNow() {
        if (!promoted) return
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotice())
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

    companion object {
        private const val TAG = "ProtectionService"
        private const val CHANNEL_ID = "shieldra_protection"
        private const val NOTIFICATION_ID = 20_003
        private const val HEARTBEAT_MS = 60_000L
        private const val NETWORK_LOST_CONFIRM_CHECKS = 2

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

        /** Re-posts the ongoing notice, so it shows the new next-signal time (or none after a cancel). */
        fun refreshNotice() {
            liveInstance?.refreshNoticeNow()
        }

        /**
         * Captures a photo and a fix for an already-created event (the Motion guard's) and sends it,
         * when the service is live. Returns true if a live instance took it or queued it for a retry;
         * false means protection is not armed and nothing could be arranged, so the caller keeps its
         * own text-only path.
         */
        fun captureForEvent(eventId: String, type: SecurityEventType): Boolean {
            val live = liveInstance ?: return false
            return live.captureForExisting(eventId, type)
        }

        /**
         * Captures evidence for an event that already exists, starting the service when it is not live
         * (A-46: the snatch ladder ticks from an alarm, often with no service alive). A live instance
         * takes the capture directly; otherwise the same request is carried by [ACTION_CAPTURE], whose
         * failure path queues it and tells the owner. The return value says the request was accepted —
         * by a live instance or by a start that Android did not refuse — never that a photo exists.
         */
        fun requestCaptureForEvent(context: Context, eventId: String, type: SecurityEventType): Boolean {
            val live = liveInstance
            if (live != null) return live.captureForExisting(eventId, type)
            val app = context.applicationContext
            val intent = Intent(app, ProtectionService::class.java)
                .setAction(ACTION_CAPTURE)
                .putExtra(EXTRA_KIND, type.name)
                .putExtra(EXTRA_EVENT_ID, eventId)
            return runCatching { ContextCompat.startForegroundService(app, intent) }
                .onFailure { error ->
                    Log.e(
                        TAG,
                        "the $type capture for event $eventId could not start the service " +
                            "(${error.javaClass.simpleName}): the caller must send its text alert",
                    )
                }
                .isSuccess
        }

        /**
         * Which alert a guard's event is announced as. The capture path and the queue of captures that
         * could not run both need it, so it lives beside the instance handle rather than inside it.
         */
        fun alertKindFor(type: SecurityEventType): AlertKind = when (type) {
            SecurityEventType.LOCK_CONFIRMED_EVENT -> AlertKind.LOCK
            SecurityEventType.SIM_CONFIRMED_EVENT -> AlertKind.SIM
            SecurityEventType.BATTERY_SIGNAL -> AlertKind.BATTERY
            SecurityEventType.COUNTDOWN_SIGNAL -> AlertKind.COUNTDOWN
            SecurityEventType.MOTION_CONFIRMED_EVENT -> AlertKind.MOTION
            SecurityEventType.PANIC_ACTIVATION -> AlertKind.PANIC
            SecurityEventType.CONFIRMED_SECURITY_EVENT -> AlertKind.PANIC
        }

        /**
         * Starts (or keeps) the persistent service. Called from the dashboard and from boot resume.
         * A refused start is reported, never swallowed: the owner has armed protection and is entitled
         * to know that nothing is watching.
         */
        fun arm(context: Context) {
            val intent = Intent(context, ProtectionService::class.java).setAction(ACTION_ARM)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure {
                    Log.e(
                        TAG,
                        "protection could not be started (${it.javaClass.simpleName}: ${it.message}) " +
                            "while the owner's armed flag stays on",
                        it,
                    )
                }
        }

        /** Stops the guards. The owner pressed the button, so a refused stop is only logged. */
        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, ProtectionService::class.java).setAction(ACTION_STOP)) }
                .onFailure { Log.w(TAG, "the protection stop request was refused: ${it.message}") }
        }

        /**
         * One safe-zone reading: where the phone is and when this became known. The service takes one at
         * arm and [GeofenceTickReceiver] takes the rest, so the state the quiet decision reads never
         * depends on a service instance staying alive.
         *
         * Refusals write nothing on purpose. A fix older than two intervals is not evidence about where
         * the phone is now, and no fix at all is the same case: leaving the stored reading alone lets it
         * age out, and an aged-out reading fails the quiet rule closed rather than muting a snatch at the
         * door.
         *
         * A-47 D2: the reading is a silencer, nothing else. Crossing out of a zone reports nothing —
         * outside a zone every guard simply returns to its own base behavior.
         */
        suspend fun sampleSafeZoneNow(context: Context) {
            val app = context.applicationContext
            val store = ProtectionSettingsStore(app)
            val settings = store.load()
            val zones = store.safeZones().map(SafeZone::zone)
            if (zones.isEmpty()) return
            val fix = runCatching { LocationFix(app).current() }.getOrNull()
            if (fix == null) {
                Log.i(TAG, "the safe-zone tick got no fix: the stored reading is left to go stale")
                return
            }
            val nowMs = System.currentTimeMillis()
            val staleAfterMs = GeofenceTicker.staleAfterMs(settings.geofenceIntervalMinutes)
            val fixAgeMs = nowMs - fix.fixedAt.toEpochMilli()
            if (fixAgeMs > staleAfterMs) {
                Log.w(TAG, "the safe-zone fix is ${fixAgeMs / 1000L} s old, too old to trust: nothing was written")
                return
            }
            val previous = ZoneStateStore(app).read()
            // With no reading yet, assume inside: a first fix is not a crossing.
            val wasInside = previous?.inside ?: true
            val accuracy = (fix.accuracyMeters ?: 0f).toDouble()
            val inside = GeofenceLogic.nextInsideState(fix.latitude, fix.longitude, accuracy, zones, wasInside)
            ZoneStateStore(app).write(inside = inside, atMs = nowMs)
            if (!inside) {
                Log.i(TAG, "the phone is outside every safe zone: every guard keeps its base behavior")
            }
        }

        /**
         * Asks for one capture of [type]. A live instance takes it directly; otherwise the service is
         * started with the request, which a background caller may be denied. A denial is not the end of
         * the story: the request is queued, a short alarm retries it, and the owner is told in text
         * right away that the guard fired — so "nothing happened" is never mistaken for "nothing was
         * seen".
         */
        fun requestCapture(context: Context, type: SecurityEventType, failedAttempts: Int? = null) {
            val live = liveInstance
            if (live != null) {
                live.capture(type, failedAttempts)
                return
            }
            val app = context.applicationContext
            val intent = Intent(app, ProtectionService::class.java)
                .setAction(ACTION_CAPTURE)
                .putExtra(EXTRA_KIND, type.name)
                .apply { failedAttempts?.let { putExtra(EXTRA_FAILED_ATTEMPTS, it) } }
            runCatching { ContextCompat.startForegroundService(app, intent) }
                .onFailure { error ->
                    declareFailure(
                        app,
                        type,
                        eventId = null,
                        failedAttempts,
                        "a background start of the camera service was refused: ${error.javaClass.simpleName}",
                    )
                }
        }

        /**
         * What happens when a capture could not run: the request is kept for a retry, the retry is
         * armed, and the owner is told in text — but only the first time this request is seen, so a
         * guard that keeps failing produces one message rather than a stream of them.
         */
        private fun declareFailure(
            context: Context,
            type: SecurityEventType,
            eventId: String?,
            failedAttempts: Int?,
            reason: String,
        ) {
            Log.e(TAG, "the $type capture could not run ($reason)")
            // The notice goes out now, so the queued request carries that fact with it: a later retry or
            // expiry must not tell the owner the same bad news a second time.
            val firstTime = requeue(context, type, eventId, failedAttempts, reason, announced = true)
            PendingCaptureReceiver.schedule(context)
            if (firstTime) announceMissingCapture(context, type, eventId, failedAttempts, reason)
        }

        /**
         * The owner's news that a guard fired and no photo or fix came. The alert says plainly that
         * nothing was captured, because a reported absence is honest while an absence nobody sees is the
         * defect this path exists to remove. Nothing here throws: this is the worst moment of the app's
         * life, and failing to report a failure must not cost the guards their last move.
         */
        private fun announceMissingCapture(
            context: Context,
            type: SecurityEventType,
            eventId: String?,
            failedAttempts: Int?,
            reason: String,
        ) {
            val base = runCatching { AlertRuntime.snapshot(context, alertKindFor(type), eventId) }.getOrNull()
            if (base == null) {
                Log.e(TAG, "the owner could not be told about the $type capture ($reason): no alert could be built")
                return
            }
            runCatching {
                AlertRuntime.alert(context, base.copy(evidenceAttempted = false, failedAttempts = failedAttempts))
            }.onFailure {
                Log.e(TAG, "the owner could not be told about the $type capture ($reason): ${it.message}", it)
            }
        }

        /** Writes [type] down as a capture still owed, or reports that it could not even do that. */
        private fun requeue(
            context: Context,
            type: SecurityEventType,
            eventId: String?,
            failedAttempts: Int?,
            reason: String,
            announced: Boolean = false,
        ): Boolean =
            runCatching { PendingCaptures(context).add(type, eventId, failedAttempts, announced) }
                .onFailure { Log.e(TAG, "the $type capture could not be queued either ($reason): ${it.message}", it) }
                .getOrDefault(false)

        /** Puts a request this drain just took back into the queue, with its own moment and notice. */
        private fun keepWaiting(context: Context, request: PendingCapture): Boolean =
            runCatching { PendingCaptures(context).put(request) }
                .onFailure { Log.e(TAG, "the ${request.type} capture could not be kept for another try: ${it.message}", it) }
                .getOrDefault(false)

        /**
         * Hands one queued request to whatever can produce its photo and reports whether anything took it.
         *
         * A live, armed instance runs it directly. Without one the request still deserves a try: the
         * service is asked to start, which a background caller may be refused — and a start that is
         * accepted carries the request into [onStartCommand], where a failed promotion queues it again.
         * So a true answer here always ends in a capture or in a queue, never in silence.
         */
        private fun honour(context: Context, request: PendingCapture): Boolean {
            val live = liveInstance
            if (live != null) {
                val eventId = request.eventId
                return if (eventId != null) live.captureForExisting(eventId, request.type)
                else {
                    live.capture(request.type, request.failedAttempts)
                    true
                }
            }
            val intent = Intent(context, ProtectionService::class.java)
                .setAction(ACTION_CAPTURE)
                .putExtra(EXTRA_KIND, request.type.name)
                .apply {
                    request.eventId?.let { putExtra(EXTRA_EVENT_ID, it) }
                    request.failedAttempts?.let { putExtra(EXTRA_FAILED_ATTEMPTS, it) }
                }
            return runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
        }

        /**
         * Runs the captures the queue is holding, from anywhere — the retry alarm, a service that just
         * promoted itself, and every heartbeat. A phone that is no longer armed empties the queue
         * instead of running it: once the owner switched protection off, nobody is to be photographed.
         *
         * A request that could not be honoured goes back with the moment it was made and the news that
         * was already sent about it, so retries expire on their own and one failed photo is reported
         * once rather than every thirty seconds.
         */
        fun drainPendingCaptures(context: Context) {
            val app = context.applicationContext
            val queue = PendingCaptures(app)
            if (!ProtectionSettingsStore(app).load().armed) {
                Log.i(TAG, "the captures still queued were dropped: protection is no longer armed")
                queue.clear()
                PendingCaptureReceiver.cancel(app)
                return
            }
            val drained = runCatching { queue.take() }.getOrElse {
                Log.e(TAG, "the capture queue could not be read: ${it.message}", it)
                return
            }
            if (drained.isEmpty) {
                // Nothing was waiting that could still be honoured: stop driving the retries.
                PendingCaptureReceiver.cancel(app)
                return
            }
            // A request that waited past its moment gets no photo. It does get the owner told, but only
            // if no earlier failure had already said it.
            drained.expired.forEach { stale ->
                Log.e(TAG, "the ${stale.type} capture waited too long to be worth running")
                if (PendingCaptureLogic.owesTheOwnerNotice(stale)) {
                    announceMissingCapture(
                        app,
                        stale.type,
                        stale.eventId,
                        stale.failedAttempts,
                        "it waited past the moment it asked for",
                    )
                }
            }
            drained.due.forEach { request ->
                if (honour(app, request)) return@forEach
                val notice = PendingCaptureLogic.owesTheOwnerNotice(request)
                val kept = keepWaiting(app, if (notice) PendingCaptureLogic.told(request) else request)
                if (kept) PendingCaptureReceiver.schedule(app)
                if (notice) {
                    announceMissingCapture(
                        app,
                        request.type,
                        request.eventId,
                        request.failedAttempts,
                        "no capture service could be reached to run it",
                    )
                }
            }
            if (drained.due.isEmpty()) PendingCaptureReceiver.cancel(app)
        }
    }
}
