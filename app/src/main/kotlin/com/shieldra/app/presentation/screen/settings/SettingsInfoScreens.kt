package com.shieldra.app.presentation.screen.settings

import android.content.Context
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.components.foundation.GlassCard
import com.shieldra.app.design.components.foundation.ShieldraButton
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.presentation.settings.ByteSize
import com.shieldra.storage.EvidenceRetentionPolicy
import com.shieldra.storage.LocalShieldraStorage
import com.shieldra.storage.RetentionSweepReport

/** The shared frame of the four information pages: back button, title, the teal mesh, a scroll body. */
@Composable
private fun InfoPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spacing = ShieldraTheme.spacing
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
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        content()
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** What this phone really holds: counts and bytes measured from the stored data, never estimated. */
@Composable
fun StorageScreen(
    figures: LocalShieldraStorage.Figures?,
    storageInstalled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InfoPage(title = stringResource(R.string.settings_storage), onBack = onBack, modifier = modifier) {
        GlassCard {
            if (!storageInstalled) {
                Text(stringResource(R.string.settings_storage_none), style = MaterialTheme.typography.bodyLarge)
            } else {
                FactRow(stringResource(R.string.storage_events), figures?.eventRows?.toString() ?: "—")
                Spacer(Modifier.height(10.dp))
                FactRow(stringResource(R.string.storage_evidence_files), figures?.evidenceFiles?.toString() ?: "—")
                Spacer(Modifier.height(10.dp))
                FactRow(
                    stringResource(R.string.storage_evidence_size),
                    figures?.let { ByteSize.format(it.evidenceBytes) } ?: "—",
                )
            }
        }
        GlassCard {
            Note(stringResource(R.string.storage_encrypted_note))
        }
    }
}

/** The approved retention windows, the last cleanup's real outcome, and a manual cleanup. */
@Composable
fun RetentionScreen(
    storageInstalled: Boolean,
    lastReport: RetentionSweepReport?,
    running: Boolean,
    onRunNow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InfoPage(title = stringResource(R.string.settings_retention), onBack = onBack, modifier = modifier) {
        GlassCard {
            FactRow(
                stringResource(R.string.retention_events),
                stringResource(R.string.retention_days, EvidenceRetentionPolicy.EVENT_METADATA_RETENTION_DAYS.toInt()),
            )
            Spacer(Modifier.height(10.dp))
            FactRow(
                stringResource(R.string.retention_evidence),
                stringResource(R.string.retention_days, EvidenceRetentionPolicy.EVIDENCE_RETENTION_DAYS.toInt()),
            )
            Spacer(Modifier.height(10.dp))
            Note(stringResource(R.string.retention_note))
        }
        GlassCard {
            Text(
                text = stringResource(R.string.retention_last_cleanup),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            if (lastReport == null) {
                Note(stringResource(R.string.retention_no_cleanup))
            } else {
                FactRow(stringResource(R.string.retention_removed_events), lastReport.deletedEventRows.toString())
                Spacer(Modifier.height(6.dp))
                FactRow(
                    stringResource(R.string.retention_removed_files),
                    (lastReport.deletedEvidenceFileIds.size + lastReport.deletedQuarantineFileIds.size).toString(),
                )
                if (lastReport.failedIds.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FactRow(stringResource(R.string.retention_failed), lastReport.failedIds.size.toString())
                }
            }
            Spacer(Modifier.height(12.dp))
            ShieldraButton(
                text = stringResource(R.string.retention_run_now),
                onClick = onRunNow,
                enabled = storageInstalled && !running,
                loading = running,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What this build is: the version the package manager reports, and what the app does and does not do. */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (versionName, versionCode) = remember(context) { readVersion(context) }
    InfoPage(title = stringResource(R.string.settings_about_shieldra), onBack = onBack, modifier = modifier) {
        GlassCard {
            Text(
                text = "Shieldra",
                style = MaterialTheme.typography.headlineMedium,
                color = ShieldraTheme.semantic.protection,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FactRow(stringResource(R.string.about_version), versionName)
            Spacer(Modifier.height(8.dp))
            FactRow(stringResource(R.string.about_build), versionCode)
            Spacer(Modifier.height(8.dp))
            FactRow(stringResource(R.string.about_package), context.packageName)
        }
        GlassCard {
            Note(stringResource(R.string.about_privacy))
        }
    }
}

private fun readVersion(context: Context): Pair<String, String> = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        info.longVersionCode.toString()
    } else {
        @Suppress("DEPRECATION") info.versionCode.toString()
    }
    (info.versionName ?: "—") to code
}.getOrDefault("—" to "—")

/** How to set Shieldra up and use it, in the owner's language. */
@Composable
fun HelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    InfoPage(title = stringResource(R.string.settings_help), onBack = onBack, modifier = modifier) {
        listOf(
            R.string.help_start_title to R.string.help_start_body,
            R.string.help_telegram_title to R.string.help_telegram_body,
            R.string.help_email_title to R.string.help_email_body,
            R.string.help_commands_title to R.string.help_commands_body,
            R.string.help_zones_title to R.string.help_zones_body,
            R.string.help_export_title to R.string.help_export_body,
            R.string.help_battery_title to R.string.help_battery_body,
        ).forEach { (title, body) ->
            GlassCard {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ShieldraTheme.semantic.protection,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** What Shieldra stores, where it can go, and what the owner controls. */
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    InfoPage(title = stringResource(R.string.settings_privacy), onBack = onBack, modifier = modifier) {
        listOf(
            R.string.privacy_collect_title to R.string.privacy_collect_body,
            R.string.privacy_where_title to R.string.privacy_where_body,
            R.string.privacy_never_title to R.string.privacy_never_body,
            R.string.privacy_permissions_title to R.string.privacy_permissions_body,
            R.string.privacy_billing_title to R.string.privacy_billing_body,
            R.string.privacy_control_title to R.string.privacy_control_body,
        ).forEach { (title, body) ->
            GlassCard {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ShieldraTheme.semantic.protection,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
