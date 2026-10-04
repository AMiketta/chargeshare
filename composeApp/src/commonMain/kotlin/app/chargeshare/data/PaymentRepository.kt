package app.chargeshare.data

import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.model.CustomerSessionResponse
import app.chargeshare.shared.model.PaymentConfig
import app.chargeshare.shared.model.SavedCardsResponse
import app.chargeshare.shared.model.SetupIntentResponse

/** Öffentliche Zahlungskonfiguration des Servers und die gespeicherten Karten des Nutzers. */
interface PaymentRepository {
    /** Lädt die Konfiguration (Publishable Key, Händlerdaten). Erfolgreiche Antworten werden für die Laufzeit gemerkt. */
    suspend fun config(): Outcome<PaymentConfig>

    /** Gespeicherte Karten; `supported = false`, wenn der Server ohne Stripe läuft. */
    suspend fun savedCards(): Outcome<SavedCardsResponse>

    /** Entfernt die gespeicherte Karte [paymentMethodId] bei Stripe. */
    suspend fun removeCard(paymentMethodId: String): Outcome<Unit>

    /** Neuer SetupIntent, um im Kartenformular eine Karte zu speichern. */
    suspend fun createSetupIntent(): Outcome<SetupIntentResponse>

    /** Customer Session, damit das Zahlungsfenster gespeicherte Karten anzeigt und neue merken kann. */
    suspend fun customerSession(): Outcome<CustomerSessionResponse>
}

class ApiPaymentRepository(private val api: ChargeShareApi) : PaymentRepository {
    private var cached: PaymentConfig? = null

    override suspend fun config(): Outcome<PaymentConfig> {
        cached?.let { return Outcome.Success(it) }
        return runCatchingOutcome { api.paymentConfig() }.also { result ->
            if (result is Outcome.Success) cached = result.value
        }
    }

    override suspend fun savedCards(): Outcome<SavedCardsResponse> = runCatchingOutcome { api.savedCards() }

    override suspend fun removeCard(paymentMethodId: String): Outcome<Unit> =
        runCatchingOutcome { api.removeSavedCard(paymentMethodId) }

    override suspend fun createSetupIntent(): Outcome<SetupIntentResponse> = runCatchingOutcome { api.createSetupIntent() }

    override suspend fun customerSession(): Outcome<CustomerSessionResponse> = runCatchingOutcome { api.createCustomerSession() }
}
