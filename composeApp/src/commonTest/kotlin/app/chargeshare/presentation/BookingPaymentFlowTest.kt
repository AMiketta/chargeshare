package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.BookingCreation
import app.chargeshare.data.Outcome
import app.chargeshare.payment.PaymentMethodChoice
import app.chargeshare.payment.PaymentResult
import app.chargeshare.payment.UnsupportedPaymentLauncher
import app.chargeshare.payment.payCatching
import app.chargeshare.payment.PaymentRequest
import app.chargeshare.presentation.booking.BookingFlowViewModel
import app.chargeshare.presentation.booking.BookingSubmitState
import app.chargeshare.presentation.booking.PaymentIssue
import app.chargeshare.presentation.booking.PaymentSetup
import app.chargeshare.presentation.booking.PaymentStep
import app.chargeshare.presentation.booking.PaymentUnavailableReason
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.testing.FakeBookingRepository
import app.chargeshare.testing.FakePaymentRepository
import app.chargeshare.testing.FakeWallboxRepository
import app.chargeshare.testing.FixedClock
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.fakePaymentConfig
import app.chargeshare.testing.stripePaymentConfig
import app.chargeshare.testing.testBooking
import app.chargeshare.testing.testWallbox
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Zahlung im Buchungsflow: Zahlungsart wählen, Zahlungsfenster, Abbruch, Fehler, Bestätigung, Verwerfen. */
class BookingPaymentFlowTest {
    private val wallboxes = FakeWallboxRepository()
    private val bookings = FakeBookingRepository()
    private val payments = FakePaymentRepository(Outcome.Success(stripePaymentConfig()))
    private val today = LocalDate(2026, 10, 5)
    private val ten = Instant.parse("2026-10-05T10:00:00Z")
    private val pendingBooking = testBooking(id = "b-pending", status = BookingStatus.PENDING)

    @BeforeTest
    fun setUp() {
        TestMain.install()
        wallboxes.slotsByDay[today] = Outcome.Success(listOf(0, 30, 60, 90).map { ten + it.minutes })
        bookings.createResult = Outcome.Success(BookingCreation(pendingBooking, clientSecret = "pi_123_secret_abc"))
    }

    @AfterTest fun tearDown() = TestMain.uninstall()

    private fun viewModel(
        methods: List<PaymentMethodChoice> = listOf(PaymentMethodChoice.GOOGLE_PAY, PaymentMethodChoice.CARD),
    ) = BookingFlowViewModel(
        wallbox = testWallbox(),
        wallboxRepository = wallboxes,
        bookingRepository = bookings,
        paymentRepository = payments,
        supportedPaymentMethods = methods,
        clock = FixedClock(),
        zone = TimeZone.UTC,
    ).also { it.onStartSelected(ten) }

    /** Bucht und öffnet das Zahlungsfenster, wie es die UI nach `PaymentRequired` tut. */
    private fun BookingFlowViewModel.submitAndOpenSheet(): PaymentRequest {
        onSubmit()
        val required = assertIs<BookingSubmitState.PaymentRequired>(state.value.submit)
        onPaymentSheetOpened()
        return required.request
    }

    // ---- Zahlungsart-Auswahl ------------------------------------------------------------------

    @Test
    fun stripeOffersPlatformMethodsAndPreselectsTheFirst() {
        val state = viewModel().state.value
        val setup = assertIs<PaymentSetup.Ready>(state.paymentSetup)
        assertEquals(listOf(PaymentMethodChoice.GOOGLE_PAY, PaymentMethodChoice.CARD), setup.methods)
        assertEquals(PaymentMethodChoice.GOOGLE_PAY, state.selectedPaymentMethod)
        assertTrue(state.canSubmit)
    }

    @Test
    fun selectingCardChangesTheMethodOfTheNextPaymentRequest() {
        val viewModel = viewModel()
        viewModel.onPaymentMethodSelected(PaymentMethodChoice.CARD)
        assertEquals(PaymentMethodChoice.CARD, viewModel.submitAndOpenSheet().method)
    }

    @Test
    fun applePayIsHiddenWhenServerHasNoMerchantId() {
        payments.configResult = Outcome.Success(stripePaymentConfig(applePayMerchantId = null))
        val viewModel = viewModel(methods = listOf(PaymentMethodChoice.APPLE_PAY, PaymentMethodChoice.CARD))
        val setup = assertIs<PaymentSetup.Ready>(viewModel.state.value.paymentSetup)
        assertEquals(listOf(PaymentMethodChoice.CARD), setup.methods)
    }

    @Test
    fun applePayIsOfferedWithMerchantId() {
        val viewModel = viewModel(methods = listOf(PaymentMethodChoice.APPLE_PAY, PaymentMethodChoice.CARD))
        assertEquals(PaymentMethodChoice.APPLE_PAY, viewModel.state.value.selectedPaymentMethod)
    }

    @Test
    fun platformWithoutWalletsCannotBookWhenStripeIsActive() {
        val state = viewModel(methods = emptyList()).state.value
        assertEquals(PaymentSetup.Unavailable(PaymentUnavailableReason.PLATFORM_NOT_SUPPORTED), state.paymentSetup)
        assertFalse(state.canSubmit)
    }

    @Test
    fun missingPublishableKeyOnServerBlocksBooking() {
        payments.configResult = Outcome.Success(stripePaymentConfig().copy(publishableKey = null))
        val state = viewModel().state.value
        assertEquals(PaymentSetup.Unavailable(PaymentUnavailableReason.SERVER_NOT_CONFIGURED), state.paymentSetup)
        assertFalse(state.canSubmit)
    }

    @Test
    fun fakeModeNeedsNoPaymentMethodAndBooksImmediately() {
        payments.configResult = Outcome.Success(fakePaymentConfig())
        bookings.createResult = Outcome.Success(BookingCreation(testBooking(id = "fake"), clientSecret = "pi_fake_secret_fake"))
        val viewModel = viewModel(methods = emptyList())
        assertEquals(PaymentSetup.TestPayment, viewModel.state.value.paymentSetup)
        assertTrue(viewModel.state.value.canSubmit)
        viewModel.onSubmit()
        assertEquals("fake", assertIs<BookingSubmitState.Success>(viewModel.state.value.submit).booking.id)
        assertTrue(bookings.confirmedPayments.isEmpty())
    }

    @Test
    fun configLoadFailureCanBeRetried() {
        payments.configResult = failure(AppError.Network)
        val viewModel = viewModel()
        assertEquals(PaymentSetup.LoadFailed(AppError.Network), viewModel.state.value.paymentSetup)
        assertFalse(viewModel.state.value.canSubmit)
        payments.configResult = Outcome.Success(stripePaymentConfig())
        viewModel.onRetryPaymentConfig()
        assertIs<PaymentSetup.Ready>(viewModel.state.value.paymentSetup)
        assertEquals(2, payments.configCalls)
    }

    // ---- Zahlungsablauf -----------------------------------------------------------------------

    @Test
    fun stripeBookingRequestsPaymentSheetWithClientSecretAndConfig() {
        val viewModel = viewModel()
        viewModel.onSubmit()
        val required = assertIs<BookingSubmitState.PaymentRequired>(viewModel.state.value.submit)
        assertEquals("b-pending", required.booking.id)
        assertEquals("pi_123_secret_abc", required.request.clientSecret)
        assertEquals("pk_test_123", required.request.config.publishableKey)
        assertEquals(PaymentMethodChoice.GOOGLE_PAY, required.request.method)
        assertFalse(viewModel.state.value.canSubmit, "während der Zahlung darf nicht erneut gebucht werden")
    }

    @Test
    fun completedPaymentConfirmsBookingOnServer() {
        bookings.confirmPaymentResult = Outcome.Success(testBooking(id = "b-pending", status = BookingStatus.CONFIRMED))
        val viewModel = viewModel()
        viewModel.submitAndOpenSheet()
        assertEquals(
            PaymentStep.SHEET_OPEN,
            assertIs<BookingSubmitState.PaymentInProgress>(viewModel.state.value.submit).step,
        )
        viewModel.onPaymentResult(PaymentResult.Completed)
        assertEquals(listOf("b-pending"), bookings.confirmedPayments)
        val success = assertIs<BookingSubmitState.Success>(viewModel.state.value.submit)
        assertEquals(BookingStatus.CONFIRMED, success.booking.status)
    }

    @Test
    fun canceledPaymentKeepsBookingPendingAndOffersRetry() {
        val viewModel = viewModel()
        val request = viewModel.submitAndOpenSheet()
        viewModel.onPaymentResult(PaymentResult.Canceled)
        val incomplete = assertIs<BookingSubmitState.PaymentIncomplete>(viewModel.state.value.submit)
        assertEquals(PaymentIssue.Canceled, incomplete.issue)
        assertTrue(bookings.confirmedPayments.isEmpty())
        assertTrue(bookings.cancelled.isEmpty())

        viewModel.onRetryPayment()
        assertEquals(request, assertIs<BookingSubmitState.PaymentRequired>(viewModel.state.value.submit).request)
    }

    @Test
    fun failedPaymentShowsIssueWithoutServerCall() {
        val viewModel = viewModel()
        viewModel.submitAndOpenSheet()
        viewModel.onPaymentResult(PaymentResult.Failed(reason = "card_declined"))
        assertEquals(PaymentIssue.Failed, assertIs<BookingSubmitState.PaymentIncomplete>(viewModel.state.value.submit).issue)
        assertTrue(bookings.confirmedPayments.isEmpty())
    }

    @Test
    fun serverRejectingConfirmationLetsUserCheckPaymentAgain() {
        bookings.confirmPaymentResult = failure(AppError.PaymentRequired)
        val viewModel = viewModel()
        viewModel.submitAndOpenSheet()
        viewModel.onPaymentResult(PaymentResult.Completed)
        val incomplete = assertIs<BookingSubmitState.PaymentIncomplete>(viewModel.state.value.submit)
        assertEquals(PaymentIssue.ConfirmationFailed(AppError.PaymentRequired), incomplete.issue)

        bookings.confirmPaymentResult = Outcome.Success(testBooking(id = "b-pending", status = BookingStatus.CONFIRMED))
        viewModel.onRetryPayment()
        assertEquals(listOf("b-pending", "b-pending"), bookings.confirmedPayments)
        assertIs<BookingSubmitState.Success>(viewModel.state.value.submit)
    }

    @Test
    fun discardingCancelsPendingBookingAndFreesSlotSelection() {
        val viewModel = viewModel()
        viewModel.submitAndOpenSheet()
        viewModel.onPaymentResult(PaymentResult.Canceled)
        viewModel.onDiscardBooking()
        assertEquals(listOf("b-pending"), bookings.cancelled)
        val state = viewModel.state.value
        assertEquals(BookingSubmitState.Idle, state.submit)
        assertNull(state.selectedStart)
    }

    @Test
    fun failedDiscardKeepsPaymentOptionsAndReportsError() {
        bookings.cancelResult = failure(AppError.Network)
        val viewModel = viewModel()
        viewModel.submitAndOpenSheet()
        viewModel.onPaymentResult(PaymentResult.Canceled)
        viewModel.onDiscardBooking()
        val incomplete = assertIs<BookingSubmitState.PaymentIncomplete>(viewModel.state.value.submit)
        assertEquals(AppError.Network, incomplete.discardError)
    }

    @Test
    fun resultWithoutOpenSheetIsIgnored() {
        val viewModel = viewModel()
        viewModel.onPaymentResult(PaymentResult.Completed)
        assertEquals(BookingSubmitState.Idle, viewModel.state.value.submit)
        assertTrue(bookings.confirmedPayments.isEmpty())
    }

    @Test
    fun pendingBookingWithoutClientSecretFailsInsteadOfOpeningSheet() {
        bookings.createResult = Outcome.Success(BookingCreation(pendingBooking, clientSecret = null))
        val viewModel = viewModel()
        viewModel.onSubmit()
        assertEquals(BookingSubmitState.Failed(AppError.Unknown), viewModel.state.value.submit)
    }

    // ---- PaymentLauncher ----------------------------------------------------------------------

    @Test
    fun unsupportedLauncherOffersNothingAndFails() = runTest {
        assertTrue(UnsupportedPaymentLauncher.supportedMethods.isEmpty())
        val request = PaymentRequest("secret", stripePaymentConfig(), PaymentMethodChoice.CARD)
        assertIs<PaymentResult.Failed>(UnsupportedPaymentLauncher.pay(request))
    }

    @Test
    fun launcherExceptionsBecomeFailedResults() = runTest {
        val throwing = object : app.chargeshare.payment.PaymentLauncher {
            override val supportedMethods = listOf(PaymentMethodChoice.CARD)
            override suspend fun pay(request: PaymentRequest): PaymentResult = error("SDK kaputt")
        }
        val result = throwing.payCatching(PaymentRequest("secret", stripePaymentConfig(), PaymentMethodChoice.CARD))
        assertEquals(PaymentResult.Failed("SDK kaputt"), result)
    }
}
