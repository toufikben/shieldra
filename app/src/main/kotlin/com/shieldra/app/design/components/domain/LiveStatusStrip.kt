package com.shieldra.app.design.components.domain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.R
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.design.tokens.ShieldraCardShape
import com.shieldra.app.presentation.model.NetworkStatus
import com.shieldra.app.presentation.guard.networkSlotLabelRes

@Composable
fun LiveStatusStrip(
    lastCheckLabel: String,
    batteryPercent: Int?,
    networkLabel: String,
    networkStatus: NetworkStatus,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    // The offline wording is only reachable from a reading that measured it; an
    // unmeasured state keeps the caller's own "not measured" label (F-K7).
    val overrideRes = networkSlotLabelRes(networkStatus)
    val shownNetworkLabel = if (overrideRes == null) networkLabel else stringResource(overrideRes)
    val networkDescription = when (networkStatus) {
        NetworkStatus.Online -> stringResource(R.string.status_network_connected, shownNetworkLabel)
        NetworkStatus.Offline -> stringResource(R.string.status_network_offline)
        NetworkStatus.Unknown -> shownNetworkLabel
    }
    val batteryDescription = batteryPercent?.let {
        stringResource(R.string.status_battery_percent, it)
    } ?: stringResource(R.string.status_battery_unknown)
    val accessibilityDescription = stringResource(
        R.string.status_network_semantics,
        lastCheckLabel,
        batteryDescription,
        networkDescription,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShieldraCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = spacing.l, vertical = spacing.m)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusSlot(
            icon = Icons.Filled.Schedule,
            primary = lastCheckLabel,
            secondary = stringResource(R.string.status_last_check),
        )
        VerticalDivider()
        StatusSlot(
            icon = Icons.Filled.BatteryFull,
            primary = batteryPercent?.let { "$it%" } ?: "—",
            secondary = stringResource(R.string.status_battery),
        )
        VerticalDivider()
        StatusSlot(
            icon = when (networkStatus) {
                NetworkStatus.Online -> Icons.Filled.Wifi
                NetworkStatus.Offline -> Icons.Filled.WifiOff
                // Nothing has measured the network, so no glyph may draw a
                // conclusion the label does not make (F-K7).
                NetworkStatus.Unknown -> Icons.AutoMirrored.Filled.HelpOutline
            },
            primary = shownNetworkLabel,
            secondary = stringResource(R.string.status_network),
        )
    }
}

@Composable
private fun StatusSlot(
    icon: ImageVector,
    primary: String,
    secondary: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = primary,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = secondary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Preview(showBackground = true)
@Composable
private fun LiveStatusStripPreview() {
    ShieldraTheme(darkTheme = true) {
        LiveStatusStrip(
            lastCheckLabel = "12s ago",
            batteryPercent = 84,
            networkLabel = "Not measured",
            networkStatus = NetworkStatus.Unknown,
            modifier = Modifier.padding(16.dp),
        )
    }
}
