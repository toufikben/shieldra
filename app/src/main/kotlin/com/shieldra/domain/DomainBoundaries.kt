package com.shieldra.domain

interface ProtectionStateEngine {
    /** Only this boundary may create a confirmed SecurityEvent contract. */
    fun createConfirmedEvent(
        signal: Signal,
        clock: Clock,
        identityProvider: IdentityProvider,
    ): SecurityEvent
}

interface EventPipeline
