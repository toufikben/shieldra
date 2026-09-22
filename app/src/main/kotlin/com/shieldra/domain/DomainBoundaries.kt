package com.shieldra.domain

interface ProtectionStateEngine {
    /** Only this boundary may create a confirmed SecurityEvent contract. */
    fun evaluate(
        signal: GuardSignal,
        clock: Clock,
        identityProvider: IdentityProvider,
    ): GuardEvaluation
}

data class GuardEvaluation(
    val confirmation: GuardConfirmation,
    val event: SecurityEvent? = null,
)

interface EventPipeline
