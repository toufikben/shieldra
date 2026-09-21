package com.shieldra.app.design.tokens

import androidx.compose.ui.graphics.Color

/**
 * Raw palette. Never referenced directly in UI code —
 * use MaterialTheme.colorScheme or ShieldraSemanticColors.
 */
internal object ShieldraPalette {

    // ========== Dark ==========
    val DeepSpace        = Color(0xFF0A0E14)
    val Ink              = Color(0xFF141922)
    val InkElevated      = Color(0xFF1C222D)
    val InkBorder        = Color(0xFF232A38)

    val ShieldTeal       = Color(0xFF00D4AA)
    val ShieldTealDim    = Color(0xFF00A889)
    val ShieldTealGlow   = Color(0x3300D4AA)

    val AlertAmber       = Color(0xFFFFB547)
    val AlertAmberGlow   = Color(0x33FFB547)

    val CriticalRed      = Color(0xFFFF5470)
    val CriticalRedGlow  = Color(0x33FF5470)

    val AttentionOrange  = Color(0xFFFF8A4C)
    val AttentionGlow    = Color(0x33FF8A4C)

    val DisabledGrayDark = Color(0xFF4A5361)

    val TextPrimaryDark  = Color(0xFFE8EDF2)
    val TextSecondaryDark= Color(0xFF8B95A5)
    val TextTertiaryDark = Color(0xFF5A6470)
    val TextOnAccentDark = Color(0xFF001A15)

    // ========== Light ==========
    val Paper            = Color(0xFFF6F8FA)
    val PaperSurface     = Color(0xFFFFFFFF)
    val PaperElevated    = Color(0xFFF1F4F8)
    val PaperBorder      = Color(0xFFE2E8F0)

    val ShieldTealLight  = Color(0xFF00806A)
    val ShieldTealLightDim = Color(0xFF006B58)
    val ShieldTealLightGlow= Color(0x1F00806A)

    val AlertAmberLight  = Color(0xFF9A6300)
    val AlertAmberLightGlow = Color(0x1F9A6300)

    val CriticalRedLight = Color(0xFFB72E4C)
    val CriticalRedLightGlow = Color(0x1FB72E4C)

    val AttentionLight   = Color(0xFFB85A1E)
    val AttentionLightGlow = Color(0x1FB85A1E)

    val DisabledGrayLight = Color(0xFF9AA3B0)

    val TextPrimaryLight = Color(0xFF0F1419)
    val TextSecondaryLight = Color(0xFF4A5361)
    val TextTertiaryLight = Color(0xFF78828F)
    val TextOnAccentLight = Color(0xFFFFFFFF)

    // ========== Utility ==========
    val Transparent      = Color(0x00000000)
}

/**
 * Semantic colors that Material3 does not model.
 * Accessed via LocalShieldraSemanticColors.
 */
data class ShieldraSemanticColors(
    val protection: Color,
    val protectionContainer: Color,
    val suspicious: Color,
    val suspiciousContainer: Color,
    val securityEvent: Color,
    val securityEventContainer: Color,
    val attention: Color,
    val attentionContainer: Color,
    val disabledState: Color,
    val disabledStateContainer: Color,
)

internal val ShieldraDarkSemanticColors = ShieldraSemanticColors(
    protection = ShieldraPalette.ShieldTeal,
    protectionContainer = ShieldraPalette.ShieldTealGlow,
    suspicious = ShieldraPalette.AlertAmber,
    suspiciousContainer = ShieldraPalette.AlertAmberGlow,
    securityEvent = ShieldraPalette.CriticalRed,
    securityEventContainer = ShieldraPalette.CriticalRedGlow,
    attention = ShieldraPalette.AttentionOrange,
    attentionContainer = ShieldraPalette.AttentionGlow,
    disabledState = ShieldraPalette.DisabledGrayDark,
    disabledStateContainer = ShieldraPalette.InkBorder,
)

internal val ShieldraLightSemanticColors = ShieldraSemanticColors(
    protection = ShieldraPalette.ShieldTealLight,
    protectionContainer = ShieldraPalette.ShieldTealLightGlow,
    suspicious = ShieldraPalette.AlertAmberLight,
    suspiciousContainer = ShieldraPalette.AlertAmberLightGlow,
    securityEvent = ShieldraPalette.CriticalRedLight,
    securityEventContainer = ShieldraPalette.CriticalRedLightGlow,
    attention = ShieldraPalette.AttentionLight,
    attentionContainer = ShieldraPalette.AttentionLightGlow,
    disabledState = ShieldraPalette.DisabledGrayLight,
    disabledStateContainer = ShieldraPalette.PaperBorder,
)
