package com.shieldra.delivery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeliverySettingsTest {

    private val completeTelegram = TelegramConfig(
        botToken = "123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
        chatId = "123456789",
    )

    // Audit development (ز) / B-4: a switched-on channel with an unreadable secret is named, never
    // silently dropped. These are the two facts the dispatcher turns into a recorded FAILED receipt.
    @Test
    fun a_switched_on_channel_with_an_unreadable_secret_is_named_with_its_reason() {
        val settings = DeliverySettings(
            telegramEnabled = true,
            telegram = TelegramConfig(botToken = "", chatId = "123456789"),
            emailEnabled = true,
            email = EmailConfig(
                host = "smtp.gmail.com",
                port = 465,
                security = MailSecurity.SSL_TLS,
                username = "owner@example.com",
                appPassword = "",
                from = "owner@example.com",
                to = "owner@example.com",
            ),
            unreadableSecrets = setOf(DeliveryChannel.TELEGRAM, DeliveryChannel.EMAIL),
        )

        assertTrue(settings.activeChannels().isEmpty())
        assertEquals(
            listOf(
                UnusableChannel(DeliveryChannel.TELEGRAM, DeliverySettings.REASON_TOKEN_UNREADABLE),
                UnusableChannel(DeliveryChannel.EMAIL, DeliverySettings.REASON_PASSWORD_UNREADABLE),
            ),
            settings.unusableChannels(),
        )
    }

    @Test
    fun a_channel_the_owner_switched_off_stays_silent_even_with_an_unreadable_secret() {
        val settings = DeliverySettings(
            telegramEnabled = false,
            unreadableSecrets = setOf(DeliveryChannel.TELEGRAM),
        )

        assertTrue(settings.unusableChannels().isEmpty())
        assertTrue(settings.activeChannels().isEmpty())
    }

    @Test
    fun a_filled_in_channel_is_used_and_is_not_reported_as_unusable() {
        val settings = DeliverySettings(telegramEnabled = true, telegram = completeTelegram)

        assertEquals(listOf(DeliveryChannel.TELEGRAM), settings.activeChannels().map { it.channel })
        assertTrue(settings.unusableChannels().isEmpty())
    }
}
