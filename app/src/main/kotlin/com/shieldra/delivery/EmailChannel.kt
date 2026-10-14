package com.shieldra.delivery

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** How the mail server wants TLS: from the first byte (465) or upgraded after EHLO (587). */
enum class MailSecurity { SSL_TLS, STARTTLS }

/**
 * Owner-supplied mail settings: an account's address and an **app password** (never the account's
 * real password; Gmail, Outlook and Yahoo all issue them). The password is stored encrypted.
 */
data class EmailConfig(
    val host: String,
    val port: Int,
    val security: MailSecurity,
    val username: String,
    val appPassword: String,
    val from: String,
    val to: String,
) {
    val isComplete: Boolean
        get() = host.isNotBlank() && port in 1..65535 && username.isNotBlank() &&
            appPassword.isNotBlank() && ADDRESS.matches(from) && ADDRESS.matches(to)

    private companion object {
        val ADDRESS = Regex("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+")
    }
}

/** The line-level seam an SMTP conversation runs over, so the dialogue is testable without a server. */
interface MailTransport : AutoCloseable {
    fun readLine(): String?

    fun writeLine(line: String)

    /** Replaces the plain connection with TLS (STARTTLS) and verifies the server's name. */
    fun upgradeToTls()
}

fun interface MailTransportFactory {
    fun open(config: EmailConfig): MailTransport
}

/**
 * Sends an alert by email over the account's own SMTP server: AUTH LOGIN with the app password,
 * one plain-text UTF-8 message, base64 bodied so no line can be mistaken for the end of DATA.
 * Nothing goes out unencrypted: the conversation is TLS from the first byte, or it is upgraded with
 * STARTTLS before the password is sent, and a server that refuses STARTTLS is refused in turn.
 */
class EmailChannel(
    private val config: EmailConfig,
    private val transports: MailTransportFactory = SocketMailTransportFactory(),
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) : AlertChannel {
    override val channel: DeliveryChannel = DeliveryChannel.EMAIL

    override fun send(subject: String, body: String, extras: AlertExtras): ChannelResult {
        if (!config.isComplete) return ChannelResult.Rejected("Email is not fully set up")
        val attachments = buildList {
            extras.photoJpeg?.let { add(Attachment("shieldra.jpg", "image/jpeg", it)) }
            extras.photoBackJpeg?.let { add(Attachment("shieldra-back.jpg", "image/jpeg", it)) }
            extras.audioBytes?.let { add(Attachment("shieldra.m4a", extras.audioMediaType, it)) }
        }
        return try {
            transports.open(config).use { transport -> converse(transport, subject, body, attachments) }
        } catch (error: MailRefused) {
            error.result
        } catch (error: IOException) {
            ChannelResult.Retryable("network: ${error.javaClass.simpleName}")
        }
    }

    private class Attachment(val fileName: String, val mediaType: String, val bytes: ByteArray)

    private fun converse(transport: MailTransport, subject: String, body: String, attachments: List<Attachment>): ChannelResult {
        expect(transport, 220)
        hello(transport)
        if (config.security == MailSecurity.STARTTLS) {
            command(transport, "STARTTLS", 220)
            transport.upgradeToTls()
            hello(transport)
        }
        command(transport, "AUTH LOGIN", 334)
        command(transport, base64(config.username), 334)
        command(transport, base64(config.appPassword), 235, rejectedOnFailure = true)
        command(transport, "MAIL FROM:<${config.from}>", 250, rejectedOnFailure = true)
        command(transport, "RCPT TO:<${config.to}>", 250, 251, rejectedOnFailure = true)
        command(transport, "DATA", 354)
        message(subject, body, attachments).forEach(transport::writeLine)
        command(transport, ".", 250)
        runCatching { command(transport, "QUIT", 221) }
        return ChannelResult.Sent
    }

    private fun hello(transport: MailTransport) {
        command(transport, "EHLO shieldra.local", 250)
    }

    private fun message(subject: String, body: String, attachments: List<Attachment>): List<String> {
        val date = now().format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val encoder = Base64.getMimeEncoder(BODY_LINE_LENGTH, "\r\n".toByteArray())
        val encodedBody = encoder.encodeToString(body.toByteArray(StandardCharsets.UTF_8))
        val headers = listOf(
            "From: <${config.from}>",
            "To: <${config.to}>",
            "Subject: =?UTF-8?B?${base64(subject)}?=",
            "Date: $date",
            "MIME-Version: 1.0",
        )
        if (attachments.isEmpty()) {
            return headers + listOf(
                "Content-Type: text/plain; charset=UTF-8",
                "Content-Transfer-Encoding: base64",
                "",
            ) + encodedBody.split("\r\n")
        }
        // Attachments turn the message into multipart/mixed: the text part, then each file. No
        // boundary can clash with base64 output, which never contains the "--" marker sequence.
        val boundary = "shieldra_${now().toInstant().toEpochMilli()}_part"
        val lines = headers.toMutableList()
        lines += "Content-Type: multipart/mixed; boundary=\"$boundary\""
        lines += ""
        lines += "--$boundary"
        lines += "Content-Type: text/plain; charset=UTF-8"
        lines += "Content-Transfer-Encoding: base64"
        lines += ""
        lines += encodedBody.split("\r\n")
        for (attachment in attachments) {
            lines += "--$boundary"
            lines += "Content-Type: ${attachment.mediaType}"
            lines += "Content-Transfer-Encoding: base64"
            lines += "Content-Disposition: attachment; filename=\"${attachment.fileName}\""
            lines += ""
            lines += encoder.encodeToString(attachment.bytes).split("\r\n")
        }
        lines += "--$boundary--"
        return lines
    }

    private fun command(
        transport: MailTransport,
        line: String,
        vararg accepted: Int,
        rejectedOnFailure: Boolean = false,
    ) {
        transport.writeLine(line)
        expect(transport, *accepted, rejectedOnFailure = rejectedOnFailure)
    }

    /** Reads one reply, multi-line included (`250-…` continues, `250 …` ends). */
    private fun expect(transport: MailTransport, vararg accepted: Int, rejectedOnFailure: Boolean = false) {
        var line: String
        do {
            line = transport.readLine() ?: throw IOException("the mail server closed the connection")
        } while (line.length > 3 && line[3] == '-')
        val code = line.take(3).toIntOrNull() ?: throw IOException("unreadable mail server reply")
        if (code in accepted) return
        // 5xx is a permanent answer (bad password, refused sender or recipient); 4xx is temporary.
        val reason = "mail server answered $code"
        throw MailRefused(
            if (code in 500..599 || rejectedOnFailure && code !in 400..499) {
                ChannelResult.Rejected(reason)
            } else {
                ChannelResult.Retryable(reason)
            },
        )
    }

    private fun base64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private class MailRefused(val result: ChannelResult) : Exception()

    private companion object {
        const val BODY_LINE_LENGTH = 76
    }
}

/** The real transport: a TLS socket (465) or a plain one upgraded by STARTTLS (587). */
class SocketMailTransportFactory(
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : MailTransportFactory {
    override fun open(config: EmailConfig): MailTransport = SocketMailTransport(config, timeoutMs)

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000
    }
}

private class SocketMailTransport(private val config: EmailConfig, timeoutMs: Int) : MailTransport {
    private var socket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: OutputStream

    init {
        val plain = Socket()
        plain.connect(InetSocketAddress(config.host, config.port), timeoutMs)
        plain.soTimeout = timeoutMs
        socket = if (config.security == MailSecurity.SSL_TLS) secure(plain) else plain
        bindStreams()
    }

    override fun readLine(): String? = reader.readLine()

    override fun writeLine(line: String) {
        writer.write("$line\r\n".toByteArray(StandardCharsets.UTF_8))
        writer.flush()
    }

    override fun upgradeToTls() {
        socket = secure(socket)
        bindStreams()
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private fun bindStreams() {
        reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        writer = socket.getOutputStream()
    }

    /** TLS over an existing connection, then the certificate is checked against the host name. */
    private fun secure(base: Socket): Socket {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val tls = factory.createSocket(base, config.host, config.port, true) as SSLSocket
        tls.startHandshake()
        if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(config.host, tls.session)) {
            tls.close()
            throw IOException("the mail server's certificate does not match ${config.host}")
        }
        return tls
    }
}
