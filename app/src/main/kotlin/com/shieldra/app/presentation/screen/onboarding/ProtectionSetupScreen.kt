package com.shieldra.app.presentation.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.components.foundation.ShieldraFullWidthButton
import com.shieldra.app.design.components.foundation.ShieldraSlider
import com.shieldra.app.design.theme.ShieldraTheme

@Composable
fun ProtectionSetupScreen(
    initialThreshold: Int,
    onContinue: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val threshold = remember { mutableFloatStateOf(initialThreshold.toFloat()) }
    val displayed = threshold.floatValue.toInt()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.lock_guard),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(spacing.m))
        Text(
            text = stringResource(R.string.protection_setup_question),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.xxxl))
        Text(
            text = "$displayed",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(spacing.l))
        ShieldraSlider(
            value = threshold.floatValue,
            onValueChange = { threshold.floatValue = it },
            valueRange = 1f..5f,
            steps = 3,
            contentDescription = stringResource(R.string.failed_attempts_threshold),
        )
        Spacer(Modifier.height(spacing.m))
        Text(
            text = stringResource(R.string.recommended_threshold),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = stringResource(R.string.attempt_recording_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.xxxl))
        ShieldraFullWidthButton(
            text = stringResource(R.string.action_continue),
            onClick = { onContinue(displayed) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProtectionSetupPreview() {
    ShieldraTheme(darkTheme = true) {
        ProtectionSetupScreen(initialThreshold = 2, onContinue = {})
    }
}
