package com.shieldra.app.design.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.staticCompositionLocalOf

data class ShieldraMotion(
    val instantMs: Int = 100,
    val fastMs: Int = 180,
    val standardMs: Int = 260,
    val slowMs: Int = 420,
    val breathingMs: Int = 3000,
    val suspiciousPulseMs: Int = 1400,

    val easingStandard: Easing = FastOutSlowInEasing,
    val easingEmphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val easingDecelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f),
)

val LocalShieldraMotion = staticCompositionLocalOf { ShieldraMotion() }
