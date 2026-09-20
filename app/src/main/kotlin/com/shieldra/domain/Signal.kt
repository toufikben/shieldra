package com.shieldra.domain

import java.time.Instant

enum class SignalType {
    LOCK,
    MOTION,
    SIM,
    BATTERY,
    PANIC,
}

enum class SignalStrength {
    STRONG,
    MEDIUM,
    WEAK,
}

data class Signal(
    val type: SignalType,
    val strength: SignalStrength,
    val timestamp: Instant,
    val context: Map<String, String> = emptyMap(),
)
