package com.shieldra.domain

import java.time.Instant

fun interface Clock {
    fun now(): Instant
}

fun interface IdentityProvider {
    fun nextEventId(): EventId
}

data class Age(
    val seconds: Long,
) {
    init {
        require(seconds >= 0) { "Age cannot be negative" }
    }
}
