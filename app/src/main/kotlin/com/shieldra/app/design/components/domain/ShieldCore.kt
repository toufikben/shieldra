package com.shieldra.app.design.components.domain

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.graphics.createShieldPath
import com.shieldra.app.design.graphics.dashedStrokeEffect
import com.shieldra.app.design.graphics.drawGlyphCheck
import com.shieldra.app.design.graphics.drawGlyphExclamation
import com.shieldra.app.design.graphics.drawGlyphSlash
import com.shieldra.app.design.graphics.drawGlyphX
import com.shieldra.app.design.theme.ShieldraTheme
import com.shieldra.app.R
import com.shieldra.app.presentation.model.ProtectionVisualState

private data class ShieldCoreConfig(
    val color: Color,
    val ringWidthDp: Float,
    val haloAlpha: Float,
    val pulseMs: Int,
    val pulseScale: Float,
    val label: String,
    val accessibilityLabel: String,
)

@Composable
private fun configFor(state: ProtectionVisualState): ShieldCoreConfig {
    val semantic = ShieldraTheme.semantic
    return when (state) {
        ProtectionVisualState.Protected -> ShieldCoreConfig(
            color = semantic.protection,
            ringWidthDp = 4f,
            haloAlpha = 0.18f,
            pulseMs = 3000,
            pulseScale = 1.015f,
            label = stringResource(R.string.protection_protected),
            accessibilityLabel = stringResource(R.string.protection_protected_accessibility),
        )
        ProtectionVisualState.Suspicious -> ShieldCoreConfig(
            color = semantic.suspicious,
            ringWidthDp = 5f,
            haloAlpha = 0.22f,
            pulseMs = 1400,
            pulseScale = 1.03f,
            label = stringResource(R.string.protection_suspicious),
            accessibilityLabel = stringResource(R.string.protection_suspicious_accessibility),
        )
        ProtectionVisualState.SecurityEvent -> ShieldCoreConfig(
            color = semantic.securityEvent,
            ringWidthDp = 6f,
            haloAlpha = 0.28f,
            pulseMs = 0,
            pulseScale = 1f,
            label = stringResource(R.string.protection_security_event),
            accessibilityLabel = stringResource(R.string.protection_security_event_accessibility),
        )
        ProtectionVisualState.Attention -> ShieldCoreConfig(
            color = semantic.attention,
            ringWidthDp = 4f,
            haloAlpha = 0.20f,
            pulseMs = 2000,
            pulseScale = 1.02f,
            label = stringResource(R.string.protection_attention),
            accessibilityLabel = stringResource(R.string.protection_attention_accessibility),
        )
        ProtectionVisualState.Disabled -> ShieldCoreConfig(
            color = semantic.disabledState,
            ringWidthDp = 3f,
            haloAlpha = 0f,
            pulseMs = 0,
            pulseScale = 1f,
            label = stringResource(R.string.protection_disabled),
            accessibilityLabel = stringResource(R.string.protection_disabled_accessibility),
        )
    }
}

@Composable
fun ShieldCore(
    state: ProtectionVisualState,
    lastCheckSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val config = configFor(state)
    val spacing = ShieldraTheme.spacing
    val surface = MaterialTheme.colorScheme.surface
    val easing: Easing = FastOutSlowInEasing
    val lastCheckLabel = formatLastCheck(lastCheckSeconds)
    val accessibilityDescription = "${config.accessibilityLabel}. $lastCheckLabel"

    val scale: Float = if (config.pulseMs > 0) {
        val transition = rememberInfiniteTransition(label = "shield_pulse")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = config.pulseScale,
            animationSpec = infiniteRepeatable(
                animation = tween(config.pulseMs, easing = easing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "shield_scale",
        )
        animated
    } else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xxl)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            if (config.haloAlpha > 0f) {
                Canvas(Modifier.size(240.dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                config.color.copy(alpha = config.haloAlpha),
                                Color.Transparent,
                            ),
                            center = c,
                            radius = r,
                        ),
                        radius = r,
                        center = c,
                    )
                }
            }

            Canvas(Modifier.size(180.dp)) {
                val path = createShieldPath(Size(size.width, size.height))
                drawPath(path, surface)
                if (state == ProtectionVisualState.Disabled) {
                    drawPath(
                        path = path,
                        color = config.color,
                        style = Stroke(
                            width = config.ringWidthDp.dp.toPx(),
                            pathEffect = dashedStrokeEffect(),
                        ),
                    )
                } else {
                    drawPath(
                        path = path,
                        color = config.color,
                        style = Stroke(width = config.ringWidthDp.dp.toPx()),
                    )
                }
            }

            Canvas(Modifier.size(64.dp)) {
                val s = Size(size.width, size.height)
                when (state) {
                    ProtectionVisualState.Protected -> drawGlyphCheck(s, config.color)
                    ProtectionVisualState.Suspicious,
                    ProtectionVisualState.Attention -> drawGlyphExclamation(s, config.color)
                    ProtectionVisualState.SecurityEvent -> drawGlyphX(s, config.color)
                    ProtectionVisualState.Disabled -> drawGlyphSlash(s, config.color)
                }
            }
        }

        Spacer(Modifier.height(spacing.xxl))

        Text(
            text = config.label,
            style = MaterialTheme.typography.displayMedium,
            color = config.color,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = lastCheckLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun formatLastCheck(seconds: Long): String = when {
    seconds < 60 -> stringResource(R.string.last_check_seconds, seconds)
    seconds < 3600 -> stringResource(R.string.last_check_minutes, seconds / 60)
    else -> stringResource(R.string.last_check_hours, seconds / 3600)
}

@Preview(name = "ShieldCore Protected", showBackground = true)
@Composable
private fun ShieldCoreProtectedPreview() {
    ShieldraTheme(darkTheme = true) {
        ShieldCore(ProtectionVisualState.Protected, 12)
    }
}

@Preview(name = "ShieldCore Disabled", showBackground = true)
@Composable
private fun ShieldCoreDisabledPreview() {
    ShieldraTheme(darkTheme = true) {
        ShieldCore(ProtectionVisualState.Disabled, 12)
    }
}
