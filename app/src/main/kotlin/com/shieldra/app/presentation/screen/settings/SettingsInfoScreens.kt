package com.shieldra.app.presentation.screen.settings

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
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
import androidx.compose.foundation.text.selection.SelectionContainer
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

/**
 * The inverted help screen. The owner is not asking how the app works here — they are holding someone
 * else's phone and saying "mine is gone". Every card is therefore an instruction that can be carried
 * out from somewhere else, in the order that keeps the most: the handset locked, the position taken,
 * the person seen, the numbers changed, and the wipe left to last, because a wipe destroys the events
 * and photos this app stored on that device.
 *
 * The screen states its own conditions and its own limits, in the same place as the steps. This is the
 * moment an over-claim costs an hour that cannot be recovered, so the commands say what they need to
 * answer at all (armed, Premium, Telegram switched on, the phone online) and the last card says plainly
 * that SHIELDRA never locks or wipes the handset and never reports a position it did not measure.
 */
@Composable
fun PanicGuideScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    InfoPage(title = stringResource(R.string.panic_guide_title), onBack = onBack, modifier = modifier) {
        GlassCard {
            Note(stringResource(R.string.panic_guide_intro))
        }
        listOf(
            R.string.panic_step_lock_title to R.string.panic_step_lock_body,
            R.string.panic_step_locate_title to R.string.panic_step_locate_body,
            R.string.panic_step_photo_title to R.string.panic_step_photo_body,
            R.string.panic_step_signal_title to R.string.panic_step_signal_body,
            R.string.panic_step_imei_title to R.string.panic_step_imei_body,
            R.string.panic_step_passwords_title to R.string.panic_step_passwords_body,
            R.string.panic_step_erase_title to R.string.panic_step_erase_body,
        ).forEachIndexed { index, (title, body) ->
            GlassCard {
                Text(
                    text = "${index + 1}. ${stringResource(title)}",
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
        listOf(
            R.string.panic_evidence_title to R.string.panic_evidence_body,
            R.string.panic_limits_title to R.string.panic_limits_body,
        ).forEach { (title, body) ->
            GlassCard {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Note(stringResource(body))
            }
        }
    }
}

/**
 * The battery-survival guide (audit development ب). On the owner's own handset the thing that kills
 * the guards first is not the thief but the vendor's battery manager, and none of those switches has a
 * public Intent that opens them: `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` reaches one of the eight,
 * the rest live only inside ColorOS's own menus. So this screen is written text, with the literal menu
 * names and a selectable path per step — the owner searches for the words rather than hunting by
 * memory, and the app never touches the clipboard to hand them over (F-K3 keeps that door shut).
 *
 * The measured line at the top says which phone the owner is reading it on, because the guide is advice
 * about a skin and an advice column that pretends to know your exact build is lying. The closing card
 * says what the app cannot do: none of this is a Shieldra setting, and no permission lets it be flipped.
 */
@Composable
fun BatterySurvivalScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    InfoPage(title = stringResource(R.string.survival_guide_title), onBack = onBack, modifier = modifier) {
        GlassCard {
            Text(
                text = stringResource(R.string.survival_this_phone, Build.MANUFACTURER, Build.VERSION.RELEASE),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ShieldraTheme.semantic.protection,
            )
            Spacer(Modifier.height(6.dp))
            Note(stringResource(R.string.survival_guide_intro))
        }
        listOf(
            SurvivalStep(
                R.string.survival_autolaunch_title,
                R.string.survival_autolaunch_body,
                R.string.survival_autolaunch_path,
            ),
            SurvivalStep(
                R.string.survival_background_title,
                R.string.survival_background_body,
                R.string.survival_background_path,
            ),
            SurvivalStep(
                R.string.survival_unused_title,
                R.string.survival_unused_body,
                R.string.survival_unused_path,
            ),
            SurvivalStep(
                R.string.survival_freeze_title,
                R.string.survival_freeze_body,
                R.string.survival_freeze_path,
            ),
            SurvivalStep(
                R.string.survival_recents_title,
                R.string.survival_recents_body,
                R.string.survival_recents_path,
            ),
            SurvivalStep(
                R.string.survival_powersave_title,
                R.string.survival_powersave_body,
                R.string.survival_powersave_path,
            ),
            SurvivalStep(
                R.string.survival_optimise_title,
                R.string.survival_optimise_body,
                R.string.survival_optimise_path,
            ),
            SurvivalStep(
                R.string.survival_cleaner_title,
                R.string.survival_cleaner_body,
                R.string.survival_cleaner_path,
            ),
        ).forEach { step ->
            // Read the path once, here: stringResource is a composable call and the button's onClick is
            // not a composable context, so the copied text has to be captured before the lambda.
            val path = stringResource(step.pathRes)
            GlassCard {
                Text(
                    text = stringResource(step.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ShieldraTheme.semantic.protection,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(step.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                // Selectable, not copied by the app: a clipboard write is an egress primitive under
                // this project's own policy (F-K3), and the path is a constant the owner can select
                // with the system's long-press UI. Nothing about evidence ever passes through here.
                SelectionContainer {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.survival_copy_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        GlassCard {
            Note(stringResource(R.string.survival_limits))
        }
    }
}

/** One menu switch the owner has to flip by hand: what it is, why, and the literal path to it. */
private data class SurvivalStep(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
    @param:StringRes val pathRes: Int,
)

/**
 * The honest comparison with Android's own theft protection (audit development د). Android 15 to 17
 * ship a large part of what an anti-theft app used to be for, and a paid app that hides that sells
 * something the owner already owns. So this screen names the platform features first, says what each
 * one does not do, and then states the single thing SHIELDRA adds on top: a record of who tried, sent
 * to a third party of the owner's choosing at the moment it happens.
 *
 * Nothing here is measured on the phone — the feature names and their behaviour move between makers and
 * versions, which the closing card says out loud, and the Health screen stays the one place that reads
 * what this app can actually prove about this device.
 */
@Composable
fun PlatformComparisonScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    InfoPage(title = stringResource(R.string.compare_title), onBack = onBack, modifier = modifier) {
        GlassCard {
            Note(stringResource(R.string.compare_intro))
        }
        listOf(
            R.string.compare_theft_lock_title to R.string.compare_theft_lock_body,
            R.string.compare_failed_auth_title to R.string.compare_failed_auth_body,
            R.string.compare_identity_title to R.string.compare_identity_body,
            R.string.compare_offline_title to R.string.compare_offline_body,
            R.string.compare_attempts_title to R.string.compare_attempts_body,
            R.string.compare_find_title to R.string.compare_find_body,
        ).forEach { (title, body) ->
            GlassCard {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        GlassCard {
            Text(
                text = stringResource(R.string.compare_shieldra_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ShieldraTheme.semantic.protection,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.compare_shieldra_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.compare_closing),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ShieldraTheme.semantic.protection,
            )
        }
        GlassCard {
            Note(stringResource(R.string.compare_limits))
        }
    }
}
