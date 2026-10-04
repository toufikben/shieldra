package com.shieldra.delivery

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** A-32: what the Telegram channel sends, and which answers count as sent. */
class TelegramChannelTest {
    private val config = TelegramConfig(botToken = "123456789:${"A".repeat(35)}", chatId = "987654321")

    @Test
    fun it_posts_the_body_to_the_owner_s_own_bot_and_chat() {
        var url = ""
        var fields = emptyMap<String, String>()
        val channel = TelegramChannel(config) { u, f ->
            url = u
            fields = f
            HttpReply(200, """{"ok":true,"result":{}}""")
        }

        val result = channel.send("subject", "the body")

        assertIs<ChannelResult.Sent>(result)
        assertEquals("https://api.telegram.org/bot${config.botToken}/sendMessage", url)
        assertEquals(mapOf("chat_id" to "987654321", "text" to "the body"), fields)
    }

    @Test
    fun only_ok_true_counts_as_sent() {
        val channel = TelegramChannel(config) { _, _ -> HttpReply(200, """{"ok":false}""") }

        assertIs<ChannelResult.Retryable>(channel.send("s", "b"))
    }

    @Test
    fun a_wrong_token_or_chat_is_rejected_and_rate_limiting_is_retryable() {
        assertIs<ChannelResult.Rejected>(TelegramChannel(config) { _, _ -> HttpReply(401, "") }.send("s", "b"))
        assertIs<ChannelResult.Rejected>(TelegramChannel(config) { _, _ -> HttpReply(400, "") }.send("s", "b"))
        assertIs<ChannelResult.Retryable>(TelegramChannel(config) { _, _ -> HttpReply(429, "") }.send("s", "b"))
        assertIs<ChannelResult.Retryable>(TelegramChannel(config) { _, _ -> HttpReply(502, "") }.send("s", "b"))
    }

    @Test
    fun no_network_is_retryable_and_an_incomplete_setup_never_touches_the_network() {
        assertIs<ChannelResult.Retryable>(
            TelegramChannel(config) { _, _ -> throw IOException("offline") }.send("s", "b"),
        )
        var called = false
        val incomplete = TelegramChannel(TelegramConfig("not-a-token", "1")) { _, _ ->
            called = true
            HttpReply(200, """{"ok":true}""")
        }
        assertIs<ChannelResult.Rejected>(incomplete.send("s", "b"))
        assertFalse(called)
    }
}
