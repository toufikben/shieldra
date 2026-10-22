package com.shieldra.app.design.components.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The premium surface used across the app's screens: a softly translucent, rounded panel with a
 * hairline border. Colours come from the theme so it reads correctly in both light and dark, and
 * the translucency lets the ambient background glow show through for depth.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = GLASS_FILL_ALPHA))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = GLASS_BORDER_ALPHA), shape)
            .padding(contentPadding),
        content = content,
    )
}

private const val GLASS_FILL_ALPHA: Float = 0.60f
private const val GLASS_BORDER_ALPHA: Float = 0.45f

/** A faint teal wash for hero areas; pass the theme's protection colour as [accent]. */
fun glassAccentWash(accent: Color): Color = accent.copy(alpha = 0.12f)
