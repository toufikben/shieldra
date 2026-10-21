package com.shieldra.billing

/** The owner's plan. FREE is the basic protection; PREMIUM unlocks every guard and channel. */
enum class Tier { FREE, PREMIUM }

/**
 * What each plan may use (owner decision, 2026-10-13): the free plan gets location by email only;
 * the paid plan gets everything — photos from both cameras, audio, Telegram, the countdown, live
 * tracking and remote commands. Pure, so the split is unit-tested and every enforcement point asks
 * the same question.
 */
object TierGate {
    fun allowsEmail(tier: Tier): Boolean = true
    fun allowsLocation(tier: Tier): Boolean = true
    fun allowsPhoto(tier: Tier): Boolean = tier == Tier.PREMIUM
    fun allowsBackCamera(tier: Tier): Boolean = tier == Tier.PREMIUM
    fun allowsAudio(tier: Tier): Boolean = tier == Tier.PREMIUM
    fun allowsTelegram(tier: Tier): Boolean = tier == Tier.PREMIUM
    fun allowsCountdown(tier: Tier): Boolean = tier == Tier.PREMIUM
    fun allowsTracking(tier: Tier): Boolean = tier == Tier.PREMIUM
    fun allowsRemote(tier: Tier): Boolean = tier == Tier.PREMIUM
}
