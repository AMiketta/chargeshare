package app.chargeshare.server.payments

import app.chargeshare.server.config.AppConfig
import com.stripe.StripeClient
import com.stripe.net.Webhook
import com.stripe.param.AccountCreateParams
import com.stripe.param.AccountLinkCreateParams
import com.stripe.param.CustomerCreateParams
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.param.RefundCreateParams
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PaymentIntentInfo(
    val id: String,
    val clientSecret: String?,
    /** `true`, wenn die Zahlung bereits als bezahlt gilt (Fake-Provider im Dev-Modus). */
    val paid: Boolean,
)

data class OnboardingInfo(val accountId: String, val url: String)

sealed interface PaymentEvent {
    data class Succeeded(val paymentIntentId: String) : PaymentEvent
    data class Failed(val paymentIntentId: String) : PaymentEvent
    data class Refunded(val paymentIntentId: String) : PaymentEvent
    data object Ignored : PaymentEvent
}

class WebhookVerificationException(message: String) : Exception(message)

/**
 * Kapselt Stripe. Ohne `STRIPE_SECRET_KEY` wird der [FakePaymentProvider] verwendet (nur Dev/Test).
 * Siehe legacy/go-backend/internal/services/payment.go.
 */
interface PaymentProvider {
    val name: String

    /** `true`: Buchungen werden direkt als bezahlt (`confirmed`) angelegt, ohne Client-Zahlungsschritt. */
    val autoConfirms: Boolean

    fun createCustomer(email: String, name: String): String?

    fun createPaymentIntent(
        amountCents: Long,
        currency: String,
        customerId: String?,
        connectedAccountId: String?,
        platformFeePercent: Int,
    ): PaymentIntentInfo

    /** Fragt ab, ob der PaymentIntent erfolgreich bezahlt wurde (vor `confirm-payment`). */
    fun isPaid(paymentIntentId: String): Boolean

    fun refund(paymentIntentId: String)

    /** Legt (falls noetig) ein Connect-Express-Konto an und liefert den Onboarding-Link. */
    fun createOnboardingLink(email: String, existingAccountId: String?): OnboardingInfo

    /** Prueft die Signatur und uebersetzt das Event. */
    fun parseWebhook(payload: String, signatureHeader: String?): PaymentEvent

    companion object {
        fun from(config: AppConfig): PaymentProvider =
            if (!config.stripeSecretKey.isNullOrBlank()) {
                StripePaymentProvider(config.stripeSecretKey, config.stripeWebhookSecret)
            } else {
                require(!config.isProduction) { "STRIPE_SECRET_KEY muss in Produktion gesetzt sein" }
                FakePaymentProvider()
            }
    }
}

/**
 * Dev-Provider: keine Netzwerkzugriffe. Zahlungen gelten sofort als bezahlt, Rueckerstattungen sind No-ops.
 * Wird nur verwendet, wenn kein STRIPE_SECRET_KEY konfiguriert ist (nie in Produktion).
 */
class FakePaymentProvider : PaymentProvider {
    override val name = "fake"
    override val autoConfirms = true
    val refunded: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    override fun createCustomer(email: String, name: String): String? = null

    override fun createPaymentIntent(
        amountCents: Long, currency: String, customerId: String?, connectedAccountId: String?, platformFeePercent: Int,
    ): PaymentIntentInfo {
        val id = "pi_fake_" + UUID.randomUUID().toString().replace("-", "")
        return PaymentIntentInfo(id, clientSecret = "${id}_secret_fake", paid = true)
    }

    override fun isPaid(paymentIntentId: String) = true

    override fun refund(paymentIntentId: String) {
        refunded += paymentIntentId
    }

    override fun createOnboardingLink(email: String, existingAccountId: String?): OnboardingInfo {
        val acct = existingAccountId ?: ("acct_fake_" + UUID.randomUUID().toString().take(12))
        return OnboardingInfo(acct, "https://example.invalid/fake-stripe-onboarding?account=$acct")
    }

    override fun parseWebhook(payload: String, signatureHeader: String?): PaymentEvent =
        throw WebhookVerificationException("Stripe ist nicht konfiguriert (STRIPE_SECRET_KEY)")
}

class StripePaymentProvider(
    secretKey: String,
    private val webhookSecret: String?,
    private val refreshUrl: String = "https://chargeshare.app/host/onboard/refresh",
    private val returnUrl: String = "https://chargeshare.app/host/onboard/return",
) : PaymentProvider {
    private val client = StripeClient(secretKey)
    override val name = "stripe"
    override val autoConfirms = false

    override fun createCustomer(email: String, name: String): String? =
        client.customers().create(CustomerCreateParams.builder().setEmail(email).setName(name).build()).id

    override fun createPaymentIntent(
        amountCents: Long, currency: String, customerId: String?, connectedAccountId: String?, platformFeePercent: Int,
    ): PaymentIntentInfo {
        val b = PaymentIntentCreateParams.builder()
            .setAmount(amountCents)
            .setCurrency(currency)
            .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
        if (!customerId.isNullOrEmpty()) b.setCustomer(customerId)
        if (!connectedAccountId.isNullOrEmpty()) {
            b.setApplicationFeeAmount(amountCents * platformFeePercent / 100)
            b.setTransferData(PaymentIntentCreateParams.TransferData.builder().setDestination(connectedAccountId).build())
        }
        val pi = client.paymentIntents().create(b.build())
        return PaymentIntentInfo(pi.id, pi.clientSecret, paid = false)
    }

    override fun isPaid(paymentIntentId: String): Boolean =
        client.paymentIntents().retrieve(paymentIntentId).status == "succeeded"

    override fun refund(paymentIntentId: String) {
        client.refunds().create(RefundCreateParams.builder().setPaymentIntent(paymentIntentId).build())
    }

    override fun createOnboardingLink(email: String, existingAccountId: String?): OnboardingInfo {
        val accountId = existingAccountId?.takeIf { it.isNotEmpty() }
            ?: client.accounts().create(
                AccountCreateParams.builder().setType(AccountCreateParams.Type.EXPRESS).setEmail(email).build(),
            ).id
        val link = client.accountLinks().create(
            AccountLinkCreateParams.builder()
                .setAccount(accountId)
                .setRefreshUrl(refreshUrl)
                .setReturnUrl(returnUrl)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build(),
        )
        return OnboardingInfo(accountId, link.url)
    }

    override fun parseWebhook(payload: String, signatureHeader: String?): PaymentEvent {
        val secret = webhookSecret?.takeIf { it.isNotBlank() }
            ?: throw WebhookVerificationException("STRIPE_WEBHOOK_SECRET ist nicht konfiguriert")
        if (signatureHeader == null) throw WebhookVerificationException("missing Stripe-Signature header")
        try {
            Webhook.constructEvent(payload, signatureHeader, secret)
        } catch (e: Exception) {
            throw WebhookVerificationException("webhook signature verification failed: ${e.message}")
        }
        // Signatur ist gueltig: das Event-JSON selbst parsen (unabhaengig von der Stripe-API-Version).
        val root = Json.parseToJsonElement(payload).jsonObject
        val type = root["type"]?.jsonPrimitive?.contentOrNull
        val obj: JsonObject = root["data"]?.jsonObject?.get("object")?.jsonObject ?: return PaymentEvent.Ignored
        return when (type) {
            "payment_intent.succeeded" -> obj["id"]?.jsonPrimitive?.contentOrNull?.let { PaymentEvent.Succeeded(it) }
            "payment_intent.payment_failed" -> obj["id"]?.jsonPrimitive?.contentOrNull?.let { PaymentEvent.Failed(it) }
            "charge.refunded" -> obj["payment_intent"]?.jsonPrimitive?.contentOrNull?.let { PaymentEvent.Refunded(it) }
            else -> null
        } ?: PaymentEvent.Ignored
    }
}
