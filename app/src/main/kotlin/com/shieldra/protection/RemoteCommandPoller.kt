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
 * It runs on one daemon thread and stops the moment protection is disarmed or the option is turned
 * off. Nothing is shown on the phone.
 */
class RemoteCommandPoller(context: Context) {
    private val app = context.applicationContext

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var trackWorker: Thread? = null

    @Synchronized
    fun start() {
        if (worker != null) return
        worker = Thread({ loop() }, "shieldra-remote").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        worker?.interrupt()
        worker = null
        stopTrack()
    }

    private fun active(): Boolean = ProtectionSettingsStore(app).load().let { it.armed && it.remoteCommandsEnabled }

    private fun loop() {
        val delivery = DeliverySettingsStore(app).load()
        if (!delivery.telegramEnabled || !delivery.telegram.isComplete) return
        val client = TelegramCommandClient(delivery.telegram)
        val ownerChat = delivery.telegram.chatId.trim()
        // Skip the backlog so a command sent before arming is not acted on now.
        var offset = client.poll(0, timeoutSeconds = 0)?.lastOrNull()?.let { it.updateId + 1 } ?: 0L
        while (!Thread.currentThread().isInterrupted && active()) {
            val updates = client.poll(offset, timeoutSeconds = 25) ?: continue
            for (update in updates) {
                offset = update.updateId + 1
                if (update.chatId != ownerChat) continue
                runCatching { execute(client, ownerChat, update.text) }
                    .onFailure { Log.w(TAG, "command failed: ${it.message}") }
            }
        }
    }

    private fun execute(client: TelegramCommandClient, chat: String, raw: String) {
        // Strip a trailing @botname and any arguments; "/photo@MyBot" -> "/photo".
        val command = raw.substringBefore(' ').substringBefore('@').lowercase()
        when (command) {
            "/track" -> {
                if (raw.substringAfter(' ', "").trim().lowercase() == "off") {
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
                Siren.start(app)
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
        if (trackWorker != null) return
        trackWorker = Thread({
            val intervalMs = ProtectionSettingsStore(app).load().trackingIntervalMinutes.coerceAtLeast(1) * 60_000L
            while (!Thread.currentThread().isInterrupted && active()) {
                val fix = runBlocking { LocationFix(app).current() }
                if (fix != null) client.replyLocation(chat, fix.latitude, fix.longitude, fix.accuracyMeters)
                try {
                    Thread.sleep(intervalMs)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }, "shieldra-track").apply { isDaemon = true; start() }
    }

    @Synchronized
    private fun stopTrack() {
        trackWorker?.interrupt()
        trackWorker = null
    }

    private fun status(): String {
        val battery = runCatching {
            app.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrNull()
        return buildString {
            append("SHIELDRA status\n")
            append("Armed: yes\n")
            battery?.let { append("Battery: ").append(it).append("%\n") }
            append("Network: ").append(network())
        }
    }

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
    }
}
