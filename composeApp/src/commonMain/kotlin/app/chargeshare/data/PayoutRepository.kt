package app.chargeshare.data

import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.model.PayoutStatus

/** Stripe-Auszahlungskonto des Hosts: Status anzeigen, Onboarding starten, nach der Rückkehr sofort synchronisieren. */
interface PayoutRepository {
    /** Gespeicherter Status (ohne Stripe-Abfrage). */
    suspend fun status(): Outcome<PayoutStatus>

    /** Fragt Stripe jetzt nach dem Kontostatus; ein Stripe-Ausfall lässt den gespeicherten Stand stehen. */
    suspend fun refresh(): Outcome<PayoutStatus>

    /** Liefert den Onboarding-Link, den die App im Browser öffnet. */
    suspend fun startOnboarding(): Outcome<String>
}

class ApiPayoutRepository(private val api: ChargeShareApi) : PayoutRepository {
    override suspend fun status(): Outcome<PayoutStatus> = runCatchingOutcome { api.payoutStatus() }

    override suspend fun refresh(): Outcome<PayoutStatus> = runCatchingOutcome { api.refreshPayoutStatus() }

    override suspend fun startOnboarding(): Outcome<String> = runCatchingOutcome { api.startPayoutOnboarding().url }
}
