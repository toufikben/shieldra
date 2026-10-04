package com.shieldra.delivery

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A-32: the SMTP dialogue, checked line by line against a scripted server. */
class EmailChannelTest {
    private val config = EmailConfig(
        host = "smtp.example.com",
        port = 465,
        security = MailSecurity.SSL_TLS,
        username = "owner@example.com",
        appPassword = "abcdabcdabcdabcd",
        from = "owner@example.com",
        to = "trusted@example.com",
    )

    @Test
    fun a_complete_dialogue_authenticates_with_the_app_password_and_sends_one_message() {
        val server = ScriptedServer(
            "220 ready",
            "250-smtp.example.com", "250 AUTH LOGIN",
            "334 VXNlcm5hbWU6", "334 UGFzc3dvcmQ6", "235 accepted",
            "250 ok", "250 ok", "354 go", "250 queued", "221 bye",
        )

        val result = channel(server).send("SHIELDRA — Test", "body ✓ عربي")

        assertIs<ChannelResult.Sent>(result)
        assertEquals("EHLO shieldra.local", server.written[0])
        assertEquals("AUTH LOGIN", server.written[1])
        assertEquals(base64("owner@example.com"), server.written[2])
        assertEquals(base64("abcdabcdabcdabcd"), server.written[3])
        assertEquals("MAIL FROM:<owner@example.com>", server.written[4])
        assertEquals("RCPT TO:<trusted@example.com>", server.written[5])
        assertEquals("DATA", server.written[6])
        assertTrue(server.written.contains("Subject: =?UTF-8?B?${base64("SHIELDRA — Test")}?="))
        assertTrue(server.written.contains("Content-Transfer-Encoding: base64"))
        assertTrue("." in server.written, "DATA must end with a lone dot")
        assertFalse(server.upgraded, "port 465 is TLS from the first byte; no STARTTLS")
        assertTrue(server.closed)
    }

    @Test
    fun the_body_is_base64_so_no_body_line_can_end_the_message_early() {
        val server = ScriptedServer(
            "220 ready", "250 ok", "334 u", "334 p", "235 ok", "250 ok", "250 ok", "354 go", "250 ok", "221 bye",
        )

        channel(server).send("s", ".\r\n.")

        val bodyStart = server.written.indexOf("") + 1
        val bodyLines = server.written.subList(bodyStart, server.written.lastIndexOf("."))
        val decoded = String(Base64.getMimeDecoder().decode(bodyLines.joinToString("\r\n")), StandardCharsets.UTF_8)
        assertEquals(".\r\n.", decoded)
    }

    @Test
    fun starttls_upgrades_before_any_credential_is_sent() {
        val server = ScriptedServer(
            "220 ready", "250 ok", "220 go ahead", "250 ok",
            "334 u", "334 p", "235 ok", "250 ok", "250 ok", "354 go", "250 ok", "221 bye",
        )

        val result = channel(server, config.copy(port = 587, security = MailSecurity.STARTTLS)).send("s", "b")

        assertIs<ChannelResult.Sent>(result)
        assertEquals(listOf("EHLO shieldra.local", "STARTTLS", "EHLO shieldra.local", "AUTH LOGIN"), server.written.take(4))
        assertEquals(2, server.upgradeIndex, "the TLS upgrade happens right after STARTTLS is accepted")
    }

    @Test
    fun a_wrong_app_password_is_rejected_and_not_retried() {
        val server = ScriptedServer("220 ready", "250 ok", "334 u", "334 p", "535 bad credentials")

        val result = channel(server).send("s", "b")

        assertIs<ChannelResult.Rejected>(result)
        assertFalse(server.written.any { it.startsWith("MAIL FROM") }, "nothing is sent after a refused login")
    }

    @Test
    fun a_temporary_server_answer_is_retryable() {
        val server = ScriptedServer("421 try later")

        assertIs<ChannelResult.Retryable>(channel(server).send("s", "b"))
    }

    @Test
    fun no_connection_is_retryable_and_an_incomplete_setup_is_refused_without_connecting() {
        val failing = EmailChannel(config, transports = { throw IOException("offline") })
        assertIs<ChannelResult.Retryable>(failing.send("s", "b"))

        var opened = false
        val incomplete = EmailChannel(config.copy(appPassword = ""), transports = { opened = true; ScriptedServer() })
        assertIs<ChannelResult.Rejected>(incomplete.send("s", "b"))
        assertFalse(opened)
    }

    private fun channel(server: ScriptedServer, mail: EmailConfig = config) = EmailChannel(
        mail,
        transports = { server },
        now = { ZonedDateTime.of(2026, 10, 4, 12, 0, 0, 0, ZoneOffset.UTC) },
    )

    private fun base64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private class ScriptedServer(vararg replies: String) : MailTransport {
        private val replies = ArrayDeque(replies.toList())
        val written = mutableListOf<String>()
        var upgraded = false
        var upgradeIndex = -1
        var closed = false

        override fun readLine(): String? = replies.removeFirstOrNull()

        override fun writeLine(line: String) {
            written += line
        }

        override fun upgradeToTls() {
            upgraded = true
            upgradeIndex = written.size
        }

        override fun close() {
            closed = true
        }
    }
}
