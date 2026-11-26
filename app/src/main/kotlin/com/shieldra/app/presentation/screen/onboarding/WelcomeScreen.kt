package com.shieldra.app.presentation.screen.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.shieldra.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shieldra.app.design.components.foundation.ShieldraFullWidthButton
import com.shieldra.app.design.components.foundation.ShieldraButtonVariant
import com.shieldra.app.design.graphics.createShieldPath
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.shieldra.app.design.theme.ShieldraTheme

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ShieldraTheme.spacing
    val motion = ShieldraTheme.motion
    val primary = MaterialTheme.colorScheme.primary
    // The launcher icon cannot move, so the same "alive" mark breathes here: the shield carries the
    // heartbeat motif of the icon and a slow halo, matching ShieldCore's calm breathing timing.
    val pulse = rememberInfiniteTransition(label = "welcome-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(motion.breathingMs, easing = motion.easingStandard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "welcome-pulse-scale",
    )
    val haloAlpha by pulse.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(motion.breathingMs, easing = motion.easingStandard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "welcome-pulse-halo",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ShieldraTheme.semantic.protection.copy(alpha = 0.12f), Color.Transparent),
                    endY = 700f,
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(180.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = haloAlpha), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.5f,
                ),
                radius = size.minDimension * 0.5f,
                center = center,
            )
            val shieldSize = Size(size.width * 0.8f, size.height * 0.8f)
            val shieldPath = createShieldPath(shieldSize)
            scale(pulseScale, pulseScale, pivot = center) {
                translate(
                    left = (size.width - shieldSize.width) / 2f,
                    top = (size.height - shieldSize.height) / 2f,
                ) {
                    drawPath(
                        path = shieldPath,
                        color = primary,
                        style = Stroke(width = 4.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(spacing.xxl))
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(spacing.s))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.l))
        Text(
            text = stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.xxxl))
        ShieldraFullWidthButton(stringResource(R.string.action_get_started), onGetStarted)
        Spacer(Modifier.height(spacing.s))
        ShieldraFullWidthButton(
            text = stringResource(R.string.welcome_skip),
            onClick = onSkip,
            variant = ShieldraButtonVariant.Ghost,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomePreview() {
    ShieldraTheme(darkTheme = true) {
        WelcomeScreen(onGetStarted = {}, onSkip = {})
    }
}
