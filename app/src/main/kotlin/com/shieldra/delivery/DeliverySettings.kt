package com.shieldra.delivery

import android.content.Context
import android.content.SharedPreferences
import com.shieldra.storage.AndroidKeystoreEncryptor
import com.shieldra.storage.EncryptedPayload
import com.shieldra.storage.EvidenceKeyPolicy
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Where the dispatcher gets the channels to use; [DeliverySettings] in the app, fakes in tests. */
interface ChannelSource {
    fun activeChannels(): List<AlertChannel>
}

/** Everything the owner configured for sending alerts. A channel left off is simply absent. */
data class DeliverySettings(
    val telegramEnabled: Boolean = false,
    val telegram: TelegramConfig = TelegramConfig(botToken = "", chatId = ""),
    val emailEnabled: Boolean = false,
    val email: EmailConfig = EmailConfig(
        host = DEFAULT_MAIL_HOST,
        port = DEFAULT_MAIL_PORT,
        security = MailSecurity.SSL_TLS,
        username = "",
        appPassword = "",
        from = "",
        to = "",
    ),
) : ChannelSource {
    /** The channels that are switched on and fully filled in; only these are ever used. */
    override fun activeChannels(): List<AlertChannel> = buildList {
        if (telegramEnabled && telegram.isComplete) add(TelegramChannel(telegram))
        if (emailEnabled && email.isComplete) add(EmailChannel(email))
    }

    companion object {
        const val DEFAULT_MAIL_HOST = "smtp.gmail.com"
        const val DEFAULT_MAIL_PORT = 465
    }
}

/**
 * Keeps [DeliverySettings] in app-private preferences, which the backup rules already exclude.
 * The two secrets — the bot token and the mail app password — are sealed with AES-256-GCM under a
 * dedicated Android Keystore key, bound by AAD to the field they belong to, so neither is ever
 * written in plain text and one cannot be swapped into the other's place. A secret that can no
 * longer be opened reads as empty, which leaves that channel "not set up" instead of guessing.
 */
class DeliverySettingsStore(
    context: Context,
    private val encryptor: AndroidKeystoreEncryptor = AndroidKeystoreEncryptor(
        applicationContext = context.applicationContext,
    ),
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): DeliverySettings = DeliverySettings(
        telegramEnabled = prefs.getBoolean(KEY_TELEGRAM_ENABLED, false),
        telegram = TelegramConfig(
            botToken = readSecret(KEY_TELEGRAM_TOKEN),
            chatId = prefs.getString(KEY_TELEGRAM_CHAT, "").orEmpty(),
        ),
        emailEnabled = prefs.getBoolean(KEY_EMAIL_ENABLED, false),
        email = EmailConfig(
            host = prefs.getString(KEY_EMAIL_HOST, DeliverySettings.DEFAULT_MAIL_HOST).orEmpty(),
            port = prefs.getInt(KEY_EMAIL_PORT, DeliverySettings.DEFAULT_MAIL_PORT),
            security = runCatching {
                MailSecurity.valueOf(prefs.getString(KEY_EMAIL_SECURITY, MailSecurity.SSL_TLS.name).orEmpty())
            }.getOrDefault(MailSecurity.SSL_TLS),
            username = prefs.getString(KEY_EMAIL_USER, "").orEmpty(),
            appPassword = readSecret(KEY_EMAIL_PASSWORD),
            from = prefs.getString(KEY_EMAIL_FROM, "").orEmpty(),
            to = prefs.getString(KEY_EMAIL_TO, "").orEmpty(),
        ),
    )

    /** Saves everything; a secret field left blank keeps the secret already stored. */
    fun save(settings: DeliverySettings) {
        val editor = prefs.edit()
            .putBoolean(KEY_TELEGRAM_ENABLED, settings.telegramEnabled)
            .putString(KEY_TELEGRAM_CHAT, settings.telegram.chatId.trim())
            .putBoolean(KEY_EMAIL_ENABLED, settings.emailEnabled)
            .putString(KEY_EMAIL_HOST, settings.email.host.trim())
            .putInt(KEY_EMAIL_PORT, settings.email.port)
            .putString(KEY_EMAIL_SECURITY, settings.email.security.name)
            .putString(KEY_EMAIL_USER, settings.email.username.trim())
            .putString(KEY_EMAIL_FROM, settings.email.from.trim())
            .putString(KEY_EMAIL_TO, settings.email.to.trim())
        if (settings.telegram.botToken.isNotBlank()) {
            editor.putString(KEY_TELEGRAM_TOKEN, sealSecret(KEY_TELEGRAM_TOKEN, settings.telegram.botToken.trim()))
        }
        if (settings.email.appPassword.isNotBlank()) {
            // App passwords are shown grouped by spaces; the server wants them without.
            editor.putString(KEY_EMAIL_PASSWORD, sealSecret(KEY_EMAIL_PASSWORD, settings.email.appPassword.replace(" ", "")))
        }
        editor.apply()
    }

    fun hasSecret(field: SecretField): Boolean = prefs.contains(
        when (field) {
            SecretField.TelegramToken -> KEY_TELEGRAM_TOKEN
            SecretField.EmailPassword -> KEY_EMAIL_PASSWORD
        },
    )

    enum class SecretField { TelegramToken, EmailPassword }

    private fun sealSecret(field: String, value: String): String {
        val payload = encryptor.encrypt(
            plaintext = value.toByteArray(StandardCharsets.UTF_8),
            keyAlias = KEY_ALIAS,
            policy = EvidenceKeyPolicy.EVIDENCE_POLICY,
            associatedData = aad(field),
        )
        return Base64.getEncoder().encodeToString(payload.toByteArray())
    }

    private fun readSecret(field: String): String {
        val stored = prefs.getString(field, null) ?: return ""
        return runCatching {
            val payload = EncryptedPayload.fromByteArray(Base64.getDecoder().decode(stored))
            String(
                encryptor.decrypt(payload, KEY_ALIAS, EvidenceKeyPolicy.EVIDENCE_POLICY, aad(field)),
                StandardCharsets.UTF_8,
            )
        }.getOrDefault("")
    }

    private fun aad(field: String): ByteArray = "shieldra-delivery-v1:$field".toByteArray(StandardCharsets.UTF_8)

    private companion object {
        const val PREFERENCES_NAME = "shieldra_delivery"
        /** Its own key: losing or rotating the evidence key must never touch the delivery secrets. */
        const val KEY_ALIAS = "shieldra_delivery_v1"
        const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
        const val KEY_TELEGRAM_TOKEN = "telegram_token"
        const val KEY_TELEGRAM_CHAT = "telegram_chat"
        const val KEY_EMAIL_ENABLED = "email_enabled"
        const val KEY_EMAIL_HOST = "email_host"
        const val KEY_EMAIL_PORT = "email_port"
        const val KEY_EMAIL_SECURITY = "email_security"
        const val KEY_EMAIL_USER = "email_user"
        const val KEY_EMAIL_PASSWORD = "email_password"
        const val KEY_EMAIL_FROM = "email_from"
        const val KEY_EMAIL_TO = "email_to"
    }
}
