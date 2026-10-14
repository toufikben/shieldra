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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.components.foundation.ShieldraButtonVariant
import com.shieldra.app.design.components.foundation.ShieldraChip
import com.shieldra.app.design.components.foundation.ShieldraFullWidthButton
import com.shieldra.app.design.components.foundation.ShieldraSlider
import com.shieldra.app.design.components.foundation.ShieldraSwitch
import com.shieldra.app.design.theme.ShieldraTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.shieldra.app.design.components.foundation.ShieldraTextField
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
    ) {
        // ===== Status + arm/disarm =====
        Text(
            text = stringResource(R.string.protection_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = stringResource(if (armed) R.string.protection_state_armed else R.string.protection_state_disarmed),
            style = MaterialTheme.typography.titleMedium,
            color = if (armed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.m))
        ShieldraFullWidthButton(
            text = stringResource(if (armed) R.string.protection_disarm else R.string.protection_arm),
            onClick = onArmToggle,
            variant = if (armed) ShieldraButtonVariant.Destructive else ShieldraButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
        )

        // ===== Permissions checklist =====
        SectionTitle(stringResource(R.string.protection_permissions_title))
        PermissionRow(stringResource(R.string.protection_perm_camera), permissions.camera, onGrantCamera)
        PermissionRow(stringResource(R.string.protection_perm_location), permissions.location, onGrantLocation)
        PermissionRow(stringResource(R.string.protection_perm_notifications), permissions.notifications, onGrantNotifications)
        PermissionRow(stringResource(R.string.protection_perm_device_admin), permissions.deviceAdmin, onEnableDeviceAdmin)
        PermissionRow(stringResource(R.string.protection_perm_phone), permissions.phone, onGrantPhone)
        PermissionRow(stringResource(R.string.protection_perm_keep_awake), permissions.batteryExemption, onKeepAwake)

        // ===== Guards =====
        SectionTitle(stringResource(R.string.protection_guards_title))
        GuardToggle(
            title = stringResource(R.string.protection_guard_lock),
            subtitle = stringResource(R.string.protection_guard_lock_desc),
            checked = draft.lockEnabled,
        ) { draft = draft.copy(lockEnabled = it) }
        if (draft.lockEnabled) {
            StepperRow(
                label = stringResource(R.string.protection_lock_threshold, draft.lockThreshold),
                value = draft.lockThreshold.toFloat(),
                range = 1f..5f,
                steps = 3,
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
                value = draft.batteryThreshold.toFloat(),
                range = 5f..50f,
                steps = 8,
            ) { draft = draft.copy(batteryThreshold = it.toInt()) }
        }

        // ===== Evidence (dual camera + audio) =====
        SectionTitle(stringResource(R.string.protection_evidence_title))
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
                value = draft.audioSeconds.toFloat(),
                range = 5f..30f,
                steps = 24,
            ) { draft = draft.copy(audioSeconds = it.toInt()) }
        }

        // ===== Table mode (motion response) =====
        SectionTitle(stringResource(R.string.protection_motion_title))
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
                value = draft.motionGraceSeconds.toFloat(),
                range = 3f..30f,
                steps = 26,
            ) { draft = draft.copy(motionGraceSeconds = it.toInt()) }
        }

        // ===== Countdown =====
        SectionTitle(stringResource(R.string.protection_countdown_title))
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
                CountdownDeadline.PRESETS_MS.forEach { ms ->
                    CountdownPreset(draft, ms) { draft = it }
                }
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
                            // Floor at the one-minute minimum the deadline enforces anyway.
                            draft = draft.copy(countdownIntervalMs = (minutes * 60_000L).coerceAtLeast(60_000L))
                        }
                    },
                    label = stringResource(R.string.protection_countdown_custom_minutes),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }

        // ===== Remote control (Telegram) =====
        SectionTitle(stringResource(R.string.protection_remote_title))
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
        }

        GuardToggle(
            title = stringResource(R.string.protection_track_enable),
            subtitle = stringResource(R.string.protection_track_desc),
            checked = draft.liveTrackingEnabled,
        ) { draft = draft.copy(liveTrackingEnabled = it) }

        Spacer(Modifier.height(spacing.xl))
        ShieldraFullWidthButton(
            text = stringResource(R.string.protection_save),
            onClick = { onSave(draft) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(spacing.s))
        ShieldraFullWidthButton(
            text = stringResource(R.string.action_cancel),
            onClick = onBack,
            variant = ShieldraButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    val spacing = ShieldraTheme.spacing
    Spacer(Modifier.height(spacing.l))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(spacing.s))
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    val spacing = ShieldraTheme.spacing
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.wrapContentWidth().padding(end = spacing.s))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!granted) {
                ShieldraChip(
                    label = stringResource(R.string.protection_grant),
                    selected = false,
                    onClick = onGrant,
                )
            }
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
