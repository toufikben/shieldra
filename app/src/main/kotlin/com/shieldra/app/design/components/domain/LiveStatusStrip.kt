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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.theme.ShieldraTheme

@Composable
fun LiveStatusStrip(
    lastCheckLabel: String,
    batteryPercent: Int?,
    networkLabel: String,
    networkOk: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = spacing.l, vertical = spacing.m)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Last check $lastCheckLabel. ")
                    if (batteryPercent != null) append("Battery $batteryPercent percent. ")
                    append(if (networkOk) "Network connected: $networkLabel" else "Network offline")
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusSlot(
            icon = Icons.Filled.Schedule,
            primary = lastCheckLabel,
            secondary = "Last check",
        )
        VerticalDivider()
        StatusSlot(
            icon = Icons.Filled.BatteryFull,
            primary = batteryPercent?.let { "$it%" } ?: "—",
            secondary = "Battery",
        )
        VerticalDivider()
        StatusSlot(
            icon = if (networkOk) Icons.Filled.Wifi else Icons.Filled.WifiOff,
            primary = if (networkOk) networkLabel else "Offline",
            secondary = "Network",
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
            networkLabel = "Wi-Fi",
            networkOk = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}
