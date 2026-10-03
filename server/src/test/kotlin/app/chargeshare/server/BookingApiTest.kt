package app.chargeshare.server

import app.chargeshare.server.payments.PaymentEvent
import app.chargeshare.server.payments.PaymentIntentInfo
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.payments.OnboardingInfo
import app.chargeshare.server.payments.StripePaymentProvider
import app.chargeshare.server.payments.WebhookVerificationException
import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.CreateReviewRequest
import app.chargeshare.shared.model.ErrorCodes
import app.chargeshare.shared.model.TimeSlot
import app.chargeshare.shared.model.UserRole
import app.chargeshare.shared.model.WallboxSearch
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import java.util.Collections
import kotlinx.coroutines.async
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Zahlungs-Double wie ein echter Provider: Zahlung ist erst nach `paid = true` bestaetigbar. */
class ScriptedPaymentProvider : PaymentProvider {
    override val name = "scripted"
    override val autoConfirms = false
    @Volatile var paid = false
    val refunds: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val amounts: MutableList<Long> = Collections.synchronizedList(mutableListOf())
    override fun createCustomer(email: String, name: String): String? = null
    override fun createPaymentIntent(amountCents: Long, currency: String, customerId: String?, connectedAccountId: String?, platformFeePercent: Int): PaymentIntentInfo {
        amounts += amountCents
        return PaymentIntentInfo("pi_test_${amounts.size}", "pi_test_${amounts.size}_secret", paid = false)
    }
    override fun isPaid(paymentIntentId: String): Boolean {
        isPaidFailure?.let { throw it }
        return paid
    }
    /** Wenn gesetzt, wirft [isPaid] diesen Fehler (Stripe nicht erreichbar). */
    @Volatile var isPaidFailure: Throwable? = null
    override fun resumeIntent(paymentIntentId: String): PaymentIntentInfo? =
        if (paymentIntentId in canceledIntents) null else PaymentIntentInfo(paymentIntentId, "${paymentIntentId}_secret", paid = paid)
    override fun refund(paymentIntentId: String) { refunds += paymentIntentId }
    val canceledIntents: MutableList<String> = Collections.synchronizedList(mutableListOf())
    /** Wenn gesetzt, wirft [cancelIntent] diesen Fehler (z. B. Intent bei Stripe schon bezahlt). */
    @Volatile var cancelIntentFailure: Throwable? = null
    /** Wenn gesetzt, wirft [createOnboardingLink] diesen Fehler (z. B. Stripe-Connect nicht aktiviert). */
    @Volatile var onboardingFailure: Throwable? = null
    override fun cancelIntent(paymentIntentId: String) {
        canceledIntents += paymentIntentId
        cancelIntentFailure?.let { throw it }
    }
    override fun createOnboardingLink(email: String, existingAccountId: String?): OnboardingInfo {
        onboardingFailure?.let { throw it }
        return OnboardingInfo(existingAccountId ?: "acct_test", "https://stripe.test/onboard")
    }
    override fun parseWebhook(payload: String, signatureHeader: String?): PaymentEvent {
        if (signatureHeader != "ok") throw WebhookVerificationException("bad signature")
        val id = Regex("\"id\":\"([^\"]+)\"").find(payload)!!.groupValues[1]
        return when {
            "succeeded" in payload -> PaymentEvent.Succeeded(id)
            "payment_failed" in payload -> PaymentEvent.Failed(id)
            else -> PaymentEvent.Refunded(id)
        }
    }
}

class BookingApiTest {

    /** Kompletter Ablauf: registrieren -> einloggen -> Wallbox suchen -> Slots -> buchen -> stornieren. */
    @Test
    fun fullDriverFlow() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wb = host.api.createWallbox(newWallbox("Marktplatz-Box", power = 11.0, price = 0.40))
        host.api.setWallboxOnline(wb.id, true)

        // Fahrer registriert sich und loggt sich auf einem zweiten Geraet ein
        newApi().api.register("fahrer@example.com", "geheim1234", "Fahrer")
        val driver = newApi()
        driver.api.login("fahrer@example.com", "geheim1234")

        // Suche in der Naehe
        val found = driver.api.searchWallboxes(WallboxSearch(lat = 53.0758, lng = 8.8072, radiusM = 3000.0)).wallboxes
        assertEquals(listOf(wb.id), found.map { it.id })

        // Slots (morgen) enthalten 10:00-10:30
        val day = inFuture(1, 0).toString().substringBefore('T')
        assertTrue(TimeSlot("10:00", "10:30") in driver.api.wallboxSlots(wb.id, day).slots)

        // Buchen: 2 h * 11 kW * 0,40 EUR = 8,80 EUR; Fake-Provider -> bezahlt/confirmed
        val res = driver.api.createBooking(CreateBookingRequest(wb.id, inFuture(1, 10), inFuture(1, 12)))
        val booking = res.booking!!
        assertEquals(BookingStatus.CONFIRMED, booking.status)
        assertEquals(22.0, booking.estimatedKwh)
        assertEquals(8.80, booking.totalAmount)
        assertEquals(0.40, booking.pricePerKwh)
        assertTrue(booking.stripePaymentIntentId!!.startsWith("pi_fake_"))
        assertNotNull(res.clientSecret)
        assertEquals(wb.id, booking.wallbox!!.id)

        // belegte Slots verschwinden
        val after = driver.api.wallboxSlots(wb.id, day).slots
        assertFalse(TimeSlot("10:00", "10:30") in after)
        assertFalse(TimeSlot("11:30", "12:00") in after)
        assertTrue(TimeSlot("09:30", "10:00") in after)

        // Liste + Detail
        assertEquals(listOf(booking.id), driver.api.bookings().bookings.map { it.id })
        assertEquals(booking.id, driver.api.booking(booking.id).id)
        assertEquals(emptyList(), driver.api.bookings(status = BookingStatus.CANCELLED).bookings)
        // confirm-payment ist im Fake-Modus idempotent
        assertEquals(BookingStatus.CONFIRMED, driver.api.confirmPayment(booking.id).status)

        // Stornieren (> 30 min vor Start) -> 204, Fake-Provider erstattet
        val cancelled = driver.api.cancelBooking(booking.id, "Plan geaendert")
        assertEquals(BookingStatus.CANCELLED, cancelled.status)
        assertEquals("Plan geaendert", cancelled.cancelReason)
        assertTrue((payments as app.chargeshare.server.payments.FakePaymentProvider).refunded.contains(booking.stripePaymentIntentId))
        // Slot ist wieder frei und neu buchbar
        assertTrue(TimeSlot("10:00", "10:30") in driver.api.wallboxSlots(wb.id, day).slots)
        driver.api.createBooking(CreateBookingRequest(wb.id, inFuture(1, 10), inFuture(1, 12)))
        // erneutes Stornieren einer stornierten Buchung ist ein Fehler
        assertEquals(400, assertFailsWith<ApiException> { driver.api.cancelBooking(booking.id) }.status)
    }

    @Test
    fun bookingRulesAndOverlapProtection() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val a = registerUser("a@example.com")
        val b = registerUser("b@example.com")
        val wb = host.api.createWallbox(newWallbox())

        fun req(sh: Int, eh: Int, day: Long = 1) = CreateBookingRequest(wb.id, inFuture(day, sh), inFuture(day, eh))

        // offline
        assertEquals("wallbox is currently offline", assertFailsWith<ApiException> { a.api.createBooking(req(10, 12)) }.message)
        host.api.setWallboxOnline(wb.id, true)

        assertEquals(400, assertFailsWith<ApiException> { a.api.createBooking(req(12, 10)) }.status) // Ende vor Start
        assertEquals(400, assertFailsWith<ApiException> { a.api.createBooking(req(10, 12, day = -1)) }.status) // Vergangenheit
        assertEquals(422, assertFailsWith<ApiException> { a.api.createBooking(CreateBookingRequest(wb.id, inFuture(1, 10), inFuture(1, 10, 10))) }.status) // < 30 min
        assertEquals(422, assertFailsWith<ApiException> { a.api.createBooking(CreateBookingRequest(wb.id, inFuture(1, 0), inFuture(1, 10))) }.status) // > 480 min
        assertEquals(404, assertFailsWith<ApiException> { a.api.createBooking(CreateBookingRequest("00000000-0000-0000-0000-000000000000", inFuture(1, 10), inFuture(1, 12))) }.status)
        assertEquals(400, assertFailsWith<ApiException> { a.api.createBooking(CreateBookingRequest("kaputt", inFuture(1, 10), inFuture(1, 12))) }.status)
        assertEquals(401, assertFailsWith<ApiException> { newApi().api.createBooking(req(10, 12)) }.status)

        a.api.createBooking(req(10, 12))
        // ueberlappend (Teilueberlappung, Enthalten, gleich) -> 409
        for ((s, e) in listOf(11 to 13, 9 to 11, 10 to 12, 10 to 11)) {
            val ex = assertFailsWith<ApiException> { b.api.createBooking(req(s, e)) }
            assertEquals(409, ex.status, "$s-$e")
            assertEquals(ErrorCodes.CONFLICT, ex.code)
        }
        // direkt anschliessend ist erlaubt
        b.api.createBooking(req(12, 14))
        b.api.createBooking(req(8, 10))
    }

    @Test
    fun exclusionConstraintCatchesConcurrentRequests() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wb = host.api.createWallbox(newWallbox())
        host.api.setWallboxOnline(wb.id, true)
        val users = (1..6).map { registerUser("u$it@example.com") }
        val results = kotlinx.coroutines.coroutineScope {
            val scope = this
            users.map { u ->
                scope.async(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching { u.api.createBooking(CreateBookingRequest(wb.id, inFuture(2, 10), inFuture(2, 12))) }
                }
            }.map { it.await() }
        }
        assertEquals(1, results.count { it.isSuccess }, "genau eine Buchung darf durchgehen")
        assertTrue(results.filter { it.isFailure }.all { (it.exceptionOrNull() as? ApiException)?.status == 409 }, results.map { it.exceptionOrNull()?.toString() }.toString())
    }

    @Test
    fun accessControlOnBookings() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val otherHost = registerUser("host2@example.com", host = true)
        val driver = registerUser("driver@example.com")
        val stranger = registerUser("stranger@example.com")
        val wb = host.api.createWallbox(newWallbox()); host.api.setWallboxOnline(wb.id, true)
        val bk = driver.api.createBooking(CreateBookingRequest(wb.id, inFuture(1, 10), inFuture(1, 11))).booking!!

        assertEquals(bk.id, host.api.booking(bk.id).id) // Wallbox-Besitzer darf lesen
        assertEquals("driver", host.api.booking(bk.id).driver!!.name)
        assertEquals(403, assertFailsWith<ApiException> { stranger.api.booking(bk.id) }.status)
        assertEquals(403, assertFailsWith<ApiException> { otherHost.api.booking(bk.id) }.status)
        assertEquals(403, assertFailsWith<ApiException> { host.api.cancelBooking(bk.id) }.status) // nur der Fahrer storniert
        assertEquals(403, assertFailsWith<ApiException> { stranger.api.confirmPayment(bk.id) }.status)
        assertEquals(404, assertFailsWith<ApiException> { driver.api.booking("00000000-0000-0000-0000-000000000000") }.status)
        assertEquals(emptyList(), stranger.api.bookings().bookings)
    }

    @Test
    fun pendingPaymentFlowWithRealisticProvider() = serverTest(payments = ScriptedPaymentProvider()) {
        val sp = payments as ScriptedPaymentProvider
        val host = registerUser("host@example.com", host = true)
        val driver = registerUser("driver@example.com")
        val wb = host.api.createWallbox(newWallbox(power = 11.0, price = 0.40)); host.api.setWallboxOnline(wb.id, true)

        val res = driver.api.createBooking(CreateBookingRequest(wb.id, inFuture(1, 10), inFuture(1, 11)))
        assertEquals(BookingStatus.PENDING, res.booking!!.status)
        assertEquals("pi_test_1_secret", res.clientSecret)
        assertEquals(listOf(440L), sp.amounts) // 1 h * 11 kW * 0,40 = 4,40 EUR
        // der pending-Slot ist reserviert
        assertEquals(409, assertFailsWith<ApiException> { registerUser("x@example.com").api.createBooking(CreateBookingRequest(wb.id, inFuture(1, 10), inFuture(1, 11))) }.status)

        // ohne erfolgte Zahlung -> 402
        val e = assertFailsWith<ApiException> { driver.api.confirmPayment(res.booking!!.id) }
        assertEquals(402, e.status)
        assertEquals(ErrorCodes.PAYMENT_REQUIRED, e.code)
        sp.paid = true
        assertEquals(BookingStatus.CONFIRMED, driver.api.confirmPayment(res.booking!!.id).status)
        assertEquals(400, assertFailsWith<ApiException> { driver.api.confirmPayment(res.booking!!.id) }.status) // nicht mehr pending

        // Webhooks
        val res2 = driver.api.createBooking(CreateBookingRequest(wb.id, inFuture(2, 10), inFuture(2, 11)))
        val pi2 = res2.booking!!.stripePaymentIntentId!!
        val bad = raw.post("/api/v1/payments/webhook") { header("Stripe-Signature", "nope"); setBody("""{"type":"payment_intent.succeeded","id":"$pi2"}""") }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        val ok = raw.post("/api/v1/payments/webhook") { header("Stripe-Signature", "ok"); setBody("""{"type":"payment_intent.payment_failed","id":"$pi2"}""") }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals(BookingStatus.FAILED, driver.api.booking(res2.booking!!.id).status)
        // `failed` gibt den Slot frei
        driver.api.createBooking(CreateBookingRequest(wb.id, inFuture(2, 10), inFuture(2, 11)))

        // Stornierung eines bezahlten Slots loest Erstattung aus; pending nicht
        driver.api.cancelBooking(res.booking!!.id)
        assertEquals(listOf(res.booking!!.stripePaymentIntentId), sp.refunds)
    }

    @Test
    fun hostDashboardReviewsAndEarnings() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val driver = registerUser("driver@example.com")
        val wb = host.api.createWallbox(newWallbox(power = 10.0, price = 0.50)); host.api.setWallboxOnline(wb.id, true)

        // Fahrer ohne Host-Rolle -> 403
        assertEquals(403, assertFailsWith<ApiException> { driver.api.hostBookings() }.status)
        assertEquals(403, assertFailsWith<ApiException> { driver.api.hostEarnings() }.status)
        assertEquals(403, assertFailsWith<ApiException> { driver.api.hostSessions() }.status)
        assertEquals(401, assertFailsWith<ApiException> { newApi().api.hostBookings() }.status)

        val bk = driver.api.createBooking(CreateBookingRequest(wb.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        val hb = host.api.hostBookings()
        assertEquals(listOf(bk.id), hb.bookings.map { it.id })
        assertEquals("driver", hb.bookings[0].driver!!.name)
        assertEquals(wb.id, hb.bookings[0].wallbox!!.id)
        assertNull(hb.bookings[0].driver!!.stripeCustomerId)

        // Bewertung erst nach `completed`
        assertEquals(400, assertFailsWith<ApiException> { driver.api.createReview(CreateReviewRequest(bk.id, 5, "super")) }.status)
        forceBookingStatus(bk.id, "completed")
        assertEquals(422, assertFailsWith<ApiException> { driver.api.createReview(CreateReviewRequest(bk.id, 6)) }.status)
        assertEquals(404, assertFailsWith<ApiException> { host.api.createReview(CreateReviewRequest(bk.id, 5)) }.status) // nicht der Fahrer
        val review = driver.api.createReview(CreateReviewRequest(bk.id, 4, "Gute Box"))
        assertEquals(4, review.rating)
        assertEquals(409, assertFailsWith<ApiException> { driver.api.createReview(CreateReviewRequest(bk.id, 5)) }.status)

        val reviews = newApi().api.wallboxReviews(wb.id)
        assertEquals(1, reviews.total)
        assertEquals("Gute Box", reviews.reviews[0].comment)
        assertEquals("driver", reviews.reviews[0].author!!.name)
        val updated = newApi().api.wallbox(wb.id)
        assertEquals(1, updated.ratingCount)
        assertEquals(4.0, updated.ratingAvg)

        // Einnahmen: 2 h * 10 kW * 0,50 = 10,00 EUR
        val earnings = host.api.hostEarnings()
        assertEquals(10.0, earnings.total)
        assertEquals(1, earnings.sessions)
        assertEquals(4.0, earnings.avgRating)
        assertEquals(0, host.api.hostEarnings(from = "2000-01-01", to = "2000-12-31").sessions)
        assertEquals(1, host.api.hostEarnings(from = "2000-01-01", to = "2999-12-31").sessions)
        assertEquals(400, assertFailsWith<ApiException> { host.api.hostEarnings(from = "gestern") }.status)

        // Sessions (Telemetrie): per SQL angelegt, wie sie OCPP schreibt
        transaction(TestDb.database) {
            exec("INSERT INTO charging_sessions(booking_id, ocpp_id, transaction_id, meter_start, meter_stop, started_at, stopped_at) VALUES ('${bk.id}', 'CP1', 42, 100, 20100, NOW(), NOW())")
        }
        val sessions = host.api.hostSessions().sessions
        assertEquals(1, sessions.size)
        assertEquals(42, sessions[0].transactionId)
        assertEquals("driver", sessions[0].driver!!.name)
        assertEquals(emptyList(), registerUser("h2@example.com", host = true).api.hostSessions().sessions)
    }

    @Test
    fun onboardingAndRoles() = serverTest(payments = ScriptedPaymentProvider()) {
        val u = registerUser("u@example.com")
        assertEquals("https://stripe.test/onboard", u.api.startPayoutOnboarding().url)
        assertEquals("acct_test", u.api.me().stripeAccountId)
        val r = u.store.accessToken()!!
        assertEquals("onboarding complete", Regex("\"message\":\"([^\"]+)\"").find(raw.getAuth("/api/v1/payments/onboard/return", r).text())!!.groupValues[1])
        assertEquals(UserRole.BOTH, u.api.me().role)
    }

    @Test
    fun stripeWebhookSignatureIsVerified() {
        val secret = "whsec_test_secret"
        val provider = StripePaymentProvider("sk_test_dummy", secret)
        val payload = """{"id":"evt_1","object":"event","type":"payment_intent.succeeded","data":{"object":{"id":"pi_123","object":"payment_intent"}}}"""
        fun sign(ts: Long, key: String): String {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }
            return "t=$ts,v1=" + mac.doFinal("$ts.$payload".toByteArray()).joinToString("") { "%02x".format(it) }
        }
        val now = System.currentTimeMillis() / 1000
        assertEquals(PaymentEvent.Succeeded("pi_123"), provider.parseWebhook(payload, sign(now, secret)))
        assertFailsWith<WebhookVerificationException> { provider.parseWebhook(payload, sign(now, "falsch")) }
        assertFailsWith<WebhookVerificationException> { provider.parseWebhook(payload, sign(now - 3600, secret)) } // zu alt
        assertFailsWith<WebhookVerificationException> { provider.parseWebhook(payload, null) }
        val refunded = """{"id":"evt_2","object":"event","type":"charge.refunded","data":{"object":{"id":"ch_1","payment_intent":"pi_9"}}}"""
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        val sig = "t=$now,v1=" + mac.doFinal("$now.$refunded".toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(PaymentEvent.Refunded("pi_9"), provider.parseWebhook(refunded, sig))
    }
}
