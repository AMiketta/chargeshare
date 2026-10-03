package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.ErrorCodes
import com.stripe.exception.ApiConnectionException
import com.stripe.exception.AuthenticationException
import com.stripe.exception.InvalidRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stripe-Fehler beim Connect-Onboarding und das Schließen offener PaymentIntents beim Storno. */
class PaymentErrorHandlingApiTest {

    private val stripeConfig = testConfig.copy(stripeSecretKey = "sk_test_geheim", stripePublishableKey = "pk_test_oeffentlich")

    private val connectNotEnabled = InvalidRequestException(
        "You can only create new accounts if you've signed up for Connect, which you can learn how to do at https://stripe.com/docs/connect. sk_test_geheim",
        null, "req_1", null, 400, null,
    )

    @Test
    fun onboardingWithoutStripeConnectReturnsReadableServiceUnavailable() = serverTest(
        config = stripeConfig,
        payments = ScriptedPaymentProvider().also { it.onboardingFailure = connectNotEnabled },
    ) {
        val user = registerUser("host@example.com", host = true)
        val error = assertFailsWith<ApiException> { user.api.startPayoutOnboarding() }
        assertEquals(503, error.status)
        assertEquals(ErrorCodes.INTERNAL_ERROR, error.code)
        assertEquals("Stripe Connect ist für dieses Konto nicht aktiviert. Auszahlungen sind noch nicht möglich.", error.message)

        val rawBody = raw.postJson("/api/v1/payments/onboard", "", user.store.accessToken()).text()
        assertFalse("sk_test_geheim" in rawBody, "Stripe-Rohtext und Schlüssel dürfen nicht durchgereicht werden")
        assertFalse("signed up for Connect" in rawBody)
    }

    @Test
    fun onboardingWithInvalidApiKeyReturnsBadGatewayWithoutStripeText() = serverTest(
        config = stripeConfig,
        payments = ScriptedPaymentProvider().also {
            it.onboardingFailure = AuthenticationException("Invalid API Key provided: sk_test_***eheim", "req_2", "api_key_invalid", 401)
        },
    ) {
        val user = registerUser("host@example.com", host = true)
        val error = assertFailsWith<ApiException> { user.api.startPayoutOnboarding() }
        assertEquals(502, error.status)
        assertEquals(ErrorCodes.INTERNAL_ERROR, error.code)
        assertFalse("sk_test" in error.message)
        assertFalse("API Key" in error.message)
    }

    @Test
    fun onboardingWhenStripeIsUnreachableReturnsServiceUnavailable() = serverTest(
        config = stripeConfig,
        payments = ScriptedPaymentProvider().also { it.onboardingFailure = ApiConnectionException("timeout") },
    ) {
        val user = registerUser("host@example.com", host = true)
        assertEquals(503, assertFailsWith<ApiException> { user.api.startPayoutOnboarding() }.status)
    }

    @Test
    fun onboardingWithUnknownStripeErrorReturnsGenericBadGateway() = serverTest(
        config = stripeConfig,
        payments = ScriptedPaymentProvider().also {
            it.onboardingFailure = InvalidRequestException("Something else went wrong", null, "req_3", null, 400, null)
        },
    ) {
        val user = registerUser("host@example.com", host = true)
        val error = assertFailsWith<ApiException> { user.api.startPayoutOnboarding() }
        assertEquals(502, error.status)
        assertFalse("Something else" in error.message)
    }

    @Test
    fun cancellingPendingBookingClosesThePaymentIntent() = serverTest(config = stripeConfig, payments = ScriptedPaymentProvider()) {
        val (driver, booking) = pendingBooking()
        driver.api.cancelBooking(booking.id)
        val provider = payments as ScriptedPaymentProvider
        assertEquals(listOf(booking.stripePaymentIntentId), provider.canceledIntents)
        assertTrue(provider.refunds.isEmpty(), "unbezahlte Buchungen werden nicht erstattet")
        assertEquals(BookingStatus.CANCELLED, driver.api.booking(booking.id).status)
    }

    @Test
    fun cancelStillSucceedsWhenTheIntentCannotBeCancelledAnymore() = serverTest(
        config = stripeConfig,
        payments = ScriptedPaymentProvider().also {
            it.cancelIntentFailure = InvalidRequestException("PaymentIntent already succeeded", null, "req_4", null, 400, null)
        },
    ) {
        val (driver, booking) = pendingBooking()
        driver.api.cancelBooking(booking.id)
        assertEquals(BookingStatus.CANCELLED, driver.api.booking(booking.id).status)
        // Der Slot ist wieder frei und buchbar.
        val other = registerUser("anderer@example.com")
        val again = other.api.createBooking(CreateBookingRequest(booking.wallboxId, inFuture(1, 10), inFuture(1, 12)))
        assertEquals(BookingStatus.PENDING, again.booking?.status)
    }

    @Test
    fun cancellingPaidBookingRefundsInsteadOfCancellingTheIntent() = serverTest(config = stripeConfig, payments = ScriptedPaymentProvider()) {
        val (driver, booking) = pendingBooking()
        val provider = payments as ScriptedPaymentProvider
        provider.paid = true
        driver.api.confirmPayment(booking.id)
        driver.api.cancelBooking(booking.id)
        assertEquals(listOf(booking.stripePaymentIntentId), provider.refunds)
        assertTrue(provider.canceledIntents.isEmpty())
    }

    private suspend fun ServerScope.pendingBooking(): Pair<TestClient, app.chargeshare.shared.model.Booking> {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox("Marktplatz-Box", power = 11.0, price = 0.40))
        host.api.setWallboxOnline(wallbox.id, true)
        val driver = registerUser("fahrer@example.com")
        val booking = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        return driver to booking
    }
}
