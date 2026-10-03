package app.chargeshare.server

import app.chargeshare.server.config.AppConfig
import app.chargeshare.server.payments.buildPaymentIntentParams
import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.PaymentProviderKind
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `GET /payments/config`, Konfigurationsregeln und PaymentIntent-Parameter für Google Pay / Apple Pay. */
class PaymentConfigApiTest {

    private val stripeConfig = testConfig.copy(
        stripeSecretKey = "sk_test_geheim",
        stripePublishableKey = "pk_test_oeffentlich",
        applePayMerchantId = "merchant.app.chargeshare",
    )

    @Test
    fun fakeModeReportsFakeProviderWithoutPublishableKey() = serverTest {
        val driver = registerUser("fahrer@example.com")
        val config = driver.api.paymentConfig()
        assertEquals(PaymentProviderKind.FAKE, config.provider)
        assertFalse(config.requiresClientPayment)
        assertNull(config.publishableKey)
        assertEquals("DE", config.merchantCountryCode)
        assertEquals("eur", config.currency)
        assertTrue(config.googlePayTestEnvironment)
    }

    @Test
    fun stripeModeDeliversPublishableKeyAndMerchantDataButNoSecrets() = serverTest(
        config = stripeConfig,
        payments = ScriptedPaymentProvider(),
    ) {
        val driver = registerUser("fahrer@example.com")
        val config = driver.api.paymentConfig()
        assertEquals(PaymentProviderKind.STRIPE, config.provider)
        assertTrue(config.requiresClientPayment)
        assertEquals("pk_test_oeffentlich", config.publishableKey)
        assertEquals("merchant.app.chargeshare", config.applePayMerchantId)
        assertEquals("ChargeShare", config.merchantDisplayName)
        assertTrue(config.googlePayTestEnvironment)

        val rawBody = raw.getAuth("/api/v1/payments/config", driver.store.accessToken()).text()
        assertFalse("sk_test_geheim" in rawBody, "der Secret Key darf nie ausgeliefert werden")
        assertTrue("\"publishable_key\":\"pk_test_oeffentlich\"" in rawBody)
        assertTrue("\"google_pay_test_environment\":true" in rawBody)
    }

    @Test
    fun configRequiresLogin() = serverTest {
        assertEquals(HttpStatusCode.Unauthorized, raw.getAuth("/api/v1/payments/config", null).status)
    }

    @Test
    fun bookingWithStripeReturnsPendingBookingAndClientSecretForPaymentSheet() = serverTest(
        config = stripeConfig,
        payments = ScriptedPaymentProvider(),
    ) {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox("Marktplatz-Box", power = 11.0, price = 0.40))
        host.api.setWallboxOnline(wallbox.id, true)
        val driver = registerUser("fahrer@example.com")

        val response = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12)))
        assertEquals(BookingStatus.PENDING, response.booking?.status)
        assertEquals("pi_test_1_secret", response.clientSecret)
        // Ohne abgeschlossene Zahlung darf die App nicht „bezahlt“ melden können.
        assertEquals(402, assertFailsWith<ApiException> { driver.api.confirmPayment(response.booking!!.id) }.status)
        (payments as ScriptedPaymentProvider).paid = true
        assertEquals(BookingStatus.CONFIRMED, driver.api.confirmPayment(response.booking!!.id).status)
    }

    /** Das PaymentSheet erlaubt nach abgelehnter Karte einen weiteren Versuch, obwohl der Webhook die Buchung schon `failed` gesetzt hat. */
    @Test
    fun retryAfterDeclinedCardStillConfirmsTheBooking() = serverTest(config = stripeConfig, payments = ScriptedPaymentProvider()) {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox("Marktplatz-Box", power = 11.0, price = 0.40))
        host.api.setWallboxOnline(wallbox.id, true)
        val driver = registerUser("fahrer@example.com")
        val booking = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))).booking!!

        forceBookingStatus(booking.id, "failed") // Webhook payment_intent.payment_failed nach abgelehnter Karte
        (payments as ScriptedPaymentProvider).paid = true // zweiter Versuch im Sheet klappt
        assertEquals(BookingStatus.CONFIRMED, driver.api.confirmPayment(booking.id).status)
        assertTrue(payments.refunds.isEmpty())
    }

    @Test
    fun paidFailedBookingWhoseSlotWasTakenIsRefundedAndRejected() = serverTest(config = stripeConfig, payments = ScriptedPaymentProvider()) {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox("Marktplatz-Box", power = 11.0, price = 0.40))
        host.api.setWallboxOnline(wallbox.id, true)
        val driver = registerUser("fahrer@example.com")
        val other = registerUser("anderer@example.com")
        val first = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        forceBookingStatus(first.id, "failed")
        other.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))) // Slot ist wieder frei und wird vergeben

        (payments as ScriptedPaymentProvider).paid = true
        assertEquals(409, assertFailsWith<ApiException> { driver.api.confirmPayment(first.id) }.status)
        assertEquals(listOf(first.stripePaymentIntentId), payments.refunds)
        assertEquals(BookingStatus.FAILED, driver.api.booking(first.id).status)
    }

    @Test
    fun succeededWebhookConfirmsBookingThatWasMarkedFailed() = serverTest(config = stripeConfig, payments = ScriptedPaymentProvider()) {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox("Marktplatz-Box", power = 11.0, price = 0.40))
        host.api.setWallboxOnline(wallbox.id, true)
        val driver = registerUser("fahrer@example.com")
        val booking = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        val paymentIntentId = booking.stripePaymentIntentId!!
        forceBookingStatus(booking.id, "failed")

        val response = raw.post("/api/v1/payments/webhook") {
            header("Stripe-Signature", "ok")
            setBody("""{"type":"payment_intent.succeeded","id":"$paymentIntentId"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(BookingStatus.CONFIRMED, driver.api.booking(booking.id).status)
    }

    @Test
    fun publishableKeyMustNotBeASecretKey() {
        assertTrue(runCatching { AppConfig(stripePublishableKey = "sk_live_verwechselt") }.isFailure)
    }

    @Test
    fun productionWithStripeAndPublishableKeyIsAccepted() {
        val production = AppConfig(
            env = "production",
            jwtSecret = "s".repeat(40),
            jwtRefreshSecret = "r".repeat(40),
            stripeSecretKey = "sk_live_x",
            stripePublishableKey = "pk_live_x",
        )
        assertEquals("production", production.googlePayEnvironment)
    }

    @Test
    fun productionWithStripeSecretButWithoutPublishableKeyIsRejected() {
        val result = runCatching {
            AppConfig(env = "production", jwtSecret = "s".repeat(40), jwtRefreshSecret = "r".repeat(40), stripeSecretKey = "sk_live_x")
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun environmentVariablesAreMappedAndGooglePayEnvironmentIsDerivedFromKey() {
        val test = AppConfig.fromEnv(mapOf("STRIPE_PUBLISHABLE_KEY" to "pk_test_a", "APPLE_PAY_MERCHANT_ID" to "merchant.x"))
        assertEquals("pk_test_a", test.stripePublishableKey)
        assertEquals("merchant.x", test.applePayMerchantId)
        assertEquals("test", test.googlePayEnvironment)

        val live = AppConfig.fromEnv(mapOf("STRIPE_PUBLISHABLE_KEY" to "pk_live_a"))
        assertEquals("production", live.googlePayEnvironment)

        val overridden = AppConfig.fromEnv(
            mapOf("STRIPE_PUBLISHABLE_KEY" to "pk_live_a", "GOOGLE_PAY_ENVIRONMENT" to "TEST", "MERCHANT_COUNTRY_CODE" to "at"),
        )
        assertEquals("test", overridden.googlePayEnvironment)
        assertEquals("AT", overridden.merchantCountryCode)
        assertTrue(runCatching { AppConfig.fromEnv(mapOf("GOOGLE_PAY_ENVIRONMENT" to "sandbox")) }.isFailure)
    }

    @Test
    fun paymentIntentUsesAutomaticPaymentMethodsWithoutRedirects() {
        val params = buildPaymentIntentParams(
            amountCents = 880,
            currency = "eur",
            customerId = "cus_1",
            connectedAccountId = "acct_host",
            platformFeePercent = 15,
        )
        assertEquals(880L, params.amount)
        assertEquals("eur", params.currency)
        assertEquals(true, params.automaticPaymentMethods?.enabled)
        assertEquals("never", params.automaticPaymentMethods?.allowRedirects?.value)
        assertEquals("cus_1", params.customer)
        assertEquals(132L, params.applicationFeeAmount)
        assertEquals("acct_host", params.transferData?.destination)
    }

    @Test
    fun paymentIntentWithoutHostAccountHasNoTransfer() {
        val params = buildPaymentIntentParams(500, "eur", customerId = null, connectedAccountId = null, platformFeePercent = 15)
        assertNull(params.transferData)
        assertNull(params.applicationFeeAmount)
        assertNull(params.customer)
    }
}
