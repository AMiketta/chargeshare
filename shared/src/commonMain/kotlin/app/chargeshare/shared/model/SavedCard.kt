package app.chargeshare.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Eine bei Stripe gespeicherte Karte. Die App sieht nie die Kartennummer, nur Marke und letzte vier Ziffern. */
@Serializable
data class SavedCard(
    /** Stripe-PaymentMethod-ID (`pm_…`). */
    val id: String,
    /** Kartenmarke in Kleinbuchstaben wie bei Stripe, z. B. `visa`, `mastercard`. */
    val brand: String,
    val last4: String,
    @SerialName("exp_month") val expMonth: Int,
    @SerialName("exp_year") val expYear: Int,
)

/** `GET /payments/methods`: die gespeicherten Karten des Nutzers. [supported] ist `false` ohne Stripe (Dev-Modus). */
@Serializable
data class SavedCardsResponse(val supported: Boolean, val cards: List<SavedCard> = emptyList())

/** `POST /payments/setup-intent`: `client_secret` zum Speichern einer Karte im PaymentSheet (ohne Zahlung). */
@Serializable
data class SetupIntentResponse(
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("customer_id") val customerId: String,
)

/** `POST /payments/customer-session`: lässt das PaymentSheet gespeicherte Karten anzeigen und neue merken. */
@Serializable
data class CustomerSessionResponse(
    @SerialName("customer_id") val customerId: String,
    @SerialName("customer_session_client_secret") val customerSessionClientSecret: String,
)
