package com.wiggyserver.immichwallpaper.support

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.wiggyserver.immichwallpaper.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

fun createSupport(activity: ComponentActivity): Support = TipJar(activity)

/**
 * One-time tips through Google Play Billing. Tips are consumable products that unlock nothing:
 * create `tip_small`, `tip_medium` and `tip_large` in Play Console.
 * Outside a Play-installed build the products can't load, and the section says so.
 */
class TipJar(private val activity: ComponentActivity) : Support {
    data class State(val tips: List<ProductDetails> = emptyList(), val message: String? = null, val loading: Boolean = true)

    private val state = MutableStateFlow(State())

    private val client: BillingClient = BillingClient.newBuilder(activity)
        .setListener { result, purchases -> onPurchases(result, purchases.orEmpty()) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadTips()
                    consumeLeftovers()
                } else {
                    unavailable("setup ${result.responseCode}")
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun loadTips() {
        val products = PRODUCT_IDS.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, details ->
            val tips = details.productDetailsList.sortedBy { PRODUCT_IDS.indexOf(it.productId) }
            if (result.responseCode == BillingClient.BillingResponseCode.OK && tips.isNotEmpty()) {
                state.update { it.copy(tips = tips, loading = false) }
            } else {
                unavailable("products ${result.responseCode} found=${tips.size}")
            }
        }
    }

    /** A tip interrupted before it was consumed (app killed mid-purchase) is consumed now, so it can be given again. */
    private fun consumeLeftovers() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { _, purchases ->
            purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach(::consume)
        }
    }

    private fun onPurchases(result: BillingResult, purchases: List<Purchase>) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.forEach { p ->
                if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    consume(p)
                    state.update { it.copy(message = "Thank you so much for the tip! ❤") }
                } else {
                    state.update { it.copy(message = "Thanks! Your tip is pending and will go through shortly.") }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> state.update { it.copy(message = "The tip didn't go through (code ${result.responseCode}).") }
        }
    }

    private fun consume(p: Purchase) {
        client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r, _ ->
            AppLog.log("TIP", "consumed code=${r.responseCode}")
        }
    }

    private fun unavailable(reason: String) {
        AppLog.log("TIP", "unavailable: $reason")
        state.update { it.copy(loading = false, message = "Tips aren't available right now. They work in the version installed from Google Play.") }
    }

    private fun buy(product: ProductDetails) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()))
            .build()
        client.launchBillingFlow(activity, params)
    }

    @Composable
    override fun Section() {
        val s by state.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "This app is free, with no ads. If it brightens your day, you can leave a tip. It unlocks nothing; it just says thanks.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (s.tips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.tips.forEach { tip ->
                        FilledTonalButton(onClick = { buy(tip) }) { Text(tip.oneTimePurchaseOfferDetails?.formattedPrice ?: tip.name) }
                    }
                }
            }
            s.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }

    override fun close() = client.endConnection()

    private companion object {
        val PRODUCT_IDS = listOf("tip_small", "tip_medium", "tip_large")
    }
}
