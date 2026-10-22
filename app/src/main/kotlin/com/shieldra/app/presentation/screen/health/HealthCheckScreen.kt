package com.shieldra.app.presentation.screen.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.components.foundation.GlassCard
import com.shieldra.app.design.theme.ShieldraTheme

/** The verified facts a health check shows; every field is read from the real device state. */
data class HealthChecks(
    val armed: Boolean,
    val camera: Boolean,
    val location: Boolean,
    val notifications: Boolean,
    val deviceAdmin: Boolean,
    val batteryExempt: Boolean,
    val channel: Boolean,
    val motionMonitoring: Boolean,
)

/**
 * A read-only "what is really protecting this phone" screen. The score and every row come from the
 * live device state (permissions, device admin, battery exemption, a connected channel, the running
 * monitor) — honest projection, never a flattering default.
 */
@Composable
fun HealthCheckScreen(
    score: Int,
    checks: HealthChecks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = ShieldraTheme.semantic.protection
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.health_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        GlassCard {
            Text(
                text = stringResource(R.string.health_score),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$score / 100",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.health_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GlassCard {
            val rows = listOf(
                R.string.health_check_armed to checks.armed,
                R.string.health_check_camera to checks.camera,
                R.string.health_check_location to checks.location,
                R.string.health_check_notifications to checks.notifications,
                R.string.health_check_device_admin to checks.deviceAdmin,
                R.string.health_check_battery to checks.batteryExempt,
                R.string.health_check_channel to checks.channel,
                R.string.health_check_motion to checks.motionMonitoring,
            )
            rows.forEachIndexed { index, (labelRes, on) ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                CheckRow(label = stringResource(labelRes), on = on)
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, on: Boolean) {
    val color = if (on) ShieldraTheme.semantic.protection else MaterialTheme.colorScheme.outline
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(if (on) R.string.health_on else R.string.health_off),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
