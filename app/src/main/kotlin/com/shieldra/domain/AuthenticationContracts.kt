package com.shieldra.domain

enum class SensitiveOperation {
    DISABLE_PROTECTION,
    CONFIGURE_DELIVERY_CHANNEL,
    DELETE_SENSITIVE_EVIDENCE,
    CHANGE_SENSITIVE_SETTING,
}

enum class AuthenticationDecision {
    REQUIRED,
    GRANTED,
    DENIED,
}

interface AuthenticationGate {
    fun check(operation: SensitiveOperation): AuthenticationDecision
}
