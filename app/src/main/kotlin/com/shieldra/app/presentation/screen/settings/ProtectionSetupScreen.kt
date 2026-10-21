package com.shieldra.app.presentation.screen.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.components.foundation.ShieldraButtonVariant
import com.shieldra.app.design.components.foundation.ShieldraChip
import com.shieldra.app.design.components.foundation.ShieldraFullWidthButton
import com.shieldra.app.design.components.foundation.ShieldraSlider
import com.shieldra.app.design.components.foundation.ShieldraSwitch
import com.shieldra.app.design.components.foundation.ShieldraTextField
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.protection.CountdownDeadline
import com.shieldra.protection.ProtectionSettings

/** The permissions and consents a guard needs, read live from the platform. */
data class ProtectionPermissions(
    val camera: Boolean = false,
    val location: Boolean = false,
    val phone: Boolean = false,
    val notifications: Boolean = false,
    val deviceAdmin: Boolean = false,
    /** Battery-optimization exemption, so the system does not doze the guards (A-33). */
    val batteryExemption: Boolean = false,
)

/**
 * The one screen the owner arms protection from and tunes the guards on (A-33/A-34). It states what
 * each guard will do, shows which permissions are still needed with a one-tap grant, and never
 * pretends a guard is working when its permission is missing. Arming is the explicit owner action
 * that lets the camera and location work in the background afterwards.
 */
@Composable
fun ProtectionSetupScreen(
    armed: Boolean,
    permissions: ProtectionPermissions,
    settings: ProtectionSettings,
    onGrantCamera: () -> Unit,
    onGrantLocation: () -> Unit,
    onGrantPhone: () -> Unit,
    onGrantNotifications: () -> Unit,
    onEnableDeviceAdmin: () -> Unit,
    onKeepAwake: () -> Unit,
    onArmToggle: () -> Unit,
    onSave: (ProtectionSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    var draft by remember(settings) { mutableStateOf(settings) }
    var customMinutes by remember(settings) { mutableStateOf("") }
    var customCountdownOpen by remember(settings) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.l, vertical = spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.l),
    ) {
        HeroHeader(armed = armed, onArmToggle = onArmToggle)

        PermissionsCard(permissions, onGrantCamera, onGrantLocation, onGrantNotifications, onEnableDeviceAdmin, onGrantPhone, onKeepAwake)

        ProtectionCard(Icons.Filled.WorkspacePremium, stringResource(R.string.protection_plan_title)) {
            Text(
                text = stringResource(R.string.protection_plan_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                ShieldraChip(
                    label = stringResource(R.string.protection_plan_free),
                    selected = draft.tier == com.shieldra.billing.Tier.FREE,
                    onClick = { draft = draft.copy(tier = com.shieldra.billing.Tier.FREE) },
                )
                ShieldraChip(
                    label = stringResource(R.string.protection_plan_premium),
                    selected = draft.tier == com.shieldra.billing.Tier.PREMIUM,
                    onClick = { draft = draft.copy(tier = com.shieldra.billing.Tier.PREMIUM) },
                )
            }
        }

        ProtectionCard(Icons.Filled.Security, stringResource(R.string.protection_guards_title)) {
            GuardToggle(
                title = stringResource(R.string.protection_guard_lock),
                subtitle = stringResource(R.string.protection_guard_lock_desc),
                checked = draft.lockEnabled,
            ) { draft = draft.copy(lockEnabled = it) }
            if (draft.lockEnabled) {
                StepperRow(
                    label = stringResource(R.string.protection_lock_threshold, draft.lockThreshold),
                    value = draft.lockThreshold.toFloat(), range = 1f..5f, steps = 3,
                ) { draft = draft.copy(lockThreshold = it.toInt()) }
            }
            GuardToggle(
                title = stringResource(R.string.protection_guard_sim),
                subtitle = stringResource(R.string.protection_guard_sim_desc),
                checked = draft.simEnabled,
            ) { draft = draft.copy(simEnabled = it) }
            GuardToggle(
                title = stringResource(R.string.protection_guard_battery),
                subtitle = stringResource(R.string.protection_guard_battery_desc),
                checked = draft.batteryEnabled,
            ) { draft = draft.copy(batteryEnabled = it) }
            if (draft.batteryEnabled) {
                StepperRow(
                    label = stringResource(R.string.protection_battery_level, draft.batteryThreshold),
                    value = draft.batteryThreshold.toFloat(), range = 5f..50f, steps = 8,
                ) { draft = draft.copy(batteryThreshold = it.toInt()) }
            }
        }

        ProtectionCard(Icons.Filled.PhotoCamera, stringResource(R.string.protection_evidence_title)) {
            GuardToggle(
                title = stringResource(R.string.protection_dual_camera),
                subtitle = stringResource(R.string.protection_dual_camera_desc),
                checked = draft.dualCameraEnabled,
            ) { draft = draft.copy(dualCameraEnabled = it) }
            GuardToggle(
                title = stringResource(R.string.protection_audio),
                subtitle = stringResource(R.string.protection_audio_desc),
                checked = draft.audioEnabled,
            ) { draft = draft.copy(audioEnabled = it) }
            if (draft.audioEnabled) {
                StepperRow(
                    label = stringResource(R.string.protection_audio_seconds, draft.audioSeconds),
                    value = draft.audioSeconds.toFloat(), range = 5f..30f, steps = 24,
                ) { draft = draft.copy(audioSeconds = it.toInt()) }
            }
        }

        ProtectionCard(Icons.Filled.Vibration, stringResource(R.string.protection_motion_title)) {
            Text(
                text = stringResource(R.string.protection_motion_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.s))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                ShieldraChip(
                    label = stringResource(R.string.protection_motion_silent),
                    selected = draft.motionResponse == ProtectionSettings.MotionResponse.SILENT,
                    onClick = { draft = draft.copy(motionResponse = ProtectionSettings.MotionResponse.SILENT) },
                )
                ShieldraChip(
                    label = stringResource(R.string.protection_motion_siren),
                    selected = draft.motionResponse == ProtectionSettings.MotionResponse.SIREN,
                    onClick = { draft = draft.copy(motionResponse = ProtectionSettings.MotionResponse.SIREN) },
                )
                ShieldraChip(
                    label = stringResource(R.string.protection_motion_grace),
                    selected = draft.motionResponse == ProtectionSettings.MotionResponse.GRACE_THEN_SIREN,
                    onClick = { draft = draft.copy(motionResponse = ProtectionSettings.MotionResponse.GRACE_THEN_SIREN) },
                )
            }
            if (draft.motionResponse == ProtectionSettings.MotionResponse.GRACE_THEN_SIREN) {
                Spacer(Modifier.height(spacing.s))
                StepperRow(
                    label = stringResource(R.string.protection_motion_grace_seconds, draft.motionGraceSeconds),
                    value = draft.motionGraceSeconds.toFloat(), range = 3f..30f, steps = 26,
                ) { draft = draft.copy(motionGraceSeconds = it.toInt()) }
            }
        }

        ProtectionCard(Icons.Filled.Timer, stringResource(R.string.protection_countdown_title)) {
            GuardToggle(
                title = stringResource(R.string.protection_countdown_enable),
                subtitle = stringResource(R.string.protection_countdown_desc),
                checked = draft.countdownEnabled,
            ) { draft = draft.copy(countdownEnabled = it) }
            if (draft.countdownEnabled) {
                Spacer(Modifier.height(spacing.s))
                val isCustom = draft.countdownIntervalMs !in CountdownDeadline.PRESETS_MS
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    CountdownDeadline.PRESETS_MS.forEach { ms -> CountdownPreset(draft, ms) { draft = it } }
                    ShieldraChip(
                        label = stringResource(R.string.protection_countdown_custom),
                        selected = isCustom,
                        onClick = { customCountdownOpen = true },
                    )
                }
                if (customCountdownOpen || isCustom) {
                    Spacer(Modifier.height(spacing.xs))
                    ShieldraTextField(
                        value = customMinutes,
                        onValueChange = { value ->
                            customMinutes = value.filter(Char::isDigit).take(5)
                            customMinutes.toLongOrNull()?.let { minutes ->
                                draft = draft.copy(countdownIntervalMs = (minutes * 60_000L).coerceAtLeast(60_000L))
                            }
                        },
                        label = stringResource(R.string.protection_countdown_custom_minutes),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        }

        ProtectionCard(Icons.AutoMirrored.Filled.Send, stringResource(R.string.protection_remote_title)) {
            GuardToggle(
                title = stringResource(R.string.protection_remote_enable),
                subtitle = stringResource(R.string.protection_remote_desc),
                checked = draft.remoteCommandsEnabled,
            ) { draft = draft.copy(remoteCommandsEnabled = it) }
            if (draft.remoteCommandsEnabled) {
                Text(
                    text = stringResource(R.string.protection_remote_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.s))
                ShieldraTextField(
                    value = draft.ownerContact,
                    onValueChange = { draft = draft.copy(ownerContact = it) },
                    label = stringResource(R.string.protection_owner_contact),
                )
                ShieldraTextField(
                    value = draft.lostMessage,
                    onValueChange = { draft = draft.copy(lostMessage = it) },
                    label = stringResource(R.string.protection_lost_message),
                )
            }
        }

        ProtectionCard(Icons.Filled.MyLocation, stringResource(R.string.protection_track_enable)) {
            GuardToggle(
                title = stringResource(R.string.protection_track_enable),
                subtitle = stringResource(R.string.protection_track_desc),
                checked = draft.liveTrackingEnabled,
            ) { draft = draft.copy(liveTrackingEnabled = it) }
        }

        ShieldraFullWidthButton(
            text = stringResource(R.string.protection_save),
            onClick = { onSave(draft) },
            modifier = Modifier.fillMaxWidth(),
        )
        ShieldraFullWidthButton(
            text = stringResource(R.string.action_cancel),
            onClick = onBack,
            variant = ShieldraButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The armed/disarmed hero: a shield badge, the live state, and the one big arm/disarm action. */
@Composable
private fun HeroHeader(armed: Boolean, onArmToggle: () -> Unit) {
    val spacing = ShieldraTheme.spacing
    val accent by animateColorAsState(
        if (armed) ShieldraTheme.semantic.protection else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "heroAccent",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.16f), MaterialTheme.colorScheme.surface),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = accent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(spacing.m))
        Text(
            text = stringResource(R.string.protection_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = stringResource(if (armed) R.string.protection_state_armed else R.string.protection_state_disarmed),
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.m))
        ShieldraFullWidthButton(
            text = stringResource(if (armed) R.string.protection_disarm else R.string.protection_arm),
            onClick = onArmToggle,
            variant = if (armed) ShieldraButtonVariant.Destructive else ShieldraButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The permissions card with a granted/total progress bar and a one-tap grant on each row. */
@Composable
private fun PermissionsCard(
    permissions: ProtectionPermissions,
    onCamera: () -> Unit,
    onLocation: () -> Unit,
    onNotifications: () -> Unit,
    onDeviceAdmin: () -> Unit,
    onPhone: () -> Unit,
    onKeepAwake: () -> Unit,
) {
    val spacing = ShieldraTheme.spacing
    val rows = listOf(
        Triple(stringResource(R.string.protection_perm_camera), permissions.camera, onCamera),
        Triple(stringResource(R.string.protection_perm_location), permissions.location, onLocation),
        Triple(stringResource(R.string.protection_perm_notifications), permissions.notifications, onNotifications),
        Triple(stringResource(R.string.protection_perm_device_admin), permissions.deviceAdmin, onDeviceAdmin),
        Triple(stringResource(R.string.protection_perm_phone), permissions.phone, onPhone),
        Triple(stringResource(R.string.protection_perm_keep_awake), permissions.batteryExemption, onKeepAwake),
    )
    val granted = rows.count { it.second }
    ProtectionCard(Icons.Filled.TravelExplore, stringResource(R.string.protection_permissions_title)) {
        Text(
            text = "$granted / ${rows.size}",
            style = MaterialTheme.typography.labelLarge,
            color = ShieldraTheme.semantic.protection,
        )
        Spacer(Modifier.height(spacing.xs))
        LinearProgressIndicator(
            progress = { granted.toFloat() / rows.size },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = ShieldraTheme.semantic.protection,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(spacing.s))
        rows.forEach { (label, ok, grant) -> PermissionRow(label, ok, grant) }
    }
}

/** A rounded, bordered card with an accent icon badge and a title — the shared section shell. */
@Composable
private fun ProtectionCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    val spacing = ShieldraTheme.spacing
    val accent = ShieldraTheme.semantic.protection
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(spacing.m))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(spacing.m))
        content()
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    val spacing = ShieldraTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) ShieldraTheme.semantic.protection else ShieldraTheme.semantic.attention,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(spacing.m))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!granted) {
            ShieldraChip(label = stringResource(R.string.protection_grant), selected = false, onClick = onGrant)
        }
    }
}

@Composable
private fun GuardToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val spacing = ShieldraTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(spacing.s))
        ShieldraSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    val spacing = ShieldraTheme.spacing
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ShieldraSlider(value = value, onValueChange = onChange, valueRange = range, steps = steps, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(spacing.xs))
}

@Composable
private fun CountdownPreset(draft: ProtectionSettings, intervalMs: Long, onPick: (ProtectionSettings) -> Unit) {
    ShieldraChip(
        label = countdownLabel(intervalMs),
        selected = draft.countdownIntervalMs == intervalMs,
        onClick = { onPick(draft.copy(countdownIntervalMs = intervalMs)) },
    )
}

@Composable
private fun countdownLabel(intervalMs: Long): String {
    val minutes = intervalMs / 60_000L
    return when {
        minutes < 60 -> stringResource(R.string.protection_countdown_minutes, minutes)
        minutes < 24 * 60 -> stringResource(R.string.protection_countdown_hours, minutes / 60)
        else -> stringResource(R.string.protection_countdown_days, minutes / (24 * 60))
    }
}
