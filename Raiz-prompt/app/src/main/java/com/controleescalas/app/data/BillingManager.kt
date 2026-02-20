package com.controleescalas.app.data

import android.app.Activity
import com.android.billingclient.api.*
import com.controleescalas.app.data.models.PlanoTipo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gerenciador de assinaturas Google Play Billing
 * 
 * Antes de usar:
 * 1. Criar produtos no Play Console (Assinaturas)
 * 2. IDs: plano_pro_mensal, plano_multi_mensal, plano_multi_pro_mensal
 * 3. Testar com conta de licença de teste
 */
class BillingManager(private val activity: Activity) : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    
    private val _connectionState = MutableStateFlow<BillingConnectionState>(BillingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()
    
    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()
    
    private val _purchaseResult = MutableStateFlow<PurchaseResult?>(null)
    val purchaseResult: StateFlow<PurchaseResult?> = _purchaseResult.asStateFlow()

    sealed class BillingConnectionState {
        object DISCONNECTED : BillingConnectionState()
        object CONNECTING : BillingConnectionState()
        object CONNECTED : BillingConnectionState()
        data class ERROR(val message: String) : BillingConnectionState()
    }

    sealed class PurchaseResult {
        object SUCCESS : PurchaseResult()
        data class ERROR(val message: String) : PurchaseResult()
        object CANCELLED : PurchaseResult()
    }

    private val productIds = listOf(
        PlanoTipo.PRO.productIdMensal,
        PlanoTipo.PRO.productIdAnual,
        PlanoTipo.MULTI.productIdMensal,
        PlanoTipo.MULTI.productIdAnual,
        PlanoTipo.MULTI_PRO.productIdMensal,
        PlanoTipo.MULTI_PRO.productIdAnual
    ).filter { it.isNotEmpty() }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        
        startConnection()
    }

    private fun startConnection() {
        if (billingClient == null) return
        _connectionState.value = BillingConnectionState.CONNECTING
        
        billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connectionState.value = BillingConnectionState.CONNECTED
                    queryProducts()
                } else {
                    _connectionState.value = BillingConnectionState.ERROR(
                        "Erro ao conectar: ${result.debugMessage}"
                    )
                }
            }
            override fun onBillingServiceDisconnected() {
                _connectionState.value = BillingConnectionState.DISCONNECTED
            }
        })
    }

    private fun queryProducts() {
        if (productIds.isEmpty()) return
        if (billingClient == null) return
        
        val productList = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        billingClient!!.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = productDetailsList ?: emptyList()
            }
        }
    }

    /**
     * Inicia fluxo de compra de assinatura
     */
    fun launchPurchaseFlow(productDetails: ProductDetails) {
        val subscriptionOfferDetails = productDetails.subscriptionOfferDetails?.firstOrNull()
            ?: return
        
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(subscriptionOfferDetails.offerToken)
                .build()
        )
        
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        
        billingClient!!.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        _purchaseResult.value = PurchaseResult.SUCCESS
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseResult.value = PurchaseResult.CANCELLED
            }
            else -> {
                _purchaseResult.value = PurchaseResult.ERROR(result.debugMessage ?: "Erro desconhecido")
            }
        }
    }

    fun clearPurchaseResult() {
        _purchaseResult.value = null
    }

    fun endConnection() {
        billingClient?.endConnection()
        billingClient = null
        _connectionState.value = BillingConnectionState.DISCONNECTED
    }
}
