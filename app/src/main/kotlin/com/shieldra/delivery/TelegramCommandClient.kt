package com.shieldra.delivery

import org.json.JSONObject
import java.io.IOException

/** One command message the owner sent to the bot: which update it was, which chat, and its text. */
data class TelegramUpdate(val updateId: Long, val chatId: String, val text: String)

/**
 * The Telegram Bot API calls behind remote commands (A-33 next stage). All `api.telegram.org`
 * traffic lives here in the delivery package. It long-polls `getUpdates` for the owner's commands
 * and replies with text, a live location, or a photo. It accepts nothing on its own — the caller
 * checks that an update really came from the owner's own chat before acting on it.
 */
class TelegramCommandClient(
    private val config: TelegramConfig,
    // 35 s read timeout so Telegram's 25 s long-poll can complete before the socket times out.
    private val poster: HttpsFormPoster = UrlConnectionFormPoster(timeoutMs = 35_000),
    private val photoPoster: HttpsMultipartPoster = UrlConnectionFormPoster(),
) {
    private val api: String get() = "https://api.telegram.org/bot${config.botToken}"

    /**
     * Long-polls for updates after [offset]. Returns the parsed command messages, or null on a
     * network error (the caller simply tries again). [timeoutSeconds] is Telegram's own long-poll
     * wait, so an idle poll costs one request every [timeoutSeconds], not a busy loop.
     */
    fun poll(offset: Long, timeoutSeconds: Int = 25): List<TelegramUpdate>? {
        val reply = try {
            poster.post(
                "$api/getUpdates",
                mapOf(
                    "offset" to offset.toString(),
                    "timeout" to timeoutSeconds.toString(),
                    "allowed_updates" to "[\"message\"]",
                ),
            )
        } catch (error: IOException) {
            return null
        }
        if (reply.code != 200) return emptyList()
        return parse(reply.body)
    }

    private fun parse(body: String): List<TelegramUpdate> = runCatching {
        val root = JSONObject(body)
        if (!root.optBoolean("ok")) return emptyList()
        val results = root.optJSONArray("result") ?: return emptyList()
        buildList {
            for (i in 0 until results.length()) {
                val update = results.optJSONObject(i) ?: continue
                val message = update.optJSONObject("message") ?: continue
                val chat = message.optJSONObject("chat") ?: continue
                val text = message.optString("text", "")
                if (text.isNotBlank()) {
                    add(TelegramUpdate(update.optLong("update_id"), chat.opt("id").toString(), text.trim()))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun replyText(chatId: String, text: String) {
        runCatching { poster.post("$api/sendMessage", mapOf("chat_id" to chatId, "text" to text)) }
    }

    fun replyLocation(chatId: String, latitude: Double, longitude: Double, accuracyMeters: Float?) {
        runCatching {
            poster.post(
                "$api/sendLocation",
                mapOf("chat_id" to chatId, "latitude" to latitude.toString(), "longitude" to longitude.toString()) +
                    (accuracyMeters?.let { mapOf("horizontal_accuracy" to it.coerceIn(0f, 1500f).toString()) } ?: emptyMap()),
            )
        }
    }

    fun replyPhoto(chatId: String, jpeg: ByteArray, caption: String) {
        runCatching {
            photoPoster.postFile(
                "$api/sendPhoto",
                mapOf("chat_id" to chatId, "caption" to caption),
                MultipartFile("photo", "shieldra.jpg", "image/jpeg", jpeg),
            )
        }
    }
}
