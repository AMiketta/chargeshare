package app.chargeshare.server.payments

import app.chargeshare.server.config.AppConfig
import com.stripe.StripeClient
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.param.RefundCreateParams
import com.stripe.model.v2.core.Account
import com.stripe.param.v2.core.AccountCreateParams
import com.stripe.param.v2.core.AccountRetrieveParams
import com.stripe.param.v2.core.AccountLinkCreateParams
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

/** Adressen, auf die Stripe nach dem Onboarding zurückleitet (Fertig bzw. Link abgelaufen). */
data class OnboardingUrls(val returnUrl: String, val refreshUrl: String)

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

    /**
     * Storniert einen noch offenen PaymentIntent (unbezahlte Buchung wurde storniert), damit er nicht bei Stripe offen bleibt.
     * Fehlertolerant: ein bereits bezahlter (`succeeded`) oder stornierter (`canceled`) Intent ist kein Fehler.
     */
    fun cancelIntent(paymentIntentId: String)

    /**
     * Holt einen bestehenden PaymentIntent erneut ab, damit eine offene Buchung nachtraeglich bezahlt werden kann.
     *
     * @return die Intent-Daten (inkl. `client_secret`; [PaymentIntentInfo.paid] = bereits bezahlt) oder `null`,
     * wenn der Intent storniert ist und ein neuer angelegt werden muss
     */
    fun resumeIntent(paymentIntentId: String): PaymentIntentInfo?

    /**
     * `true`: Wallboxes sind erst buchbar, wenn der Host ein vollstaendig eingerichtetes Auszahlungskonto hat
     * (siehe [isPayoutReady]). Der Fake-Provider (Dev/Test) verlangt das nicht.
     */
    val requiresPayoutAccount: Boolean get() = false

    /** Fragt beim Zahlungsanbieter ab, ob das Konto Auszahlungen empfangen kann (Onboarding abgeschlossen). */
    fun isPayoutReady(accountId: String): Boolean = true

    /** Legt (falls noetig) ein Connect-Express-Konto an und liefert den Onboarding-Link. */
    fun createOnboardingLink(email: String, existingAccountId: String?, urls: OnboardingUrls): OnboardingInfo

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
    val canceledIntents: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

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

    override fun cancelIntent(paymentIntentId: String) {
        canceledIntents += paymentIntentId
    }

    override fun resumeIntent(paymentIntentId: String): PaymentIntentInfo? =
        PaymentIntentInfo(paymentIntentId, clientSecret = "${paymentIntentId}_secret_fake", paid = true)

    override fun createOnboardingLink(email: String, existingAccountId: String?, urls: OnboardingUrls): OnboardingInfo {
        val acct = existingAccountId ?: ("acct_fake_" + UUID.randomUUID().toString().take(12))
        return OnboardingInfo(acct, "https://example.invalid/fake-stripe-onboarding?account=$acct")
    }

    override fun parseWebhook(payload: String, signatureHeader: String?): PaymentEvent =
        throw WebhookVerificationException("Stripe ist nicht konfiguriert (STRIPE_SECRET_KEY)")
}

class StripePaymentProvider(
    secretKey: String,
    private val webhookSecret: String?,
) : PaymentProvider {
    private val client = StripeClient(secretKey)
    private companion object {
        /** Intents in diesen Zustaenden lassen sich nicht mehr stornieren; sie muessen den Storno der Buchung nicht blockieren. */
        val FINISHED_INTENT_STATUSES = setOf("succeeded", "canceled")
    }
    override val name = "stripe"
    override val autoConfirms = false
    override val requiresPayoutAccount = true

    override fun isPayoutReady(accountId: String): Boolean {
        val params = AccountRetrieveParams.builder().addInclude(AccountRetrieveParams.Include.CONFIGURATION__RECIPIENT).build()
        return client.v2().core().accounts().retrieve(accountId, params).canReceiveTransfers()
    }

    override fun createCustomer(email: String, name: String): String? =
        client.customers().create(CustomerCreateParams.builder().setEmail(email).setName(name).build()).id

    override fun createPaymentIntent(
        amountCents: Long, currency: String, customerId: String?, connectedAccountId: String?, platformFeePercent: Int,
    ): PaymentIntentInfo {
        val params = buildPaymentIntentParams(amountCents, currency, customerId, connectedAccountId, platformFeePercent)
        val pi = client.paymentIntents().create(params)
        return PaymentIntentInfo(pi.id, pi.clientSecret, paid = false)
    }

    override fun isPaid(paymentIntentId: String): Boolean =
        client.paymentIntents().retrieve(paymentIntentId).status == "succeeded"

    override fun refund(paymentIntentId: String) {
        client.refunds().create(RefundCreateParams.builder().setPaymentIntent(paymentIntentId).build())
    }

    override fun cancelIntent(paymentIntentId: String) {
        val status = client.paymentIntents().retrieve(paymentIntentId).status
        if (status in FINISHED_INTENT_STATUSES) return
        client.paymentIntents().cancel(paymentIntentId)
    }

    override fun resumeIntent(paymentIntentId: String): PaymentIntentInfo? {
        val intent = client.paymentIntents().retrieve(paymentIntentId)
        return when (intent.status) {
            "canceled" -> null
            "succeeded" -> PaymentIntentInfo(intent.id, intent.clientSecret, paid = true)
            else -> PaymentIntentInfo(intent.id, intent.clientSecret, paid = false)
        }
    }

    /**
     * Legt (falls noetig) ein Connect-Konto ueber die Accounts-v2-API an (v1 ist fuer neue Stripe-Plattformen gesperrt)
     * und liefert den Onboarding-Link. Das Konto hat das Express-Dashboard und die Empfaenger-Konfiguration
     * (`stripe_transfers`), damit Destination Charges mit `transfer_data` funktionieren; Gebuehren und Verluste traegt die Plattform.
     */
    override fun createOnboardingLink(email: String, existingAccountId: String?, urls: OnboardingUrls): OnboardingInfo {
        val accountId = existingAccountId?.takeIf { it.isNotEmpty() }
            ?: client.v2().core().accounts().create(buildRecipientAccountParams(email)).id
        val link = client.v2().core().accountLinks().create(buildOnboardingLinkParams(accountId, urls.refreshUrl, urls.returnUrl))
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

/**
 * Parameter fuer den PaymentIntent. `automatic_payment_methods` laesst Stripe die im Dashboard aktivierten Methoden
 * waehlen; Google Pay und Apple Pay laufen dabei ueber die Zahlungsart „card“ und erscheinen im PaymentSheet der App.
 * `allow_redirects=never` beschraenkt auf Methoden ohne Weiterleitung (Karte, Wallets), weil die Apps noch keine
 * Rueckkehr-URL (`return_url`) verarbeiten.
 */
internal fun buildPaymentIntentParams(
    amountCents: Long,
    currency: String,
    customerId: String?,
    connectedAccountId: String?,
    platformFeePercent: Int,
): PaymentIntentCreateParams {
    val builder = PaymentIntentCreateParams.builder()
        .setAmount(amountCents)
        .setCurrency(currency)
        .setAutomaticPaymentMethods(
            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                .setEnabled(true)
                .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                .build(),
        )
    if (!customerId.isNullOrEmpty()) builder.setCustomer(customerId)
    if (!connectedAccountId.isNullOrEmpty()) {
        builder.setApplicationFeeAmount(amountCents * platformFeePercent / 100)
        builder.setTransferData(PaymentIntentCreateParams.TransferData.builder().setDestination(connectedAccountId).build())
    }
    return builder.build()
}

/** Hosts sitzen in Deutschland (Konzept); das Land eines Connect-Kontos laesst sich spaeter nicht aendern. */
private const val HOST_COUNTRY_CODE = "de"

/**
 * Parameter fuer ein Connect-Konto ueber die Accounts-v2-API (v1 ist fuer neue Stripe-Plattformen gesperrt):
 * Express-Dashboard und Empfaenger-Konfiguration mit `stripe_transfers`, damit Destination Charges mit `transfer_data`
 * funktionieren; Gebuehren und Verluste traegt die Plattform.
 */
internal fun buildRecipientAccountParams(email: String): AccountCreateParams {
    val transfers = AccountCreateParams.Configuration.Recipient.Capabilities.StripeBalance.StripeTransfers.builder()
        .setRequested(true)
        .build()
    val recipient = AccountCreateParams.Configuration.Recipient.builder()
        .setCapabilities(
            AccountCreateParams.Configuration.Recipient.Capabilities.builder()
                .setStripeBalance(
                    AccountCreateParams.Configuration.Recipient.Capabilities.StripeBalance.builder()
                        .setStripeTransfers(transfers)
                        .build(),
                )
                .build(),
        )
        .build()
    val responsibilities = AccountCreateParams.Defaults.Responsibilities.builder()
        .setFeesCollector(AccountCreateParams.Defaults.Responsibilities.FeesCollector.APPLICATION)
        .setLossesCollector(AccountCreateParams.Defaults.Responsibilities.LossesCollector.APPLICATION)
        .build()
    return AccountCreateParams.builder()
        .setContactEmail(email)
        .setDisplayName(email.substringBefore('@'))
        .setIdentity(AccountCreateParams.Identity.builder().setCountry(HOST_COUNTRY_CODE).build())
        .setDashboard(AccountCreateParams.Dashboard.EXPRESS)
        .setDefaults(AccountCreateParams.Defaults.builder().setResponsibilities(responsibilities).build())
        .setConfiguration(AccountCreateParams.Configuration.builder().setRecipient(recipient).build())
        .build()
}

/** Parameter fuer den einmalig nutzbaren Onboarding-Link eines Connect-Kontos (Accounts v2). */
internal fun buildOnboardingLinkParams(accountId: String, refreshUrl: String, returnUrl: String): AccountLinkCreateParams =
    AccountLinkCreateParams.builder()
        .setAccount(accountId)
        .setUseCase(
            AccountLinkCreateParams.UseCase.builder()
                .setType(AccountLinkCreateParams.UseCase.Type.ACCOUNT_ONBOARDING)
                .setAccountOnboarding(
                    AccountLinkCreateParams.UseCase.AccountOnboarding.builder()
                        .setRefreshUrl(refreshUrl)
                        .setReturnUrl(returnUrl)
                        .build(),
                )
                .build(),
        )
        .build()

/** Das Konto darf Ueberweisungen empfangen (`stripe_transfers` aktiv), also ist das Onboarding abgeschlossen. */
internal fun Account.canReceiveTransfers(): Boolean =
    configuration?.recipient?.capabilities?.stripeBalance?.stripeTransfers?.status == "active"
