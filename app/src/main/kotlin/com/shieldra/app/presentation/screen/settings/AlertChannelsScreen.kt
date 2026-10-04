package com.shieldra.app.presentation.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.shieldra.app.R
import com.shieldra.app.design.components.foundation.ShieldraButton
import com.shieldra.app.design.components.foundation.ShieldraButtonVariant
import com.shieldra.app.design.components.foundation.ShieldraChip
import com.shieldra.app.design.components.foundation.ShieldraFullWidthButton
import com.shieldra.app.design.components.foundation.ShieldraSwitch
import com.shieldra.app.design.components.foundation.ShieldraTextField
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraCardShape
import com.shieldra.delivery.ChannelResult
import com.shieldra.delivery.DeliverySettings
import com.shieldra.delivery.EmailConfig
import com.shieldra.delivery.MailSecurity
import com.shieldra.delivery.TelegramConfig
import kotlinx.coroutines.launch

/**
 * Where the owner sets up the channels alerts leave the phone through (A-32): their own Telegram
 * bot and their own email account with an app password. Secrets are never shown back: a saved one
 * reads as "saved" and is replaced only when something new is typed.
 */
@Composable
fun AlertChannelsScreen(
    saved: DeliverySettings,
    onSave: (DeliverySettings) -> Unit,
    onTest: suspend (DeliverySettings, testTelegram: Boolean) -> ChannelResult,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val scope = rememberCoroutineScope()

    var telegramEnabled by remember(saved) { mutableStateOf(saved.telegramEnabled) }
    var botToken by remember(saved) { mutableStateOf("") }
    var chatId by remember(saved) { mutableStateOf(saved.telegram.chatId) }
    var emailEnabled by remember(saved) { mutableStateOf(saved.emailEnabled) }
    var host by remember(saved) { mutableStateOf(saved.email.host) }
    var port by remember(saved) { mutableStateOf(saved.email.port.toString()) }
    var security by remember(saved) { mutableStateOf(saved.email.security) }
    var account by remember(saved) { mutableStateOf(saved.email.username) }
    var appPassword by remember(saved) { mutableStateOf("") }
    var recipient by remember(saved) { mutableStateOf(saved.email.to) }
    var telegramResult by remember { mutableStateOf<ChannelResult?>(null) }
    var emailResult by remember { mutableStateOf<ChannelResult?>(null) }
    var testing by remember { mutableStateOf(false) }

    // A blank secret field means "keep the one already saved".
    fun current(): DeliverySettings = DeliverySettings(
        telegramEnabled = telegramEnabled,
        telegram = TelegramConfig(
            botToken = botToken.trim().ifBlank { saved.telegram.botToken },
            chatId = chatId.trim(),
        ),
        emailEnabled = emailEnabled,
        email = EmailConfig(
            host = host.trim(),
            port = port.trim().toIntOrNull() ?: DeliverySettings.DEFAULT_MAIL_PORT,
            security = security,
            username = account.trim(),
            appPassword = appPassword.replace(" ", "").ifBlank { saved.email.appPassword },
            from = account.trim(),
            to = recipient.trim(),
        ),
    )

    fun runTest(telegram: Boolean) {
        testing = true
        scope.launch {
            val result = onTest(current(), telegram)
            if (telegram) telegramResult = result else emailResult = result
            testing = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        Text(
            text = stringResource(R.string.alert_channels_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.alert_channels_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ChannelCard(
            title = stringResource(R.string.alert_channel_telegram),
            enabled = telegramEnabled,
            onEnabledChange = { telegramEnabled = it },
        ) {
            ShieldraTextField(
                value = botToken,
                onValueChange = { botToken = it },
                label = stringResource(R.string.alert_telegram_token),
                placeholder = savedPlaceholder(saved.telegram.botToken.isNotBlank()),
                visualTransformation = PasswordVisualTransformation(),
            )
            ShieldraTextField(
                value = chatId,
                onValueChange = { chatId = it },
                label = stringResource(R.string.alert_telegram_chat),
                supportingText = stringResource(R.string.alert_telegram_help),
            )
            TestRow(
                result = telegramResult,
                enabled = !testing,
                onTest = { runTest(telegram = true) },
            )
        }

        ChannelCard(
            title = stringResource(R.string.alert_channel_email),
            enabled = emailEnabled,
            onEnabledChange = { emailEnabled = it },
        ) {
            ShieldraTextField(
                value = account,
                onValueChange = { account = it },
                label = stringResource(R.string.alert_email_account),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            ShieldraTextField(
                value = appPassword,
                onValueChange = { appPassword = it },
                label = stringResource(R.string.alert_email_app_password),
                placeholder = savedPlaceholder(saved.email.appPassword.isNotBlank()),
                supportingText = stringResource(R.string.alert_email_app_password_help),
                visualTransformation = PasswordVisualTransformation(),
            )
            ShieldraTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = stringResource(R.string.alert_email_recipient),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            ShieldraTextField(
                value = host,
                onValueChange = { host = it },
                label = stringResource(R.string.alert_email_server),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalAlignment = Alignment.CenterVertically) {
                ShieldraTextField(
                    value = port,
                    onValueChange = { value -> port = value.filter(Char::isDigit).take(5) },
                    label = stringResource(R.string.alert_email_port),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                ShieldraChip(
                    label = stringResource(R.string.alert_email_ssl),
                    selected = security == MailSecurity.SSL_TLS,
                    onClick = { security = MailSecurity.SSL_TLS; if (port == "587") port = "465" },
                )
                ShieldraChip(
                    label = stringResource(R.string.alert_email_starttls),
                    selected = security == MailSecurity.STARTTLS,
                    onClick = { security = MailSecurity.STARTTLS; if (port == "465") port = "587" },
                )
            }
            TestRow(
                result = emailResult,
                enabled = !testing,
                onTest = { runTest(telegram = false) },
            )
        }

        ShieldraFullWidthButton(
            text = stringResource(R.string.alert_channels_save),
            onClick = { onSave(current()) },
        )
        ShieldraFullWidthButton(
            text = stringResource(R.string.action_cancel),
            onClick = onBack,
            variant = ShieldraButtonVariant.Ghost,
        )
        Spacer(Modifier.height(spacing.xl))
    }
}

@Composable
private fun savedPlaceholder(isSaved: Boolean): String? =
    if (isSaved) stringResource(R.string.alert_secret_saved) else null

@Composable
private fun ChannelCard(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val spacing = ShieldraTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ShieldraSwitch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) content()
    }
}

@Composable
private fun TestRow(result: ChannelResult?, enabled: Boolean, onTest: () -> Unit) {
    val spacing = ShieldraTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
        ShieldraButton(
            text = stringResource(R.string.alert_send_test),
            onClick = onTest,
            enabled = enabled,
            variant = ShieldraButtonVariant.Secondary,
        )
        result?.let {
            Text(
                text = when (it) {
                    ChannelResult.Sent -> stringResource(R.string.alert_test_sent)
                    is ChannelResult.Retryable -> stringResource(R.string.alert_test_retryable, it.reason)
                    is ChannelResult.Rejected -> stringResource(R.string.alert_test_rejected, it.reason)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (it == ChannelResult.Sent) ShieldraTheme.semantic.protection else MaterialTheme.colorScheme.error,
            )
        }
    }
}
