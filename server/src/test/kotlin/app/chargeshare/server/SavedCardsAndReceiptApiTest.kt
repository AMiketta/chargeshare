package app.chargeshare.server

import app.chargeshare.server.payments.PaymentIntentInfo
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.payments.SavedCardInfo
import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.CreateBookingRequest
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Wie Stripe: Kunden und Karten pro Kunde ([cards]); `customerSecrets` zählt Customer Sessions. */
class CardCapableProvider(val base: ScriptedPaymentProvider = ScriptedPaymentProvider()) : PaymentProvider by base {
    override val supportsSavedCards = true
    val cards: MutableMap<String, MutableList<SavedCardInfo>> = Collections.synchronizedMap(mutableMapOf())
    val paymentIntentCustomers: MutableList<String?> = Collections.synchronizedList(mutableListOf())
    @Volatile var failure: Throwable? = null

    override fun createCustomer(email: String, name: String): String = "cus_${email.substringBefore('@')}"

    override fun createPaymentIntent(
        amountCents: Long, currency: String, customerId: String?, connectedAccountId: String?, platformFeePercent: Int,
    ): PaymentIntentInfo {
        paymentIntentCustomers += customerId
        return base.createPaymentIntent(amountCents, currency, customerId, connectedAccountId, platformFeePercent)
    }

    override fun listCards(customerId: String): List<SavedCardInfo> {
        failure?.let { throw it }
        return cards[customerId].orEmpty().toList()
    }

    override fun detachCard(customerId: String, paymentMethodId: String): Boolean =
        cards[customerId]?.removeAll { it.id == paymentMethodId } ?: false

    override fun createSetupIntent(customerId: String) = "seti_${customerId}_secret"

    override fun createCustomerSession(customerId: String): String? = "cuss_${customerId}_secret"
}

/** Gespeicherte Karten (SetupIntent, Liste, Entfernen) und Quittung (PDF) für abgeschlossene Buchungen. */
class SavedCardsAndReceiptApiTest {

    private val visa = SavedCardInfo("pm_1", "visa", "4242", 12, 2030)

    @Test
    fun fakeProviderReportsCardsAsUnsupported() = serverTest {
        val user = registerUser("fahrer@example.com")
        assertEquals(false, user.api.savedCards().supported)
        val e = assertFailsWith<ApiException> { user.api.createSetupIntent() }
        assertEquals(503, e.status)
    }

    @Test
    fun cardsAreListedAndRemovedPerUser() = serverTest(payments = CardCapableProvider()) {
        val provider = payments as CardCapableProvider
        val ada = registerUser("ada@example.com")
        val bob = registerUser("bob@example.com")
        provider.cards["cus_ada"] = mutableListOf(visa)

        val list = ada.api.savedCards()
        assertTrue(list.supported)
        assertEquals(listOf("pm_1" to "4242"), list.cards.map { it.id to it.last4 })
        assertEquals(emptyList(), bob.api.savedCards().cards)

        // Fremde Karte lässt sich nicht entfernen, die eigene schon
        assertEquals(404, assertFailsWith<ApiException> { bob.api.removeSavedCard("pm_1") }.status)
        ada.api.removeSavedCard("pm_1")
        assertEquals(emptyList(), ada.api.savedCards().cards)
        assertEquals(404, assertFailsWith<ApiException> { ada.api.removeSavedCard("pm_1") }.status)
    }

    @Test
    fun setupIntentAndCustomerSessionUseTheUsersStripeCustomer() = serverTest(payments = CardCapableProvider()) {
        val ada = registerUser("ada@example.com")

        val setup = ada.api.createSetupIntent()
        assertEquals("cus_ada", setup.customerId)
        assertEquals("seti_cus_ada_secret", setup.clientSecret)
        val session = ada.api.createCustomerSession()
        assertEquals("cus_ada", session.customerId)
        assertEquals("cuss_cus_ada_secret", session.customerSessionClientSecret)
        assertEquals("cus_ada", ada.api.me().stripeCustomerId)
    }

    @Test
    fun stripeOutageOnListingIsAReadableServiceError() = serverTest(payments = CardCapableProvider()) {
        val provider = payments as CardCapableProvider
        val ada = registerUser("ada@example.com")
        provider.failure = com.stripe.exception.ApiConnectionException("boom")

        val e = assertFailsWith<ApiException> { ada.api.savedCards() }
        assertEquals(503, e.status)
        assertTrue("Zahlungsanbieter" in e.message.orEmpty())
    }

    @Test
    fun bookingCreatesTheStripeCustomerForTheDriver() = serverTest(payments = CardCapableProvider()) {
        val provider = payments as CardCapableProvider
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox()).also { host.api.setWallboxOnline(it.id, true) }
        val driver = registerUser("fahrer@example.com")

        driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12)))

        assertEquals(listOf<String?>("cus_fahrer"), provider.paymentIntentCustomers)
    }

    // ---- Quittung -----------------------------------------------------------------------------

    private suspend fun ServerScope.completedBooking(): Pair<TestClient, String> {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox("Marktplatz-Box", price = 0.40)).also { host.api.setWallboxOnline(it.id, true) }
        val driver = registerUser("fahrer@example.com")
        val booking = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        forceBookingStatus(booking.id, "completed")
        forceBookingTotals(booking.id, amount = 8.80, kwh = 22.0)
        return driver to booking.id
    }

    @Test
    fun receiptIsAPdfWithBookingDataAndPlatformFee() = serverTest {
        val (driver, bookingId) = completedBooking()

        val bytes = driver.api.bookingReceipt(bookingId)
        assertEquals("%PDF-1.4", bytes.decodeToString(0, 8))
        assertTrue(bytes.decodeToString().trimEnd().endsWith("%%EOF"))
        val text = String(bytes, Charsets.ISO_8859_1)
        // Windows-1252: € = 0x80
        assertTrue("(8,80 \u0080)" in text, "Gesamtbetrag")
        assertTrue("(22,00 kWh)" in text, "gemessene kWh")
        assertTrue("(1,32 \u0080)" in text, "15 % Plattformgebühr von 8,80 €")
        assertTrue("Marktplatz-Box" in text)
        assertTrue("(host)" in text, "Host-Name")
    }

    @Test
    fun receiptIsServedAsAttachment() = serverTest {
        val (driver, bookingId) = completedBooking()
        val response = raw.getAuth("/api/v1/bookings/$bookingId/receipt", driver.store.accessToken())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, ContentType.parse(response.headers[HttpHeaders.ContentType]!!).withoutParameters())
        assertTrue("attachment" in response.headers[HttpHeaders.ContentDisposition].orEmpty())
        assertNotNull(response.readRawBytes().takeIf { it.size > 500 })
    }

    @Test
    fun receiptIsOnlyForTheDriverOfACompletedBooking() = serverTest {
        val (driver, bookingId) = completedBooking()
        val stranger = registerUser("fremd@example.com")
        assertEquals(404, assertFailsWith<ApiException> { stranger.api.bookingReceipt(bookingId) }.status)

        forceBookingStatus(bookingId, "confirmed")
        assertEquals(409, assertFailsWith<ApiException> { driver.api.bookingReceipt(bookingId) }.status)

        assertEquals(HttpStatusCode.Unauthorized, raw.get("/api/v1/bookings/$bookingId/receipt").status)
    }

    @Test
    fun receiptNeverContainsSecrets() = serverTest {
        val (driver, bookingId) = completedBooking()
        val text = String(driver.api.bookingReceipt(bookingId), Charsets.ISO_8859_1)
        assertNull(Regex("sk_(test|live)_|whsec_").find(text))
    }
}
