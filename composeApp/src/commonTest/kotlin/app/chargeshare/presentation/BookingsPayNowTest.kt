package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.BookingCreation
import app.chargeshare.data.Outcome
import app.chargeshare.payment.PaymentMethodChoice
import app.chargeshare.payment.PaymentResult
import app.chargeshare.presentation.booking.BookingsViewModel
import app.chargeshare.presentation.booking.PayNowState
import app.chargeshare.presentation.booking.PaymentIssue
import app.chargeshare.presentation.booking.PaymentStep
import app.chargeshare.presentation.booking.PaymentUnavailableReason
import app.chargeshare.presentation.booking.isPayable
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.testing.FakeBookingRepository
import app.chargeshare.testing.FakePaymentRepository
import app.chargeshare.testing.FixedClock
import app.chargeshare.testing.NOW
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.fakePaymentConfig
import app.chargeshare.testing.stripePaymentConfig
import app.chargeshare.testing.testBooking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** „Jetzt bezahlen“ für offene Buchungen in der Buchungsliste. */
class BookingsPayNowTest {
    private val bookings = FakeBookingRepository()
    private val payments = FakePaymentRepository(Outcome.Success(stripePaymentConfig()))
    private val pending = testBooking("pend", BookingStatus.PENDING)
    private val paid = testBooking("pend", BookingStatus.CONFIRMED)

    @BeforeTest
    fun setUp() {
        TestMain.install()
        bookings.listResult = Outcome.Success(listOf(pending))
        bookings.startPaymentResult = Outcome.Success(BookingCreation(pending, clientSecret = "pi_1_secret_x"))
        bookings.confirmPaymentResult = Outcome.Success(paid.copy(wallbox = null))
    }

    @AfterTest fun tearDown() = TestMain.uninstall()

    private fun viewModel(methods: List<PaymentMethodChoice> = listOf(PaymentMethodChoice.GOOGLE_PAY, PaymentMethodChoice.CARD)) =
        BookingsViewModel(bookings, payments, methods, FixedClock()).also { it.refresh() }

    @Test
    fun payNowIsOnlyOfferedWhereThePlatformCanPay() {
        assertTrue(viewModel().state.value.canPayOnline)
        assertFalse(viewModel(methods = emptyList()).state.value.canPayOnline)
    }

    @Test
    fun onlyUnfinishedPendingBookingsArePayable() {
        assertTrue(pending.isPayable(NOW))
        assertFalse(paid.isPayable(NOW))
        assertFalse(pending.isPayable(pending.endTime + 1.hours))
    }

    @Test
    fun payNowAsksForThePaymentSheetWithTheFirstMethod() {
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        val state = assertIs<PayNowState.PaymentRequired>(viewModel.state.value.payNow)
        assertEquals(listOf("pend"), bookings.startedPayments)
        assertEquals("pi_1_secret_x", state.request.clientSecret)
        assertEquals(PaymentMethodChoice.GOOGLE_PAY, state.request.method)
    }

    @Test
    fun successfulPaymentConfirmsOnTheServerAndUpdatesTheList() {
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        viewModel.onPaymentSheetOpened()
        assertEquals(PaymentStep.SHEET_OPEN, assertIs<PayNowState.InProgress>(viewModel.state.value.payNow).step)
        viewModel.onPaymentResult(PaymentResult.Completed)
        assertEquals(listOf("pend"), bookings.confirmedPayments)
        val state = viewModel.state.value
        assertEquals(PayNowState.Idle, state.payNow)
        val updated = state.bookings.single()
        assertEquals(BookingStatus.CONFIRMED, updated.status)
        assertNotNull(updated.wallbox, "Wallbox der Liste bleibt erhalten")
    }

    @Test
    fun canceledSheetCanBeRetried() {
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        viewModel.onPaymentSheetOpened()
        viewModel.onPaymentResult(PaymentResult.Canceled)
        assertEquals(PaymentIssue.Canceled, assertIs<PayNowState.Incomplete>(viewModel.state.value.payNow).issue)
        assertTrue(bookings.confirmedPayments.isEmpty())

        viewModel.onRetryPayNow()
        assertIs<PayNowState.PaymentRequired>(viewModel.state.value.payNow)
        assertEquals(listOf("pend", "pend"), bookings.startedPayments)
    }

    @Test
    fun failedSheetIsReportedAsFailed() {
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        viewModel.onPaymentSheetOpened()
        viewModel.onPaymentResult(PaymentResult.Failed("card_declined"))
        assertEquals(PaymentIssue.Failed, assertIs<PayNowState.Incomplete>(viewModel.state.value.payNow).issue)
    }

    @Test
    fun unconfirmedPaymentRetriesOnlyTheConfirmation() {
        bookings.confirmPaymentResult = failure(AppError.PaymentRequired)
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        viewModel.onPaymentSheetOpened()
        viewModel.onPaymentResult(PaymentResult.Completed)
        val incomplete = assertIs<PayNowState.Incomplete>(viewModel.state.value.payNow)
        assertEquals(PaymentIssue.ConfirmationFailed(AppError.PaymentRequired), incomplete.issue)

        bookings.confirmPaymentResult = Outcome.Success(paid)
        viewModel.onRetryPayNow()
        assertEquals(listOf("pend", "pend"), bookings.confirmedPayments)
        assertEquals(listOf("pend"), bookings.startedPayments, "kein neues Zahlungsfenster")
        assertEquals(BookingStatus.CONFIRMED, viewModel.state.value.bookings.single().status)
    }

    @Test
    fun alreadyPaidBookingIsJustRefreshed() {
        bookings.startPaymentResult = Outcome.Success(BookingCreation(paid, clientSecret = null))
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        assertEquals(PayNowState.Idle, viewModel.state.value.payNow)
        assertEquals(BookingStatus.CONFIRMED, viewModel.state.value.bookings.single().status)
    }

    @Test
    fun serverErrorWhileStartingIsShownAndRetryable() {
        bookings.startPaymentResult = failure(AppError.Server)
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        assertEquals(AppError.Server, assertIs<PayNowState.PrepareFailed>(viewModel.state.value.payNow).error)

        bookings.startPaymentResult = Outcome.Success(BookingCreation(pending, "pi_2_secret_y"))
        viewModel.onRetryPayNow()
        assertEquals("pi_2_secret_y", assertIs<PayNowState.PaymentRequired>(viewModel.state.value.payNow).request.clientSecret)
    }

    @Test
    fun noLongerPayableBookingReloadsTheList() {
        bookings.startPaymentResult = failure(AppError.Validation("booking is not awaiting payment"))
        val viewModel = viewModel()
        bookings.listResult = Outcome.Success(listOf(testBooking("pend", BookingStatus.CANCELLED)))
        viewModel.onPayNow(pending)
        assertIs<PayNowState.PrepareFailed>(viewModel.state.value.payNow)
        assertEquals(BookingStatus.CANCELLED, viewModel.state.value.bookings.single().status)
    }

    @Test
    fun configErrorIsShownWithoutCallingTheBookingEndpoint() {
        payments.configResult = failure(AppError.Network)
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        assertEquals(AppError.Network, assertIs<PayNowState.PrepareFailed>(viewModel.state.value.payNow).error)
        assertTrue(bookings.startedPayments.isEmpty())
    }

    @Test
    fun serverWithoutPublishableKeyIsReportedAsUnavailable() {
        payments.configResult = Outcome.Success(stripePaymentConfig().copy(publishableKey = null))
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        assertEquals(PaymentUnavailableReason.SERVER_NOT_CONFIGURED, assertIs<PayNowState.Unavailable>(viewModel.state.value.payNow).reason)
        assertTrue(bookings.startedPayments.isEmpty())
    }

    @Test
    fun testPaymentModeWithPendingBookingIsAnError() {
        payments.configResult = Outcome.Success(fakePaymentConfig())
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        assertIs<PayNowState.PrepareFailed>(viewModel.state.value.payNow)
    }

    @Test
    fun payNowIsIgnoredWithoutPaymentSupport() {
        val viewModel = viewModel(methods = emptyList())
        viewModel.onPayNow(pending)
        assertEquals(PayNowState.Idle, viewModel.state.value.payNow)
        assertTrue(bookings.startedPayments.isEmpty())
    }

    @Test
    fun cancelIsBlockedWhileThePaymentRuns() {
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        viewModel.onCancelRequested(pending)
        assertEquals(null, viewModel.state.value.cancelCandidate)
        viewModel.onPaymentSheetOpened()
        viewModel.onPaymentResult(PaymentResult.Canceled)
        viewModel.onCancelRequested(pending)
        assertEquals(pending, viewModel.state.value.cancelCandidate)
    }

    @Test
    fun dismissingClearsTheIssue() {
        val viewModel = viewModel()
        viewModel.onPayNow(pending)
        viewModel.onPaymentSheetOpened()
        viewModel.onPaymentResult(PaymentResult.Canceled)
        viewModel.onPayNowDismissed()
        assertEquals(PayNowState.Idle, viewModel.state.value.payNow)
    }
}
