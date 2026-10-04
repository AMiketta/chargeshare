package app.chargeshare.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Stand des Stripe-Auszahlungskontos eines Hosts. */
@Serializable
enum class PayoutState {
    /** Der Zahlungsdienst braucht kein Auszahlungskonto (Dev-Modus ohne Stripe); die App blendet den Status aus. */
    @SerialName("not_required") NOT_REQUIRED,

    /** Der Host hat das Onboarding noch nicht begonnen. */
    @SerialName("not_started") NOT_STARTED,

    /** Es gibt ein Konto, aber Stripe hat die Auszahlungen noch nicht freigegeben (Angaben fehlen oder werden geprüft). */
    @SerialName("pending") PENDING,

    /** Das Konto kann Auszahlungen empfangen; die Wallboxes des Hosts sind buchbar. */
    @SerialName("ready") READY,
}

/** `GET /payments/payout-status` und `POST /payments/payout-status/refresh`. */
@Serializable
data class PayoutStatus(val state: PayoutState) {
    /** `true`, wenn der Host noch etwas tun kann („Auszahlungen einrichten“). */
    val needsSetup: Boolean get() = state == PayoutState.NOT_STARTED || state == PayoutState.PENDING
}

/** Deep Links, mit denen der Browser nach dem Stripe-Onboarding zurück in die App springt (Android: Intent-Filter, iOS: URL-Schema). */
object AppDeepLinks {
    const val SCHEME = "chargeshare"

    /** Der Host hat das Stripe-Onboarding im Browser verlassen (fertig oder abgebrochen); die App prüft sofort den Kontostatus. */
    const val ONBOARDING_DONE = "$SCHEME://onboarding-done"

    /** Der Onboarding-Link ist abgelaufen oder wurde schon benutzt; die App holt einen neuen. */
    const val ONBOARDING_REFRESH = "$SCHEME://onboarding-refresh"
}
