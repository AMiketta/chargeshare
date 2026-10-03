package app.chargeshare.data

import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.model.PaymentConfig

/** Öffentliche Zahlungskonfiguration des Servers. */
interface PaymentRepository {
    /** Lädt die Konfiguration (Publishable Key, Händlerdaten). Erfolgreiche Antworten werden für die Laufzeit gemerkt. */
    suspend fun config(): Outcome<PaymentConfig>
}

class ApiPaymentRepository(private val api: ChargeShareApi) : PaymentRepository {
    private var cached: PaymentConfig? = null

    override suspend fun config(): Outcome<PaymentConfig> {
        cached?.let { return Outcome.Success(it) }
        return runCatchingOutcome { api.paymentConfig() }.also { result ->
            if (result is Outcome.Success) cached = result.value
        }
    }
}
