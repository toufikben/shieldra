package com.shieldra.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.shieldra.protection.ProtectionSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Google Play subscription behind the Pro plan (amendment A-40) — the only file that talks to the
 * billing library. It lists the plans Play offers this user (prices are Play's own text), launches
 * the purchase, acknowledges it, and keeps the stored plan in step with what Play says the owner owns.
 *
 * There is no backend, so ownership is Play's on-device answer; the plan only changes through
 * [EntitlementPolicy], which never downgrades on a failed query or on a build Play did not install.
 */
class PlayProStore(context: Context) : ProStore {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val installedFromPlay = InstallSource.isGooglePlay(app)

    private val mutableState = MutableStateFlow(ProState())
    override val state: StateFlow<ProState> = mutableState.asStateFlow()

    private var client: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var lifetimeDetails: ProductDetails? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            scope.launch { applyPurchases(purchases.orEmpty(), queryOk = true) }
        }
        // A cancelled or failed flow changes nothing: the screen simply stays as it was.
    }

    override fun start() {
        if (client != null) return
        if (!installedFromPlay) {
            // A sideloaded build has no Play to ask: say so plainly and keep the stored plan.
            mutableState.update { it.copy(availability = ProAvailability.Unavailable(ProUnavailable.NOT_FROM_PLAY)) }
            return
        }
        val built = BillingClient.newBuilder(app)
            .setListener(purchasesListener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client = built
        built.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { loadEverything() }
                } else {
                    mutableState.update { it.copy(availability = ProAvailability.Unavailable(reasonFor(result.responseCode))) }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Auto service reconnection is on: Play re-establishes the link on the next call.
            }
        })
    }

    override fun refresh() {
        if (client == null) start() else scope.launch { loadEverything() }
    }

    override fun purchase(activity: Activity, basePlanId: String) {
        val c = client ?: return
        val plan = mutableState.value.plans.firstOrNull { it.basePlanId == basePlanId } ?: return
        val product = if (plan.period == ProPeriod.LIFETIME) {
            // A one-time product has no offer token.
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(lifetimeDetails ?: return)
                .build()
        } else {
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails ?: return)
                .setOfferToken(plan.offerToken)
                .build()
        }
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()
        c.launchBillingFlow(activity, flow)
    }

    private suspend fun loadEverything() {
        refreshPurchases()
        loadProducts()
    }

    private suspend fun loadProducts() {
        val c = client ?: return
        // A query holds one product type, so the subscription and the one-time product are asked apart.
        val subsResult = c.queryProductDetails(productQuery(ProCatalog.PRODUCT_ID, BillingClient.ProductType.SUBS))
        val lifetimeResult = c.queryProductDetails(
            productQuery(ProCatalog.LIFETIME_PRODUCT_ID, BillingClient.ProductType.INAPP),
        )
        val subs = subsResult.productDetailsList?.firstOrNull()
            ?.takeIf { subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK }
        val lifetime = lifetimeResult.productDetailsList?.firstOrNull()
            ?.takeIf { lifetimeResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK }
        productDetails = subs
        lifetimeDetails = lifetime
        val offers = subs?.subscriptionOfferDetails.orEmpty().map { offer ->
            RawOffer(
                basePlanId = offer.basePlanId,
                offerId = offer.offerId,
                offerToken = offer.offerToken,
                phases = offer.pricingPhases.pricingPhaseList.map {
                    RawPhase(it.priceAmountMicros, it.formattedPrice, it.billingPeriod)
                },
            )
        }
        val lifetimePlan = lifetime?.oneTimePurchaseOfferDetails?.let {
            ProPlan(
                basePlanId = ProCatalog.PLAN_LIFETIME,
                period = ProPeriod.LIFETIME,
                offerToken = "",
                recurringPrice = it.formattedPrice,
                trialDays = null,
            )
        }
        val plans = PlanChooser.plans(offers) + listOfNotNull(lifetimePlan)
        if (plans.isEmpty()) {
            Log.w(TAG, "Pro not available: ${subsResult.billingResult.debugMessage} / ${lifetimeResult.billingResult.debugMessage}")
        }
        mutableState.update {
            it.copy(
                availability = if (plans.isEmpty()) {
                    ProAvailability.Unavailable(ProUnavailable.PRODUCT_NOT_FOUND)
                } else {
                    ProAvailability.Ready
                },
                plans = plans,
            )
        }
    }

    private fun productQuery(productId: String, type: String): QueryProductDetailsParams =
        QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(type)
                        .build(),
                ),
            )
            .build()

    private suspend fun refreshPurchases() {
        val c = client ?: return
        val subs = c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        val once = c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        applyPurchases(
            subs.purchasesList + once.purchasesList,
            queryOk = subs.billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                once.billingResult.responseCode == BillingClient.BillingResponseCode.OK,
        )
    }

    private suspend fun applyPurchases(purchases: List<Purchase>, queryOk: Boolean) {
        val pro = purchases.filter {
            ProCatalog.PRODUCT_ID in it.products || ProCatalog.LIFETIME_PRODUCT_ID in it.products
        }
        val owned = pro.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = pro.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        // Play refunds a purchase that is not acknowledged within three days, so acknowledge at once.
        owned.filterNot { it.isAcknowledged }.forEach { purchase ->
            runCatching {
                client?.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
                )
            }.onFailure { Log.w(TAG, "acknowledge failed: ${it.message}") }
        }
        val store = ProtectionSettingsStore(app)
        val resolved = EntitlementPolicy.resolve(
            ownsPro = owned.isNotEmpty(),
            queryOk = queryOk,
            installedFromPlay = installedFromPlay,
            stored = store.load().tier,
        )
        if (resolved != store.load().tier) store.setTier(resolved)
        mutableState.update { it.copy(entitled = resolved == Tier.PREMIUM && owned.isNotEmpty(), purchasePending = pending) }
    }

    private fun reasonFor(code: Int): ProUnavailable = when (code) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> ProUnavailable.BILLING_UNSUPPORTED
        else -> ProUnavailable.SERVICE_UNAVAILABLE
    }

    private companion object {
        const val TAG = "PlayProStore"
    }
}
