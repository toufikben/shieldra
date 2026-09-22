package com.shieldra.app.design.components.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.components.foundation.ShieldraButton
import com.shieldra.app.design.components.foundation.ShieldraButtonVariant
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraCardShape

@Composable
fun ErrorStateInline(
    title: String? = null,
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = title ?: stringResource(R.string.error_something_wrong),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(spacing.s))
            ShieldraButton(
                text = stringResource(R.string.action_retry),
                onClick = onRetry,
                variant = ShieldraButtonVariant.Secondary,
            )
        }
    }
}

@Composable
fun ErrorStateFullScreen(
    title: String? = null,
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title ?: stringResource(R.string.error_unable_to_load),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.s))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(spacing.xxl))
            ShieldraButton(text = stringResource(R.string.action_retry), onClick = onRetry)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorStateInlinePreview() {
    ShieldraTheme(darkTheme = true) {
        ErrorStateInline(
            message = stringResource(R.string.could_not_load_events),
            onRetry = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
