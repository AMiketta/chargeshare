package app.chargeshare.server.services

import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.util.badGateway
import app.chargeshare.server.util.notFound
import app.chargeshare.server.util.serviceUnavailable
import app.chargeshare.shared.model.CustomerSessionResponse
import app.chargeshare.shared.model.SavedCard
import app.chargeshare.shared.model.SavedCardsResponse
import app.chargeshare.shared.model.SetupIntentResponse
import com.stripe.exception.ApiConnectionException
import com.stripe.exception.RateLimitException
import com.stripe.exception.StripeException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Gespeicherte Zahlungsmethoden (Karten) über Stripe: auflisten, entfernen, neue Karte per SetupIntent speichern und
 * Customer Session für das PaymentSheet. Kartennummern berühren den Server nie. Stripe-Fehler werden in lesbare deutsche
 * Meldungen übersetzt; Rohtexte und Schlüssel werden weder durchgereicht noch geloggt.
 */
class SavedCardService(private val payments: PaymentProvider, private val customers: CustomerService) {
    private val log = LoggerFactory.getLogger(SavedCardService::class.java)

    suspend fun list(userId: Uuid): SavedCardsResponse {
        if (!payments.supportsSavedCards) return SavedCardsResponse(supported = false)
        val customerId = customers.ensure(userId) ?: return SavedCardsResponse(supported = true)
        val cards = stripeCall { payments.listCards(customerId) }
        return SavedCardsResponse(supported = true, cards = cards.map { SavedCard(it.id, it.brand, it.last4, it.expMonth, it.expYear) })
    }

    /** @throws app.chargeshare.server.util.AppException `404`, wenn die Karte nicht existiert oder nicht dem Nutzer gehört */
    suspend fun remove(userId: Uuid, paymentMethodId: String) {
        if (!payments.supportsSavedCards) throw notFound("payment method not found")
        val customerId = customers.ensure(userId) ?: throw notFound("payment method not found")
        val removed = stripeCall { payments.detachCard(customerId, paymentMethodId) }
        if (!removed) throw notFound("payment method not found")
    }

    suspend fun createSetupIntent(userId: Uuid): SetupIntentResponse {
        val customerId = requireCustomer(userId)
        return SetupIntentResponse(stripeCall { payments.createSetupIntent(customerId) }, customerId)
    }

    suspend fun createCustomerSession(userId: Uuid): CustomerSessionResponse {
        val customerId = requireCustomer(userId)
        val secret = stripeCall { payments.createCustomerSession(customerId) } ?: throw serviceUnavailable(UNAVAILABLE_MESSAGE)
        return CustomerSessionResponse(customerId, secret)
    }

    private suspend fun requireCustomer(userId: Uuid): String {
        if (!payments.supportsSavedCards) throw serviceUnavailable(UNAVAILABLE_MESSAGE)
        return customers.ensure(userId) ?: throw serviceUnavailable(UNAVAILABLE_MESSAGE)
    }

    private suspend fun <T> stripeCall(block: () -> T): T =
        try {
            withContext(Dispatchers.IO) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: StripeException) {
            log.warn("stripe card call failed: {} code={} status={} requestId={}", e.javaClass.simpleName, e.code, e.statusCode, e.requestId)
            throw if (e is ApiConnectionException || e is RateLimitException) serviceUnavailable(UNREACHABLE_MESSAGE) else badGateway(FAILED_MESSAGE)
        }

    private companion object {
        const val UNAVAILABLE_MESSAGE = "Gespeicherte Karten sind nicht verfügbar."
        const val UNREACHABLE_MESSAGE = "Der Zahlungsanbieter ist gerade nicht erreichbar. Bitte später erneut versuchen."
        const val FAILED_MESSAGE = "Die Karten konnten nicht geladen werden. Bitte später erneut versuchen."
    }
}
