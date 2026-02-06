package com.fencing.spacedrepetition.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play Billing for in-app donations
 */
class BillingManager(private val context: Context) {

    private val TAG = "BillingManager"

    // Available donation tiers
    companion object {
        const val DONATION_SMALL = "donation_small"
        const val DONATION_MEDIUM = "donation_medium"
        const val DONATION_LARGE = "donation_large"

        val DONATION_PRODUCT_IDS = listOf(
            DONATION_SMALL,
            DONATION_MEDIUM,
            DONATION_LARGE
        )
    }

    private var billingClient: BillingClient? = null

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Disconnected)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    private val _donationProducts = MutableStateFlow<List<ProductDetails>>(emptyList())
    val donationProducts: StateFlow<List<ProductDetails>> = _donationProducts.asStateFlow()

    private val _purchaseResult = MutableStateFlow<PurchaseResult?>(null)
    val purchaseResult: StateFlow<PurchaseResult?> = _purchaseResult.asStateFlow()

    sealed class BillingState {
        object Disconnected : BillingState()
        object Connecting : BillingState()
        object Connected : BillingState()
        data class Error(val message: String) : BillingState()
    }

    sealed class PurchaseResult {
        object Success : PurchaseResult()
        object Cancelled : PurchaseResult()
        data class Error(val message: String) : PurchaseResult()
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "Purchase cancelled by user")
                _purchaseResult.value = PurchaseResult.Cancelled
            }
            else -> {
                Log.e(TAG, "Purchase error: ${billingResult.debugMessage}")
                _purchaseResult.value = PurchaseResult.Error(
                    billingResult.debugMessage ?: "Unknown error"
                )
            }
        }
    }

    fun initialize() {
        _billingState.value = BillingState.Connecting

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    _billingState.value = BillingState.Connected
                    queryProducts()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _billingState.value = BillingState.Error(
                        billingResult.debugMessage ?: "Connection failed"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing service disconnected")
                _billingState.value = BillingState.Disconnected
            }
        })
    }

    private fun queryProducts() {
        val productList = DONATION_PRODUCT_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _donationProducts.value = productDetailsList
                Log.d(TAG, "Products loaded: ${productDetailsList.size}")
            } else {
                Log.e(TAG, "Failed to query products: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchDonationFlow(activity: Activity, productDetails: ProductDetails) {
        _purchaseResult.value = null // Reset previous result

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // For donations (consumable products), we need to acknowledge and consume
            if (!purchase.isAcknowledged) {
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient?.consumeAsync(consumeParams) { billingResult, _ ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Donation consumed successfully")
                        _purchaseResult.value = PurchaseResult.Success
                    } else {
                        Log.e(TAG, "Failed to consume donation: ${billingResult.debugMessage}")
                        _purchaseResult.value = PurchaseResult.Error(
                            "Failed to complete donation"
                        )
                    }
                }
            }
        }
    }

    fun clearPurchaseResult() {
        _purchaseResult.value = null
    }

    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
        _billingState.value = BillingState.Disconnected
    }
}
