package app.chargeshare.payment

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Anfrage an die Swift-Brücke. Bewusst nur aus Strings und Booleans, damit Swift sie ohne Kotlin-Sonderfälle lesen kann.
 *
 * @property useApplePay `true`, wenn das PaymentSheet Apple Pay anbieten soll (gewählt und Merchant-ID vorhanden)
 */
class IosPaymentRequest(
    val clientSecret: String,
    val publishableKey: String,
    val merchantDisplayName: String,
    val merchantCountryCode: String,
    val applePayMerchantId: String?,
    val useApplePay: Boolean,
)

/** Rückmeldung der Swift-Brücke; genau einer der drei Aufrufe erfolgt pro Zahlung, auf dem Main-Thread. */
interface IosPaymentCallback {
    fun onCompleted()
    fun onCanceled()

    /** @param message technische Ursache nur für Logs */
    fun onFailed(message: String)
}

/**
 * Wird in Swift implementiert (`StripePaymentBridge` in `iosApp`) und kapselt das Stripe-iOS-SDK (`StripePaymentSheet`).
 * Kotlin kennt das SDK nicht – so bleibt `composeApp` frei von Swift-Package-Abhängigkeiten.
 */
interface IosPaymentBridge {
    /** `true`, wenn das Gerät Apple Pay mit mindestens einer Karte anbieten kann. */
    fun canUseApplePay(): Boolean

    /** Zeigt das Stripe-PaymentSheet über dem obersten ViewController und meldet das Ergebnis über [callback]. */
    fun presentPaymentSheet(request: IosPaymentRequest, callback: IosPaymentCallback)
}

/** [PaymentLauncher] für iOS: reicht die Zahlung an die Swift-Brücke weiter und wartet auf deren Rückmeldung. */
class IosPaymentLauncher(private val bridge: IosPaymentBridge) : PaymentLauncher {

    override val supportedMethods: List<PaymentMethodChoice>
        get() = buildList {
            if (bridge.canUseApplePay()) add(PaymentMethodChoice.APPLE_PAY)
            add(PaymentMethodChoice.CARD)
        }

    override suspend fun pay(request: PaymentRequest): PaymentResult {
        val publishableKey = request.config.publishableKey
            ?: return PaymentResult.Failed(reason = "Server liefert keinen Stripe Publishable Key")
        val bridgeRequest = IosPaymentRequest(
            clientSecret = request.clientSecret,
            publishableKey = publishableKey,
            merchantDisplayName = request.config.merchantDisplayName,
            merchantCountryCode = request.config.merchantCountryCode,
            applePayMerchantId = request.config.applePayMerchantId,
            useApplePay = request.method == PaymentMethodChoice.APPLE_PAY && request.config.applePayMerchantId != null,
        )
        return suspendCancellableCoroutine { continuation ->
            bridge.presentPaymentSheet(
                bridgeRequest,
                object : IosPaymentCallback {
                    override fun onCompleted() = resumeOnce(PaymentResult.Completed)
                    override fun onCanceled() = resumeOnce(PaymentResult.Canceled)
                    override fun onFailed(message: String) = resumeOnce(PaymentResult.Failed(reason = message))

                    // Doppelte Rückmeldungen der Brücke dürfen die Coroutine nicht ein zweites Mal fortsetzen.
                    private fun resumeOnce(result: PaymentResult) {
                        if (continuation.isActive) continuation.resume(result)
                    }
                },
            )
        }
    }
}
