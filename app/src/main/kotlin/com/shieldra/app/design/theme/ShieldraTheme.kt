package com.shieldra.app.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.shieldra.app.design.tokens.LocalShieldraMotion
import com.shieldra.app.design.tokens.LocalShieldraSpacing
import com.shieldra.app.design.tokens.ShieldraDarkSemanticColors
import com.shieldra.app.design.tokens.ShieldraLightSemanticColors
import com.shieldra.app.design.tokens.ShieldraMotion
import com.shieldra.app.design.tokens.ShieldraPalette
import com.shieldra.app.design.tokens.ShieldraSemanticColors
import com.shieldra.app.design.tokens.ShieldraShapes
import com.shieldra.app.design.tokens.ShieldraSpacing
import com.shieldra.app.design.tokens.ShieldraTypography

private val ShieldraDarkColorScheme = darkColorScheme(
    primary = ShieldraPalette.ShieldTeal,
    onPrimary = ShieldraPalette.TextOnAccentDark,
    primaryContainer = ShieldraPalette.ShieldTealGlow,
    onPrimaryContainer = ShieldraPalette.ShieldTeal,
    secondary = ShieldraPalette.TextSecondaryDark,
    onSecondary = ShieldraPalette.TextPrimaryDark,
    tertiary = ShieldraPalette.AlertAmber,
    onTertiary = ShieldraPalette.TextOnAccentDark,
    background = ShieldraPalette.DeepSpace,
    onBackground = ShieldraPalette.TextPrimaryDark,
    surface = ShieldraPalette.Ink,
    onSurface = ShieldraPalette.TextPrimaryDark,
    surfaceVariant = ShieldraPalette.InkElevated,
    onSurfaceVariant = ShieldraPalette.TextSecondaryDark,
    outline = ShieldraPalette.InkBorder,
    outlineVariant = ShieldraPalette.InkBorder,
    error = ShieldraPalette.CriticalRed,
    onError = ShieldraPalette.TextPrimaryDark,
    errorContainer = ShieldraPalette.CriticalRedGlow,
    onErrorContainer = ShieldraPalette.CriticalRed,
)

private val ShieldraLightColorScheme = lightColorScheme(
    primary = ShieldraPalette.ShieldTealLight,
    onPrimary = ShieldraPalette.TextOnAccentLight,
    primaryContainer = ShieldraPalette.ShieldTealLightGlow,
    onPrimaryContainer = ShieldraPalette.ShieldTealLight,
    secondary = ShieldraPalette.TextSecondaryLight,
    onSecondary = ShieldraPalette.TextOnAccentLight,
    tertiary = ShieldraPalette.AlertAmberLight,
    onTertiary = ShieldraPalette.TextOnAccentLight,
    background = ShieldraPalette.Paper,
    onBackground = ShieldraPalette.TextPrimaryLight,
    surface = ShieldraPalette.PaperSurface,
    onSurface = ShieldraPalette.TextPrimaryLight,
    surfaceVariant = ShieldraPalette.PaperElevated,
    onSurfaceVariant = ShieldraPalette.TextSecondaryLight,
    outline = ShieldraPalette.PaperBorder,
    outlineVariant = ShieldraPalette.PaperBorder,
    error = ShieldraPalette.CriticalRedLight,
    onError = ShieldraPalette.TextOnAccentLight,
    errorContainer = ShieldraPalette.CriticalRedLightGlow,
    onErrorContainer = ShieldraPalette.CriticalRedLight,
)

internal val LocalShieldraSemanticColors = staticCompositionLocalOf { ShieldraDarkSemanticColors }

@Composable
fun ShieldraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ShieldraDarkColorScheme else ShieldraLightColorScheme
    val semanticColors = if (darkTheme) ShieldraDarkSemanticColors else ShieldraLightSemanticColors

    CompositionLocalProvider(
        LocalShieldraSpacing provides ShieldraSpacing(),
        LocalShieldraMotion provides ShieldraMotion(),
        LocalShieldraSemanticColors provides semanticColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ShieldraTypography,
            shapes = ShieldraShapes,
            content = content,
        )
    }
}

object ShieldraTheme {
    val spacing: ShieldraSpacing
        @Composable @ReadOnlyComposable get() = LocalShieldraSpacing.current

    val motion: ShieldraMotion
        @Composable @ReadOnlyComposable get() = LocalShieldraMotion.current

    val semantic: ShieldraSemanticColors
        @Composable @ReadOnlyComposable get() = LocalShieldraSemanticColors.current
}
