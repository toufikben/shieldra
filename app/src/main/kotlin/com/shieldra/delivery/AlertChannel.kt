package com.shieldra.delivery

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/** What one send really did. A channel never reports success it did not get from the other end. */
sealed interface ChannelResult {
    data object Sent : ChannelResult

    /** Worth trying again later: no network, a timeout, or a server-side hiccup. */
    data class Retryable(val reason: String) : ChannelResult

    /** Trying again cannot help: wrong token, wrong password, refused recipient. */
    data class Rejected(val reason: String) : ChannelResult
}

/** One configured way of getting an alert to the owner. */
interface AlertChannel {
    val channel: DeliveryChannel

    /** [extras] carries the photo and fix when there are any; a channel that cannot use them ignores them. */
    fun send(subject: String, body: String, extras: AlertExtras = AlertExtras.NONE): ChannelResult
}

/** The HTTPS seam, so the request a channel builds is testable without a network. */
fun interface HttpsFormPoster {
    /** Posts [fields] form-encoded to [url]; returns the status code and body, or throws [IOException]. */
    fun post(url: String, fields: Map<String, String>): HttpReply
}

data class HttpReply(val code: Int, val body: String)

/** The multipart seam for a file upload (Telegram `sendPhoto`), testable without a network. */
fun interface HttpsMultipartPoster {
    fun postFile(url: String, fields: Map<String, String>, file: MultipartFile): HttpReply
}

/** One file part: the form field it fills, its name, its media type and its bytes. */
class MultipartFile(val field: String, val fileName: String, val mediaType: String, val bytes: ByteArray)

/** The real poster: HTTPS only, bounded timeouts, nothing logged. */
class UrlConnectionFormPoster(
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : HttpsFormPoster, HttpsMultipartPoster {
    override fun post(url: String, fields: Map<String, String>): HttpReply {
        require(url.startsWith("https://")) { "alerts are only sent over HTTPS" }
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            val payload = fields.entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }.toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpReply(code, body)
        } finally {
            connection.disconnect()
        }
    }

    override fun postFile(url: String, fields: Map<String, String>, file: MultipartFile): HttpReply {
        require(url.startsWith("https://")) { "alerts are only sent over HTTPS" }
        val boundary = "shieldra" + System.nanoTime().toString(16)
        val dash = "--"
        val crlf = "\r\n"
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMs
            // A photo over a weak mobile link takes longer than a line of text.
            connection.readTimeout = timeoutMs * 2
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.use { out ->
                val head = buildString {
                    fields.forEach { (key, value) ->
                        append(dash).append(boundary).append(crlf)
                        append("Content-Disposition: form-data; name=\"").append(key).append('"').append(crlf).append(crlf)
                        append(value).append(crlf)
                    }
                    append(dash).append(boundary).append(crlf)
                    append("Content-Disposition: form-data; name=\"").append(file.field)
                    append("\"; filename=\"").append(file.fileName).append('"').append(crlf)
                    append("Content-Type: ").append(file.mediaType).append(crlf).append(crlf)
                }
                out.write(head.toByteArray(StandardCharsets.UTF_8))
                out.write(file.bytes)
                out.write("$crlf$dash$boundary$dash$crlf".toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpReply(code, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000
    }
}

/** Owner-supplied Telegram bot settings. The token is a secret and is stored encrypted. */
data class TelegramConfig(val botToken: String, val chatId: String) {
    val isComplete: Boolean get() = TOKEN.matches(botToken) && CHAT_ID.matches(chatId)

    private companion object {
        val TOKEN = Regex("[0-9]{5,}:[A-Za-z0-9_-]{30,}")
        val CHAT_ID = Regex("-?[0-9]{3,}|@[A-Za-z0-9_]{5,}")
    }
}

/**
 * Sends an alert through the owner's own bot. With a photo it is one `sendPhoto` whose caption is
 * the text; without one, a `sendMessage`. A fix is followed by a `sendLocation`, so the chat shows
 * a real map pin. Telegram answers `{"ok":true,...}` only when the message was accepted, and that
 * answer — for the text or photo, which carries the facts — is the only thing that counts as sent;
 * the pin is a convenience on top and never changes the result.
 */
class TelegramChannel(
    private val config: TelegramConfig,
    private val photoPoster: HttpsMultipartPoster = UrlConnectionFormPoster(),
    private val poster: HttpsFormPoster = UrlConnectionFormPoster(),
) : AlertChannel {
    override val channel: DeliveryChannel = DeliveryChannel.TELEGRAM

    override fun send(subject: String, body: String, extras: AlertExtras): ChannelResult {
        if (!config.isComplete) return ChannelResult.Rejected("Telegram is not fully set up")
        val photo = extras.photoJpeg
        var result: ChannelResult = if (photo != null && body.length <= CAPTION_LIMIT) {
            call {
                photoPoster.postFile(
                    "$api/sendPhoto",
                    mapOf("chat_id" to config.chatId, "caption" to body),
                    MultipartFile("photo", "shieldra.jpg", "image/jpeg", photo),
                )
            }
        } else {
            // A photo Telegram refused, or a caption too long for one, must not cost the owner the
            // facts: send the text, then try the photo as a follow-up that cannot fail the alert.
            val text = call { poster.post("$api/sendMessage", mapOf("chat_id" to config.chatId, "text" to body)) }
            if (text is ChannelResult.Sent && photo != null) {
                call {
                    photoPoster.postFile(
                        "$api/sendPhoto",
                        mapOf("chat_id" to config.chatId),
                        MultipartFile("photo", "shieldra.jpg", "image/jpeg", photo),
                    )
                }
            }
            text
        }
        if (result is ChannelResult.Rejected && photo != null) {
            // sendPhoto was refused (image too large, unreadable): fall back to the plain message.
            result = call { poster.post("$api/sendMessage", mapOf("chat_id" to config.chatId, "text" to body)) }
        }
        val location = extras.location
        if (result is ChannelResult.Sent && location != null) {
            call {
                poster.post(
                    "$api/sendLocation",
                    mapOf(
                        "chat_id" to config.chatId,
                        "latitude" to location.latitude.toString(),
                        "longitude" to location.longitude.toString(),
                    ) + (location.accuracyMeters?.let { mapOf("horizontal_accuracy" to it.coerceIn(0f, 1500f).toString()) } ?: emptyMap()),
                )
            }
        }
        return result
    }

    private val api: String get() = "https://api.telegram.org/bot${config.botToken}"

    private fun call(request: () -> HttpReply): ChannelResult {
        val reply = try {
            request()
        } catch (error: IOException) {
            return ChannelResult.Retryable("network: ${error.javaClass.simpleName}")
        }
        val accepted = reply.code == HttpURLConnection.HTTP_OK && OK_TRUE.containsMatchIn(reply.body)
        return when {
            accepted -> ChannelResult.Sent
            // 401 bad token, 400 bad chat, 403 the bot was blocked: retrying cannot change the answer.
            reply.code in 400..499 && reply.code != TOO_MANY_REQUESTS ->
                ChannelResult.Rejected("Telegram refused the message (HTTP ${reply.code})")
            else -> ChannelResult.Retryable("Telegram did not accept the message (HTTP ${reply.code})")
        }
    }

    private companion object {
        val OK_TRUE = Regex("\"ok\"\\s*:\\s*true")
        const val TOO_MANY_REQUESTS = 429
        /** Telegram's caption limit; a longer text goes as a message with the photo after it. */
        const val CAPTION_LIMIT = 1024
    }
}
