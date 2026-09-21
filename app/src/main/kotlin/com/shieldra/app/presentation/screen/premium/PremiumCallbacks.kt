package com.shieldra.app.presentation.screen.premium

import androidx.compose.runtime.Immutable

@Immutable
data class PremiumCallbacks(
    val onBuy: () -> Unit,
    val onRestore: () -> Unit,
)
