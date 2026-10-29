package com.shieldra.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** The ids the owner creates once in Play Console (Monetize → Subscriptions). */
object ProCatalog {
    const val PRODUCT_ID = "shieldra_pro"
    const val PLAN_MONTHLY = "pro-monthly"
    const val PLAN_YEARLY = "pro-yearly"

    /** The one-time purchase (Monetize → In-app products): Pro for good, no renewal. */
    const val LIFETIME_PRODUCT_ID = "shieldra_pro_lifetime"

    /** The id the screen uses for the lifetime choice; it is not a Play base plan. */
    const val PLAN_LIFETIME = "pro-lifetime"
}

enum class ProPeriod { MONTHLY, YEARLY, LIFETIME, OTHER }

/** One plan the screen can sell: the price text comes from Google Play, never from this app. */
data class ProPlan(
    val basePlanId: String,
    val period: ProPeriod,
    /** The subscription offer to buy; empty for the one-time lifetime purchase. */
    val offerToken: String,
    /** The recurring price as Play formats it for this user's country and currency. */
    val recurringPrice: String,
    /** Length of the free trial this user is offered on this plan, or null when there is none. */
    val trialDays: Int?,
)

/** Why a purchase cannot be made right now — each one a fact, each one worded for the owner. */
enum class ProUnavailable { NOT_FROM_PLAY, BILLING_UNSUPPORTED, SERVICE_UNAVAILABLE, PRODUCT_NOT_FOUND }

sealed interface ProAvailability {
    data object Connecting : ProAvailability
    data object Ready : ProAvailability
    data class Unavailable(val reason: ProUnavailable) : ProAvailability
}

data class ProState(
    val availability: ProAvailability = ProAvailability.Connecting,
    val plans: List<ProPlan> = emptyList(),
    /** True only while Google Play reports an owned Pro subscription or lifetime purchase. */
    val entitled: Boolean = false,
    /** A purchase Play has accepted but not completed yet (for example a cash payment). */
    val purchasePending: Boolean = false,
)

/**
 * What the UI needs from the store, and nothing about how it talks to Google Play: the screens never
 * touch the billing library (the app layer stays free of it, phase-3 gate).
 */
interface ProStore {
    val state: StateFlow<ProState>
    fun start()
    fun refresh()
    fun purchase(activity: Activity, basePlanId: String)
}
