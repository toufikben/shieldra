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

    fun send(subject: String, body: String): ChannelResult
}

/** The HTTPS seam, so the request a channel builds is testable without a network. */
fun interface HttpsFormPoster {
    /** Posts [fields] form-encoded to [url]; returns the status code and body, or throws [IOException]. */
    fun post(url: String, fields: Map<String, String>): HttpReply
}

data class HttpReply(val code: Int, val body: String)

/** The real poster: HTTPS only, bounded timeouts, nothing logged. */
class UrlConnectionFormPoster(
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : HttpsFormPoster {
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
 * Sends an alert as a Telegram message through the owner's own bot (Bot API `sendMessage`).
 * Telegram answers `{"ok":true,...}` only when the message was accepted, and that answer is the
 * only thing that counts as sent.
 */
class TelegramChannel(
    private val config: TelegramConfig,
    private val poster: HttpsFormPoster = UrlConnectionFormPoster(),
) : AlertChannel {
    override val channel: DeliveryChannel = DeliveryChannel.TELEGRAM

    override fun send(subject: String, body: String): ChannelResult {
        if (!config.isComplete) return ChannelResult.Rejected("Telegram is not fully set up")
        val reply = try {
            poster.post(
                "https://api.telegram.org/bot${config.botToken}/sendMessage",
                mapOf("chat_id" to config.chatId, "text" to body),
            )
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
    }
}
