package com.shieldra.app.presentation.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import com.shieldra.app.design.icons.icon
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraCardShape
import com.shieldra.app.presentation.model.SettingsRowUiModel
import com.shieldra.app.presentation.model.SettingsSection
import com.shieldra.app.presentation.settings.honestSettingsRows
import com.shieldra.notification.NotificationPermissionState

@Composable
fun SettingsScreen(
    rows: List<SettingsRowUiModel>,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val grouped = rows.groupBy { it.section }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                Brush.verticalGradient(
                    colors = listOf(ShieldraTheme.semantic.protection.copy(alpha = 0.10f), Color.Transparent),
                    endY = 360f,
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.l, vertical = spacing.l),
    ) {
        // A section with no rows prints no heading: a title over nothing is a claim too.
        SECTION_ORDER.forEach { (section, titleRes) ->
            val sectionRows = grouped[section].orEmpty()
            if (sectionRows.isNotEmpty()) {
                SettingsSectionTitle(stringResource(titleRes))
                SettingsGroup(sectionRows, callbacks)
                Spacer(Modifier.height(spacing.xl))
            }
        }
        Spacer(Modifier.height(spacing.l))
    }
}

private val SECTION_ORDER: List<Pair<SettingsSection, Int>> = listOf(
    SettingsSection.Notifications to R.string.settings_section_notifications,
    SettingsSection.Appearance to R.string.settings_section_appearance,
    SettingsSection.PrivacyData to R.string.settings_section_data_privacy,
    SettingsSection.SecurityApp to R.string.settings_section_security_app,
    SettingsSection.About to R.string.settings_section_about,
)

@Composable
private fun SettingsSectionTitle(text: String) {
    val spacing = ShieldraTheme.spacing
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = spacing.xs, bottom = spacing.s),
    )
}

@Composable
private fun SettingsGroup(
    rows: List<SettingsRowUiModel>,
    callbacks: SettingsCallbacks,
) {
    if (rows.isEmpty()) return
    val spacing = ShieldraTheme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        rows.forEachIndexed { idx, row ->
            SettingsRow(row, onClick = { callbacks.onRowClick(row.id, row.requiresAuth) })
            if (idx < rows.lastIndex) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = spacing.l)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(row: SettingsRowUiModel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = row.icon.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (row.subtitle != null) {
                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsDarkPreview() {
    ShieldraTheme(darkTheme = true) {
        SettingsScreen(
            rows = honestSettingsRows(
                storageInstalled = false,
                notificationState = NotificationPermissionState.NotRequested,
            ),
            callbacks = SettingsCallbacks { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsLightPreview() {
    ShieldraTheme(darkTheme = false) {
        SettingsScreen(
            rows = honestSettingsRows(
                storageInstalled = false,
                notificationState = NotificationPermissionState.NotRequested,
            ),
            callbacks = SettingsCallbacks { _, _ -> },
        )
    }
}
