package com.shieldra.app.presentation.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    /** Exact-alarm access, so the countdown fires on the second instead of "within the hour" (A-33). */
    val exactAlarms: Boolean = false,
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
    onGrantExactAlarms: () -> Unit,
    /**
     * Opens the vendor battery-manager guide. The exemption above is the only one of those switches an
     * Intent can reach, so the rest have to be flipped by hand (audit development ب).
     */
    onOpenSurvivalGuide: () -> Unit = {},
    onArmToggle: () -> Unit,
    onSave: (ProtectionSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** True only for copies not installed from Google Play (see the plan switch below). */
    planSwitchAllowed: Boolean = false,
    onTierChange: (com.shieldra.billing.Tier) -> Unit = {},
    /** Opens the Pro screen; a feature the free plan lacks sends the owner there. */
    onOpenPro: () -> Unit = {},
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

        // ===== Plan (test switch) =====
        // The plan is Google Play's to decide (A-40). This manual switch exists only for copies that were
        // not installed from Play, so a sideloaded test build can try both plans; a Play build never shows it.
        if (planSwitchAllowed) {
            SectionTitle(stringResource(R.string.protection_plan_title))
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
                    onClick = {
                        draft = draft.copy(tier = com.shieldra.billing.Tier.FREE)
                        onTierChange(com.shieldra.billing.Tier.FREE)
                    },
                )
                ShieldraChip(
                    label = stringResource(R.string.protection_plan_premium),
                    selected = draft.tier == com.shieldra.billing.Tier.PREMIUM,
                    onClick = {
                        draft = draft.copy(tier = com.shieldra.billing.Tier.PREMIUM)
                        onTierChange(com.shieldra.billing.Tier.PREMIUM)
                    },
                )
            }
        }

        // ===== Permissions checklist =====
        SectionTitle(stringResource(R.string.protection_permissions_title))
        PermissionRow(stringResource(R.string.protection_perm_camera), permissions.camera, onGrantCamera)
        PermissionRow(stringResource(R.string.protection_perm_location), permissions.location, onGrantLocation)
        PermissionRow(stringResource(R.string.protection_perm_notifications), permissions.notifications, onGrantNotifications)
        PermissionRow(stringResource(R.string.protection_perm_device_admin), permissions.deviceAdmin, onEnableDeviceAdmin)
        PermissionRow(stringResource(R.string.protection_perm_phone), permissions.phone, onGrantPhone)
        PermissionRow(stringResource(R.string.protection_perm_keep_awake), permissions.batteryExemption, onKeepAwake)
        // Exact scheduling is a special access the owner grants in system settings; until then the
        // countdown still fires, just less precisely (audit F-PLAY-1, A-33).
        PermissionRow(stringResource(R.string.protection_perm_exact_alarms), permissions.exactAlarms, onGrantExactAlarms)
        Spacer(Modifier.height(spacing.s))
        // The guards die on the vendor's battery manager long before any thief touches them, and no
        // Intent opens those menus: the screen states the paths and the owner follows them by hand.
        ShieldraFullWidthButton(
            text = stringResource(R.string.survival_guide_button),
            onClick = onOpenSurvivalGuide,
            variant = ShieldraButtonVariant.Secondary,
        )

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

        GuardToggle(
            title = stringResource(R.string.protection_motion_with_arming),
            subtitle = stringResource(R.string.protection_motion_with_arming_desc),
            checked = draft.motionEnabled,
        ) { draft = draft.copy(motionEnabled = it) }

        // ===== Evidence (dual camera + audio) =====
        SectionTitle(stringResource(R.string.protection_evidence_title))
        GuardToggle(
            title = stringResource(R.string.protection_dual_camera),
            subtitle = stringResource(R.string.protection_dual_camera_desc),
            checked = draft.dualCameraEnabled && com.shieldra.billing.TierGate.allowsBackCamera(draft.tier),
            locked = !com.shieldra.billing.TierGate.allowsBackCamera(draft.tier),
            onLocked = onOpenPro,
        ) { draft = draft.copy(dualCameraEnabled = it) }
        GuardToggle(
            title = stringResource(R.string.protection_audio),
            subtitle = stringResource(R.string.protection_audio_desc),
            checked = draft.audioEnabled && com.shieldra.billing.TierGate.allowsAudio(draft.tier),
            locked = !com.shieldra.billing.TierGate.allowsAudio(draft.tier),
            onLocked = onOpenPro,
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
            checked = draft.countdownEnabled && com.shieldra.billing.TierGate.allowsCountdown(draft.tier),
            locked = !com.shieldra.billing.TierGate.allowsCountdown(draft.tier),
            onLocked = onOpenPro,
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
            checked = draft.remoteCommandsEnabled && com.shieldra.billing.TierGate.allowsRemote(draft.tier),
            locked = !com.shieldra.billing.TierGate.allowsRemote(draft.tier),
            onLocked = onOpenPro,
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

        GuardToggle(
            title = stringResource(R.string.protection_track_enable),
            subtitle = stringResource(R.string.protection_track_desc),
            checked = draft.liveTrackingEnabled && com.shieldra.billing.TierGate.allowsTracking(draft.tier),
            locked = !com.shieldra.billing.TierGate.allowsTracking(draft.tier),
            onLocked = onOpenPro,
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
private fun GuardToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    locked: Boolean = false,
    onLocked: () -> Unit = {},
    onChange: (Boolean) -> Unit,
) {
    val spacing = ShieldraTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (locked) Modifier.clickable(onClick = onLocked) else Modifier)
            .padding(vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (locked) "$title  ·  ${stringResource(R.string.pro_pill)}" else title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (locked) {
            ShieldraSwitch(checked = false, onCheckedChange = { onLocked() })
        } else {
            ShieldraSwitch(checked = checked, onCheckedChange = onChange)
        }
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
