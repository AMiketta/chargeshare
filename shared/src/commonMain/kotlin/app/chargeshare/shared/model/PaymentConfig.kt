package app.chargeshare.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Welcher Zahlungsdienst hinter dem Server steht (`GET /payments/config`). */
@Serializable
enum class PaymentProviderKind {
    /** Entwicklungsmodus ohne `STRIPE_SECRET_KEY`: Buchungen gelten sofort als bezahlt, die App zeigt kein Zahlungsfenster. */
    @SerialName("fake") FAKE,

    /** Echte Zahlung über Stripe: die App bestätigt den PaymentIntent mit dem `client_secret` der Buchung. */
    @SerialName("stripe") STRIPE,
}

/**
 * Öffentliche Zahlungskonfiguration für die Apps (`GET /payments/config`).
 * Enthält bewusst **keine Secrets**: der Publishable Key ist für den Einbau in Apps gedacht.
 */
@Serializable
data class PaymentConfig(
    val provider: PaymentProviderKind,
    /** Stripe-Publishable-Key (`pk_...`); `null`, wenn der Server keinen konfiguriert hat. */
    @SerialName("publishable_key") val publishableKey: String? = null,
    @SerialName("merchant_display_name") val merchantDisplayName: String,
    /** ISO-3166-Land des Händlers, z. B. `DE` (Pflichtangabe für Google Pay und Apple Pay). */
    @SerialName("merchant_country_code") val merchantCountryCode: String,
    /** ISO-4217-Währung in Kleinbuchstaben, z. B. `eur`. */
    val currency: String,
    /** `true`, wenn Google Pay die Testumgebung nutzen soll (Test-Karten, keine echten Abbuchungen). */
    @SerialName("google_pay_test_environment") val googlePayTestEnvironment: Boolean,
    /** Apple-Pay-Merchant-ID (`merchant.…`); `null`, solange iOS-Apple-Pay nicht eingerichtet ist. */
    @SerialName("apple_pay_merchant_id") val applePayMerchantId: String? = null,
) {
    /** `true`, wenn die App das Stripe-Zahlungsfenster braucht und der Server dafür alles geliefert hat. */
    val requiresClientPayment: Boolean
        get() = provider == PaymentProviderKind.STRIPE
}
