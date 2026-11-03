package com.shieldra.app.presentation.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.components.foundation.ShieldraButtonVariant
import com.shieldra.app.design.components.foundation.ShieldraFullWidthButton
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.shieldra.app.design.theme.ShieldraTheme

@Composable
fun ProtectionSetupScreen(
    onContinue: (Int) -> Unit,
    /** The vendor battery-manager guide, offered before the owner arms anything (audit development ب). */
    onOpenSurvivalGuide: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val displayed = 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ShieldraTheme.semantic.protection.copy(alpha = 0.12f), Color.Transparent),
                    endY = 700f,
                ),
            )
            .verticalScroll(rememberScrollState())
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
            text = stringResource(R.string.protection_step_inert),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.xxxl))
        ShieldraFullWidthButton(
            text = stringResource(R.string.action_continue),
            onClick = { onContinue(displayed) },
        )
        Spacer(Modifier.height(spacing.m))
        // Offered here rather than after arming, because on the owner's skin the battery manager is the
        // thing that stops the guards first — and a week from now they will not remember this screen.
        ShieldraFullWidthButton(
            text = stringResource(R.string.survival_guide_button),
            onClick = onOpenSurvivalGuide,
            variant = ShieldraButtonVariant.Secondary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProtectionSetupPreview() {
    ShieldraTheme(darkTheme = true) {
        ProtectionSetupScreen(onContinue = {})
    }
}
