package com.tech2544.gurukulam.billing

import android.app.Activity
import android.app.Application
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
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.tech2544.gurukulam.data.Plan
import com.tech2544.gurukulam.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play Billing. Ek hi subscription ke andar do base plan:
 *   prepaid (manual, ek baar)  aur  auto-renewing (autopay).
 * Asli verification server (Edge Function) karta hai. App ka flag sirf dikhane ke liye hai.
 */
class BillingManager(private val app: Application, private val repo: Repo) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** "ok", "pending", "cancel", "error:..." */
    val events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())

    private val listener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { p -> scope.launch { handle(p) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> events.tryEmit("cancel")
            else -> events.tryEmit("error:${result.debugMessage}")
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(listener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build()
        )
        .enableAutoServiceReconnection()
        .build()

    private suspend fun ensureConnected(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) {
                    if (cont.isActive) cont.resume(r.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {}
            })
        }
    }

    suspend fun loadProducts(plans: List<Plan>) {
        if (!ensureConnected() || plans.isEmpty()) return
        val list = plans.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it.productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val res = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(list).build())
        products.value = (res.productDetailsList ?: emptyList()).associateBy { it.productId }
    }

    private fun offerFor(plan: Plan, auto: Boolean): ProductDetails.SubscriptionOfferDetails? {
        val pd = products.value[plan.productId] ?: return null
        val wanted = if (auto) plan.basePlanAuto else plan.basePlanPrepaid
        val offers = pd.subscriptionOfferDetails ?: return null
        return offers.firstOrNull { it.basePlanId == wanted && it.offerId == null }
            ?: offers.firstOrNull { it.basePlanId == wanted }
    }

    /** Play Store ka localized price (jaise ₹301.00). Na mile to null. */
    fun priceText(plan: Plan, auto: Boolean): String? =
        offerFor(plan, auto)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    fun launch(activity: Activity, plan: Plan, auto: Boolean) {
        val uid = repo.supa.userId
        val pd = products.value[plan.productId]
        val offer = offerFor(plan, auto)
        if (uid == null || pd == null || offer == null) {
            events.tryEmit("error:Plan not available right now")
            return
        }
        val pp = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd)
            .setOfferToken(offer.offerToken)
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(pp))
            .setObfuscatedAccountId(uid) // server isi se purchase ko user se jodta hai
            .build()
        client.launchBillingFlow(activity, flow)
    }

    private suspend fun handle(p: Purchase) {
        when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                try {
                    repo.verifyPurchase(p.products.first(), p.purchaseToken)
                    events.emit("ok")
                } catch (e: Exception) {
                    events.emit("error:${e.message}")
                }
            }
            Purchase.PurchaseState.PENDING -> events.emit("pending")
            else -> {}
        }
    }

    /** App khulte hi / "Restore" par: jo purchase hua par server tak nahi pahuncha, use dobara bhejo. */
    suspend fun syncExisting(): Boolean {
        if (!ensureConnected()) return false
        val res = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        var any = false
        res.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach {
            try {
                repo.verifyPurchase(it.products.first(), it.purchaseToken)
                any = true
            } catch (_: Exception) {
            }
        }
        return any
    }
}
