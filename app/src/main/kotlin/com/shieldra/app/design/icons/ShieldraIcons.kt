package com.shieldra.app.design.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.shieldra.app.presentation.model.EventType
import com.shieldra.app.presentation.model.GuardKind
import com.shieldra.app.presentation.model.SettingsIcon

fun GuardKind.icon(): ImageVector = when (this) {
    GuardKind.Lock -> Icons.Filled.Lock
    GuardKind.Motion -> Icons.AutoMirrored.Filled.DirectionsRun
    GuardKind.Sim -> Icons.Filled.SimCard
    GuardKind.Panic -> Icons.Filled.WarningAmber
    GuardKind.Battery -> Icons.Filled.BatteryAlert
}

fun EventType.icon(): ImageVector = when (this) {
    EventType.FailedUnlock -> Icons.Filled.Lock
    EventType.Motion -> Icons.AutoMirrored.Filled.DirectionsRun
    EventType.SimChange -> Icons.Filled.SimCard
    EventType.Panic -> Icons.Filled.WarningAmber
    EventType.Battery -> Icons.Filled.BatteryAlert
}

fun SettingsIcon.icon(): ImageVector = when (this) {
    SettingsIcon.Security -> Icons.Filled.Security
    SettingsIcon.Delivery -> Icons.Filled.PhoneAndroid
    SettingsIcon.Lock -> Icons.Filled.Lock
    SettingsIcon.Theme -> Icons.Filled.ColorLens
    SettingsIcon.Language -> Icons.Filled.Language
    SettingsIcon.Storage -> Icons.Filled.Storage
    SettingsIcon.Retention -> Icons.Filled.DeleteOutline
    SettingsIcon.Info -> Icons.Filled.Info
    SettingsIcon.Help -> Icons.Filled.Bolt
}
