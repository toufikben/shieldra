package com.shieldra.delivery

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** A-32: what the Telegram channel sends, and which answers count as sent. */
class TelegramChannelTest {
    private val config = TelegramConfig(botToken = "123456789:${"A".repeat(35)}", chatId = "987654321")
    private val API = "https://api.telegram.org/bot${config.botToken}"

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
    fun a_photo_goes_as_sendphoto_with_the_text_as_caption_and_a_fix_adds_a_pin() {
        val calls = mutableListOf<String>()
        val channel = TelegramChannel(
            config,
            poster = { url, _ -> calls += url; HttpReply(200, """{"ok":true}""") },
            photoPoster = { url, fields, file ->
                calls += url
                assertEquals("the body", fields["caption"])
                assertEquals("image/jpeg", file.mediaType)
                HttpReply(200, """{"ok":true}""")
            },
        )

        val result = channel.send(
            "s",
            "the body",
            AlertExtras(
                photoJpeg = byteArrayOf(1, 2, 3),
                location = AlertLocation(36.75, 3.06, 12f, java.time.Instant.EPOCH, "fused"),
            ),
        )

        assertIs<ChannelResult.Sent>(result)
        assertEquals(
            listOf("$API/sendPhoto", "$API/sendLocation"),
            calls,
        )
    }

    @Test
    fun a_rejected_photo_falls_back_to_a_plain_message_so_the_owner_still_gets_the_facts() {
        val calls = mutableListOf<String>()
        val channel = TelegramChannel(
            config,
            poster = { url, _ -> calls += url; HttpReply(200, """{"ok":true}""") },
            photoPoster = { url, _, _ -> calls += url; HttpReply(400, "photo too big") },
        )

        assertIs<ChannelResult.Sent>(channel.send("s", "b", AlertExtras(photoJpeg = byteArrayOf(9))))
        assertEquals(listOf("$API/sendPhoto", "$API/sendMessage"), calls)
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
