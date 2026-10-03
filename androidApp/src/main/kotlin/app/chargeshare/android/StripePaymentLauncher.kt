package app.chargeshare.android

import androidx.activity.ComponentActivity
import app.chargeshare.payment.PaymentLauncher
import app.chargeshare.payment.PaymentMethodChoice
import app.chargeshare.payment.PaymentRequest
import app.chargeshare.payment.PaymentResult
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import kotlinx.coroutines.CompletableDeferred

/**
 * Zahlungsfenster für Android: Stripe-PaymentSheet, bei Auswahl [PaymentMethodChoice.GOOGLE_PAY] mit Google Pay.
 *
 * Das PaymentSheet registriert einen Activity-Result-Launcher und muss deshalb **in `onCreate` vor dem Start** der
 * Activity erzeugt werden (siehe [MainActivity]). Es kann immer nur eine Zahlung gleichzeitig offen sein.
 *
 * Google Pay erscheint nur, wenn das Gerät es anbietet (Google Wallet mit hinterlegter Karte); im Emulator ohne
 * Wallet zeigt das Sheet nur Karteneingabe. Zum Testen ein echtes Gerät verwenden.
 */
class StripePaymentLauncher(activity: ComponentActivity) : PaymentLauncher {
    private val applicationContext = activity.applicationContext
    private var pendingResult: CompletableDeferred<PaymentResult>? = null
    private val paymentSheet = PaymentSheet.Builder(::onSheetResult).build(activity)

    override val supportedMethods: List<PaymentMethodChoice> =
        listOf(PaymentMethodChoice.GOOGLE_PAY, PaymentMethodChoice.CARD)

    /** Muss auf dem Main-Thread aufgerufen werden (der Compose-Scope der UI tut das). */
    override suspend fun pay(request: PaymentRequest): PaymentResult {
        val publishableKey = request.config.publishableKey
            ?: return PaymentResult.Failed(reason = "Server liefert keinen Stripe Publishable Key")
        if (pendingResult != null) return PaymentResult.Failed(reason = "Es läuft bereits eine Zahlung")

        val result = CompletableDeferred<PaymentResult>()
        pendingResult = result
        try {
            PaymentConfiguration.init(applicationContext, publishableKey)
            paymentSheet.presentWithPaymentIntent(request.clientSecret, sheetConfiguration(request))
            return result.await()
        } finally {
            pendingResult = null
        }
    }

    private fun sheetConfiguration(request: PaymentRequest): PaymentSheet.Configuration {
        val config = request.config
        val builder = PaymentSheet.Configuration.Builder(merchantDisplayName = config.merchantDisplayName)
        // Ohne googlePay(...) zeigt das PaymentSheet keine Google-Pay-Schaltfläche – so wirkt die Auswahl „Karte und weitere“.
        if (request.method == PaymentMethodChoice.GOOGLE_PAY) {
            val environment = if (config.googlePayTestEnvironment) {
                PaymentSheet.GooglePayConfiguration.Environment.Test
            } else {
                PaymentSheet.GooglePayConfiguration.Environment.Production
            }
            builder.googlePay(
                PaymentSheet.GooglePayConfiguration(
                    environment = environment,
                    countryCode = config.merchantCountryCode,
                    currencyCode = config.currency.uppercase(),
                ),
            )
        }
        return builder.build()
    }

    private fun onSheetResult(result: PaymentSheetResult) {
        val mapped = when (result) {
            is PaymentSheetResult.Completed -> PaymentResult.Completed
            is PaymentSheetResult.Canceled -> PaymentResult.Canceled
            is PaymentSheetResult.Failed -> PaymentResult.Failed(reason = result.error.message)
        }
        pendingResult?.complete(mapped)
    }
}
