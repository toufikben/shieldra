package com.shieldra.app.presentation.screen.premium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.components.foundation.ShieldraFullWidthButton
import com.shieldra.app.design.components.foundation.ShieldraButtonVariant
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.presentation.model.PremiumFeatureUiModel
import com.shieldra.app.presentation.model.PremiumUiModel
import com.shieldra.app.presentation.preview.PreviewData

@Composable
fun PremiumScreen(
    model: PremiumUiModel,
    callbacks: PremiumCallbacks,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        Spacer(Modifier.height(spacing.xxl))
        Text(
            text = stringResource(R.string.premium_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.premium_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.l))

        model.features.forEach { FeatureRow(it) }

        Spacer(Modifier.height(spacing.l))

        ShieldraFullWidthButton(
            text = model.priceLabel?.let { "Buy Lifetime — $it" } ?: "Buy Lifetime",
            onClick = callbacks.onBuy,
        )
        ShieldraFullWidthButton(
            text = stringResource(R.string.premium_restore),
            onClick = callbacks.onRestore,
            variant = ShieldraButtonVariant.Ghost,
        )
        Spacer(Modifier.height(spacing.l))
        Text(
            text = model.freeTierSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xxxl))
    }
}

@Composable
private fun FeatureRow(feature: PremiumFeatureUiModel) {
    Column {
        Text(
            text = "✓  ${feature.title}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (feature.subtitle != null) {
            Text(
                text = feature.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 2.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PremiumDarkPreview() {
    ShieldraTheme(darkTheme = true) {
        PremiumScreen(
            model = PreviewData.premium,
            callbacks = PremiumCallbacks({}, {}),
        )
    }
}
