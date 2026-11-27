package com.shieldra.protection

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import com.shieldra.delivery.DeliverySettingsStore
import com.shieldra.delivery.TelegramCommandClient
import kotlinx.coroutines.runBlocking

/**
 * Lets the owner control a stolen phone from their own Telegram chat (A-33 next stage). While
 * protection is armed and the owner turned this on, it long-polls the bot for commands and acts on
 * them — but only on messages from the owner's own chat id, so nobody else can drive the phone:
 *
 *  - `/locate` — reply with a live location pin.
 *  - `/track on` / `/track off` — start or stop a repeating location stream.
 *  - `/photo`  — take a front-camera photo and reply with it.
 *  - `/alarm` / `/stopalarm` — sound or silence the loud siren.
 *  - `/status` — reply with battery, network and armed state.
 *
 * It runs on one daemon thread and stops the moment protection is disarmed, the option is turned
 * off, or the plan no longer allows remote commands. Nothing is shown on the phone.
 */
class RemoteCommandPoller(context: Context) {
    private val app = context.applicationContext

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var trackWorker: Thread? = null

    @Synchronized
    fun start() {
        // P4-28: a finished thread is not a running one. `loop()` returns on its own in ordinary
        // cases — Telegram not set up yet, a disarm, the switch off, a lapsed plan — and a field that
        // kept naming that thread made every later start a no-op for the rest of the process. The
        // thread clears the field itself when it leaves, and this check covers the window between.
        if (worker?.isAlive == true) return
        val thread = Thread({
            try {
                loop()
            } finally {
                synchronized(this@RemoteCommandPoller) {
                    if (worker === Thread.currentThread()) worker = null
                }
            }
        }, "shieldra-remote").apply { isDaemon = true }
        worker = thread
        thread.start()
    }

    @Synchronized
    fun stop() {
        worker?.interrupt()
        worker = null
        stopTrack()
    }

    /**
     * Whether the loop may keep running. It asks the plan as well as the switch, because a tier is
     * not fixed for the life of this process: `PlayProStore` rewrites it whenever Google Play
     * answers, and nothing restarts the service when it does. Asking only armed + switch would let a
     * subscription that lapsed mid-run keep obeying `/photo`, `/alarm` and `/track` (P4-20).
     */
    private fun active(): Boolean = ProtectionSettingsStore(app).load().let {
        it.armed &&
            it.remoteCommandsEnabled &&
            com.shieldra.billing.TierGate.allowsRemote(it.tier)
    }

    private fun loop() {
        val initial = DeliverySettingsStore(app).load()
        if (!initial.telegramEnabled || !initial.telegram.isComplete) return
        var client = TelegramCommandClient(initial.telegram)
        var ownerChat = initial.telegram.chatId.trim()
        // P2-4: one command per update. Telegram redelivers an update whose offset it never saw
        // confirmed, and this poller re-reads its offset after a restart; without the guard the same
        // `/alarm` or `/track on` would run twice from one message.
        val replayGuard = RemoteCommandReplayGuard()
        // Skip the backlog so a command sent before arming is not acted on now.
        var offset = client.poll(0, timeoutSeconds = 0)?.lastOrNull()?.let { it.updateId + 1 } ?: 0L
        while (!Thread.currentThread().isInterrupted && active()) {
            // P4-30: the credentials are read again on every turn of the loop. A mistyped token or chat
            // id that the owner corrects in Alert channels is saved while this thread is alive, and
            // `start()` refuses to make a second thread (P4-28), so nothing else could ever pick it up:
            // the poller would keep talking to a bot that was never his and ignoring his own chat.
            val current = DeliverySettingsStore(app).load()
            if (!current.telegramEnabled || !current.telegram.isComplete) {
                Log.i(TAG, "the bot credentials were withdrawn while remote control ran: stopping")
                return
            }
            if (current.telegram != initial.telegram) {
                client = TelegramCommandClient(current.telegram)
                ownerChat = current.telegram.chatId.trim()
                // A different bot has its own update ids, so an offset from the old one means nothing.
                offset = client.poll(0, timeoutSeconds = 0)?.lastOrNull()?.let { it.updateId + 1 } ?: 0L
                Log.i(TAG, "the bot credentials changed: the poller is using the new ones")
            }
            val updates = client.poll(offset, timeoutSeconds = 25) ?: continue
            for (update in updates) {
                offset = update.updateId + 1
                if (update.chatId != ownerChat) continue
                if (!replayGuard.claim(update.updateId)) {
                    Log.i(TAG, "an update was already carried out and is not repeated: ${update.updateId}")
                    continue
                }
                runCatching { execute(client, ownerChat, update.text) }
                    .onFailure { Log.w(TAG, "command failed: ${it.message}") }
            }
        }
    }

    private fun execute(client: TelegramCommandClient, chat: String, raw: String) {
        // Strip a trailing @botname and any arguments; "/photo@MyBot" -> "/photo".
        val command = RemoteCommand.parse(raw)
        when (command.verb) {
            "/track" -> {
                if (command.argument == "off") {
                    stopTrack()
                    client.replyText(chat, "SHIELDRA: tracking off")
                } else {
                    startTrack(client, chat)
                    client.replyText(chat, "SHIELDRA: tracking on — send /track off to stop")
                }
            }
            "/locate" -> {
                val fix = runBlocking { LocationFix(app).current() }
                if (fix != null) client.replyLocation(chat, fix.latitude, fix.longitude, fix.accuracyMeters)
                else client.replyText(chat, "SHIELDRA: no location available")
            }
            "/photo" -> {
                val jpeg = runBlocking { CameraCapture(app).capture() }
                if (jpeg != null) client.replyPhoto(chat, jpeg, "SHIELDRA")
                else client.replyText(chat, "SHIELDRA: no photo could be taken")
            }
            "/alarm" -> {
                Siren.start(app, REMOTE_ALARM_MAX_MS)
                client.replyText(chat, "SHIELDRA: alarm on — send /stopalarm to silence")
            }
            "/stopalarm" -> {
                Siren.stop(app)
                client.replyText(chat, "SHIELDRA: alarm off")
            }
            "/status" -> client.replyText(chat, status())
            "/lost" -> {
                val settings = ProtectionSettingsStore(app).load()
                LostNotice.show(app, settings.ownerContact, settings.lostMessage)
                client.replyText(chat, "SHIELDRA: lost notice shown on the phone — send /found to clear")
            }
            "/found" -> {
                LostNotice.hide(app)
                client.replyText(chat, "SHIELDRA: lost notice cleared")
            }
            else -> Unit // unknown command: ignore silently
        }
    }

    /** `/track on` streams a location pin every tracking-interval minutes until `/track off`. */
    @Synchronized
    private fun startTrack(client: TelegramCommandClient, chat: String) {
        // P4-28: same shape as `start`. This loop ends on its own when the plan or the switch says
        // no, and a field that kept naming that thread made `/track on` a permanent no-op.
        if (trackWorker?.isAlive == true) return
        val thread = Thread({
            try {
                while (!Thread.currentThread().isInterrupted && active()) {
                    val fix = runBlocking { LocationFix(app).current() }
                    if (fix != null) client.replyLocation(chat, fix.latitude, fix.longitude, fix.accuracyMeters)
                    // P4-31: the gap is recomputed every turn, the way the service's own trail does it
                    // (`ProtectionService.scheduleTrackingTick`): the owner can change the interval
                    // while this stream runs, and the battery widens it as it drains so the trail keeps
                    // reporting instead of draining the last charge in a burst.
                    val baseMinutes = ProtectionSettingsStore(app).load().trackingIntervalMinutes
                    val intervalMs = TrackingCadence.intervalMs(
                        baseMinutes,
                        batteryPercent(),
                        batteryCharging(),
                    )
                    try {
                        Thread.sleep(intervalMs)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            } finally {
                synchronized(this@RemoteCommandPoller) {
                    if (trackWorker === Thread.currentThread()) trackWorker = null
                }
            }
        }, "shieldra-track").apply { isDaemon = true }
        trackWorker = thread
        thread.start()
    }

    @Synchronized
    private fun stopTrack() {
        trackWorker?.interrupt()
        trackWorker = null
    }

    private fun status(): String {
        val battery = batteryPercent()
        return buildString {
            append("SHIELDRA status\n")
            append("Armed: yes\n")
            battery?.let { append("Battery: ").append(it).append("%\n") }
            append("Network: ").append(network())
        }
    }

    /**
     * P4-31: the same battery read the service's own trail uses
     * (`ProtectionService.batteryPercent`), so both trails widen the gap identically as the phone
     * drains. Null when the platform cannot answer, which `TrackingCadence` treats as the base gap.
     */
    private fun batteryPercent(): Int? = runCatching {
        app.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrNull()?.takeIf { it in 0..100 }

    private fun batteryCharging(): Boolean = runCatching {
        app.getSystemService(BatteryManager::class.java)?.isCharging
    }.getOrNull() ?: false

    private fun network(): String {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            else -> "other"
        }
    }

    private companion object {
        const val TAG = "RemoteCommandPoller"
        /** Cap for the owner-initiated `/alarm` siren so a leaked token cannot loop it forever. */
        const val REMOTE_ALARM_MAX_MS = 120_000L
    }
}
