package br.com.celularsaudavel.data

import android.app.Activity
import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Um plano de assinatura como a Play informa (preço já formatado na moeda do usuário). */
data class PlanOffer(
    val basePlanId: String,
    val offerToken: String,
    val price: String,
    val period: String,
    val freeTrialDays: Int?,
)

data class BillingUi(
    val ready: Boolean = false,
    val premium: Boolean = false,
    val plans: List<PlanOffer> = emptyList(),
    val message: String? = null,
)

/**
 * Assinatura "Celular Saudável Premium" pela Google Play.
 * No Play Console crie a assinatura com o ID [PRODUCT_ID] e dois planos básicos:
 * "mensal" (1 mês) e "anual" (1 ano). A oferta de teste grátis é opcional.
 */
class BillingRepository(context: Context) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "premium"
        const val PLAN_MONTHLY = "mensal"
        const val PLAN_YEARLY = "anual"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("billing", Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(BillingUi(premium = prefs.getBoolean("premium", false)))
    val ui: StateFlow<BillingUi> = _ui

    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        if (client.isReady) {
            refresh(); return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _ui.value = _ui.value.copy(ready = true, message = null)
                    refresh()
                } else {
                    _ui.value = _ui.value.copy(ready = false, message = "Google Play indisponível (${result.debugMessage})")
                }
            }

            override fun onBillingServiceDisconnected() {
                _ui.value = _ui.value.copy(ready = false)
            }
        })
    }

    private fun refresh() {
        // Assinaturas ativas: vale para qualquer celular com a mesma conta Google.
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { _, purchases -> handle(purchases, fromQuery = true) }

        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
        ).build()
        client.queryProductDetailsAsync(params) { _, list ->
            val d = list.firstOrNull() ?: return@queryProductDetailsAsync
            details = d
            val plans = (d.subscriptionOfferDetails ?: emptyList())
                .groupBy { it.basePlanId }
                .mapNotNull { (basePlan, offers) ->
                    // Prefere a oferta com teste grátis, se existir.
                    val offer = offers.maxByOrNull { it.pricingPhases.pricingPhaseList.size } ?: return@mapNotNull null
                    val phases = offer.pricingPhases.pricingPhaseList
                    val paid = phases.lastOrNull() ?: return@mapNotNull null
                    val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
                    PlanOffer(
                        basePlanId = basePlan,
                        offerToken = offer.offerToken,
                        price = paid.formattedPrice,
                        period = paid.billingPeriod,
                        freeTrialDays = trial?.billingPeriod?.let { periodDays(it) }
                    )
                }
                .sortedBy { if (it.basePlanId == PLAN_YEARLY) 0 else 1 }
            _ui.value = _ui.value.copy(plans = plans)
        }
    }

    private fun periodDays(iso: String): Int? {
        // P7D, P1W, P1M ...
        val n = iso.filter { it.isDigit() }.toIntOrNull() ?: return null
        return when {
            iso.endsWith("D") -> n
            iso.endsWith("W") -> n * 7
            iso.endsWith("M") -> n * 30
            else -> null
        }
    }

    fun buy(activity: Activity, plan: PlanOffer) {
        val d = details ?: run {
            _ui.value = _ui.value.copy(message = "Planos ainda carregando. Tente de novo em instantes.")
            return
        }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(d)
                    .setOfferToken(plan.offerToken)
                    .build()
            )
        ).build()
        client.launchBillingFlow(activity, params)
    }

    fun restore() {
        _ui.value = _ui.value.copy(message = null)
        start()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases ?: emptyList(), fromQuery = false)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> _ui.value = _ui.value.copy(message = "A compra não foi concluída (${result.debugMessage}).")
        }
    }

    private fun handle(purchases: List<Purchase>, fromQuery: Boolean) {
        val active = purchases.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && PRODUCT_ID in it.products
        }
        active.filter { !it.isAcknowledged }.forEach { p ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
            ) { }
        }
        val premium = active.isNotEmpty()
        // Uma consulta vazia significa assinatura cancelada/vencida; uma compra nova só adiciona.
        if (fromQuery || premium) {
            prefs.edit().putBoolean("premium", premium).apply()
            _ui.value = _ui.value.copy(premium = premium, message = if (premium && !fromQuery) "Premium ativado. Obrigado! 💚" else _ui.value.message)
        }
    }
}
