package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.Booking
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.CancelReasons
import app.chargeshare.shared.model.CreateBookingRequest
import com.stripe.exception.InvalidRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** „Jetzt bezahlen“ für offene Buchungen und der automatische Ablauf unbezahlter Buchungen. */
class PendingBookingApiTest {

    /** Schneller Sweeper (1 s), damit der Hintergrund-Job im Test sichtbar wird. */
    private val fastSweepConfig = testConfig.copy(pendingBookingTtlMinutes = 15, pendingBookingSweepSeconds = 1)

    // ---- Jetzt bezahlen ---------------------------------------------------------------------

    @Test
    fun payingAPendingBookingReturnsTheExistingClientSecret() = serverTest(payments = ScriptedPaymentProvider()) {
        val (driver, booking) = pendingBooking()
        val response = driver.api.payBooking(booking.id)
        assertEquals("${booking.stripePaymentIntentId}_secret", response.clientSecret)
        assertEquals(booking.stripePaymentIntentId, response.booking?.stripePaymentIntentId)
        assertEquals(BookingStatus.PENDING, response.booking?.status)
        assertEquals(1, (payments as ScriptedPaymentProvider).amounts.size, "kein zweiter PaymentIntent")
    }

    @Test
    fun payingWithACancelledIntentCreatesAFreshOne() = serverTest(payments = ScriptedPaymentProvider()) {
        val (driver, booking) = pendingBooking()
        val provider = payments as ScriptedPaymentProvider
        provider.canceledIntents += booking.stripePaymentIntentId.orEmpty()
        val response = driver.api.payBooking(booking.id)
        val fresh = response.booking?.stripePaymentIntentId
        assertNotNull(fresh)
        assertNotEquals(booking.stripePaymentIntentId, fresh)
        assertEquals("${fresh}_secret", response.clientSecret)
        assertEquals(fresh, driver.api.booking(booking.id).stripePaymentIntentId)
        assertEquals(provider.amounts[0], provider.amounts[1], "gleicher Betrag wie beim ersten Intent")
    }

    @Test
    fun payingAnAlreadyPaidIntentConfirmsTheBooking() = serverTest(payments = ScriptedPaymentProvider()) {
        val (driver, booking) = pendingBooking()
        (payments as ScriptedPaymentProvider).paid = true
        val response = driver.api.payBooking(booking.id)
        assertEquals(BookingStatus.CONFIRMED, response.booking?.status)
        assertNull(response.clientSecret)
    }

    @Test
    fun onlyTheDriverCanPayAndOnlyPendingBookings() = serverTest(payments = ScriptedPaymentProvider()) {
        val (driver, booking) = pendingBooking()
        val stranger = registerUser("fremd@example.com")
        assertEquals(403, assertFailsWith<ApiException> { stranger.api.payBooking(booking.id) }.status)
        assertEquals(404, assertFailsWith<ApiException> { driver.api.payBooking("00000000-0000-0000-0000-000000000000") }.status)

        driver.api.cancelBooking(booking.id)
        assertEquals(400, assertFailsWith<ApiException> { driver.api.payBooking(booking.id) }.status)
    }

    // ---- Ablauf -----------------------------------------------------------------------------

    @Test
    fun staleUnpaidBookingExpiresAndClosesTheIntent() = serverTest(config = fastSweepConfig, payments = ScriptedPaymentProvider()) {
        val (driver, stale) = pendingBooking()
        val provider = payments as ScriptedPaymentProvider
        backdateBooking(stale.id, minutesAgo = 16)

        awaitStatus(driver, stale.id, BookingStatus.CANCELLED)
        val expired = driver.api.booking(stale.id)
        assertEquals(CancelReasons.PAYMENT_EXPIRED, expired.cancelReason)
        assertEquals(listOf(stale.stripePaymentIntentId), provider.canceledIntents)
        assertTrue(provider.refunds.isEmpty())

        // Der Slot ist wieder frei.
        val other = registerUser("anderer@example.com")
        val again = other.api.createBooking(CreateBookingRequest(stale.wallboxId, stale.startTime, stale.endTime))
        assertEquals(BookingStatus.PENDING, again.booking?.status)
    }

    @Test
    fun freshUnpaidBookingIsLeftAlone() = serverTest(config = fastSweepConfig, payments = ScriptedPaymentProvider()) {
        val (driver, fresh) = pendingBooking()
        val stale = driver.api.createBooking(CreateBookingRequest(fresh.wallboxId, inFuture(2, 10), inFuture(2, 12))).booking!!
        backdateBooking(stale.id, minutesAgo = 20)
        awaitStatus(driver, stale.id, BookingStatus.CANCELLED)
        assertEquals(BookingStatus.PENDING, driver.api.booking(fresh.id).status)
        assertEquals(listOf(stale.stripePaymentIntentId), (payments as ScriptedPaymentProvider).canceledIntents)
    }

    @Test
    fun expiryStillCancelsWhenTheIntentCannotBeClosed() = serverTest(
        config = fastSweepConfig,
        payments = ScriptedPaymentProvider().also {
            it.cancelIntentFailure = InvalidRequestException("no such payment_intent", null, "req_9", null, 404, null)
        },
    ) {
        val (driver, stale) = pendingBooking()
        backdateBooking(stale.id, minutesAgo = 30)
        awaitStatus(driver, stale.id, BookingStatus.CANCELLED)
    }

    @Test
    fun expiryKeepsTheBookingWhenThePaymentStatusIsUnknown() = serverTest(
        config = fastSweepConfig,
        payments = ScriptedPaymentProvider().also { it.isPaidFailure = IllegalStateException("stripe unreachable") },
    ) {
        val (driver, stale) = pendingBooking()
        backdateBooking(stale.id, minutesAgo = 30)
        withContext(Dispatchers.Default) { delay(3_500) } // mehrere Durchläufe
        assertEquals(BookingStatus.PENDING, driver.api.booking(stale.id).status)

        // Sobald Stripe wieder antwortet, läuft die Buchung ab.
        (payments as ScriptedPaymentProvider).isPaidFailure = null
        awaitStatus(driver, stale.id, BookingStatus.CANCELLED)
    }

    @Test
    fun expiryConfirmsABookingThatWasPaidInTheMeantime() = serverTest(config = fastSweepConfig, payments = ScriptedPaymentProvider()) {
        val (driver, stale) = pendingBooking()
        (payments as ScriptedPaymentProvider).paid = true
        backdateBooking(stale.id, minutesAgo = 30)
        awaitStatus(driver, stale.id, BookingStatus.CONFIRMED)
        assertTrue(payments.refunds.isEmpty())
    }

    @Test
    fun expiryCanBeSwitchedOff() = serverTest(
        config = fastSweepConfig.copy(pendingBookingTtlMinutes = 0),
        payments = ScriptedPaymentProvider(),
    ) {
        val (driver, stale) = pendingBooking()
        backdateBooking(stale.id, minutesAgo = 600)
        withContext(Dispatchers.Default) { delay(2_500) }
        assertEquals(BookingStatus.PENDING, driver.api.booking(stale.id).status)
    }

    private suspend fun ServerScope.awaitStatus(driver: TestClient, bookingId: String, expected: BookingStatus) {
        repeat(AWAIT_ATTEMPTS) {
            if (driver.api.booking(bookingId).status == expected) return
            withContext(Dispatchers.Default) { delay(AWAIT_STEP_MS) }
        }
        assertEquals(expected, driver.api.booking(bookingId).status)
    }

    private suspend fun ServerScope.pendingBooking(): Pair<TestClient, Booking> {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox("Marktplatz-Box", power = 11.0, price = 0.40))
        host.api.setWallboxOnline(wallbox.id, true)
        val driver = registerUser("fahrer@example.com")
        val booking = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        return driver to booking
    }

    private companion object {
        const val AWAIT_ATTEMPTS = 40
        const val AWAIT_STEP_MS = 250L
    }
}
