package com.shieldra.app.presentation.screen.settings

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsCallbacks(
    val onRowClick: (String, requiresAuth: Boolean) -> Unit,
)
