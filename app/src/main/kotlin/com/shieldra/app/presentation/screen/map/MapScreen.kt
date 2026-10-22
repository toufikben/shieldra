package com.shieldra.app.presentation.screen.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.components.foundation.GlassCard
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.presentation.model.EventUiModel

/**
 * The Map/Tracking tab. Honest projection: it reports the real tracking configuration and whether
 * protection is armed and location is granted — never a fabricated live map. The most recent event
 * that actually carries a location opens on the event screen's own map.
 */
@Composable
fun MapScreen(
    armed: Boolean,
    liveTrackingEnabled: Boolean,
    intervalMinutes: Int,
    durationMinutes: Int,
    locationGranted: Boolean,
    lastLocatedEvent: EventUiModel?,
    onOpenEvent: (String) -> Unit,
    onOpenProtectionSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = ShieldraTheme.semantic.protection
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.map_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Tracking status
        GlassCard {
            StatusRow(
                accent = accent,
                on = armed,
                title = stringResource(if (armed) R.string.map_armed else R.string.map_disarmed),
            )
            Spacer(Modifier.height(10.dp))
            StatusRow(
                accent = accent,
                on = liveTrackingEnabled,
                title = stringResource(if (liveTrackingEnabled) R.string.map_tracking_on else R.string.map_tracking_off),
            )
            if (liveTrackingEnabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.map_tracking_detail, intervalMinutes, durationMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            StatusRow(
                accent = accent,
                on = locationGranted,
                title = stringResource(if (locationGranted) R.string.map_location_granted else R.string.map_location_missing),
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOpenProtectionSetup) {
                Text(stringResource(R.string.map_open_protection))
            }
        }

        // Last recorded location
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.map_last_location),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            val event = lastLocatedEvent
            if (event == null) {
                Text(
                    text = stringResource(R.string.map_no_location),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(event.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    event.timestampLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { onOpenEvent(event.id) }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.map_open_event))
                }
            }
        }

        // Remote hint
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.map_remote_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(accent: Color, on: Boolean, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val dot = if (on) accent else MaterialTheme.colorScheme.outline
        Spacer(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}
