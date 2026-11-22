package com.crtc.books

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val SKU_PRO = "pro_upgrade"
const val PRO_PRICE = "$2.99"

class BillingManager(private val ctx: Context) : PurchasesUpdatedListener {

    private val prefs = ctx.getSharedPreferences("billing", Context.MODE_PRIVATE)

    private val _isPro = MutableStateFlow(prefs.getBoolean("is_pro", false))
    val isPro: StateFlow<Boolean> = _isPro

    private val _billingReady = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = _billingReady

    private var productDetails: ProductDetails? = null

    private val client = BillingClient.newBuilder(ctx)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingReady.value = true
                    queryProductDetails()
                    queryExistingPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                _billingReady.value = false
            }
        })
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SKU_PRO)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )).build()
        client.queryProductDetailsAsync(params) { _, details ->
            productDetails = details.firstOrNull()
        }
    }

    private fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPro = purchases.any { p ->
                    p.products.contains(SKU_PRO) &&
                    p.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (hasPro) grantPro()
            }
        }
    }

    fun launchBillingFlow(activity: Activity) {
        val details = productDetails ?: return
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            )).build()
        client.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.products.contains(SKU_PRO) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    grantPro()
                    if (!purchase.isAcknowledged) {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken).build()
                        client.acknowledgePurchase(ackParams) {}
                    }
                }
            }
        }
    }

    private fun grantPro() {
        prefs.edit().putBoolean("is_pro", true).apply()
        _isPro.value = true
    }

    fun destroy() = client.endConnection()
}
