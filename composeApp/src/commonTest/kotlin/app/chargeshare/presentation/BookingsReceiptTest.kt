package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.Outcome
import app.chargeshare.presentation.booking.BookingsViewModel
import app.chargeshare.presentation.booking.ReceiptState
import app.chargeshare.presentation.booking.hasReceipt
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.testing.FakeBookingRepository
import app.chargeshare.testing.FakePaymentRepository
import app.chargeshare.testing.FixedClock
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.testBooking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** „Quittung (PDF)“ in der Buchungsliste. */
class BookingsReceiptTest {
    private val bookings = FakeBookingRepository()
    private val completed = testBooking("booking-12345678", BookingStatus.COMPLETED)

    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    private fun viewModel() = BookingsViewModel(bookings, FakePaymentRepository(), clock = FixedClock())

    @Test
    fun onlyCompletedBookingsHaveAReceipt() {
        assertTrue(completed.hasReceipt())
        assertFalse(testBooking(status = BookingStatus.CONFIRMED).hasReceipt())
        assertFalse(testBooking(status = BookingStatus.CANCELLED).hasReceipt())
    }

    @Test
    fun requestingAReceiptOfAnOpenBookingDoesNotCallTheServer() {
        val viewModel = viewModel()
        viewModel.onReceiptRequested(testBooking(status = BookingStatus.CONFIRMED))
        assertTrue(bookings.receiptRequests.isEmpty())
        assertEquals(ReceiptState.Idle, viewModel.state.value.receipt)
    }

    @Test
    fun loadedReceiptIsReadyWithAFileNameAndGoesBackToIdleOnceDelivered() {
        val viewModel = viewModel()
        viewModel.onReceiptRequested(completed)
        val ready = assertIs<ReceiptState.Ready>(viewModel.state.value.receipt)
        assertEquals("Quittung-ChargeShare-booking-.pdf", ready.fileName)
        assertEquals(listOf(completed.id), bookings.receiptRequests)

        viewModel.onReceiptHandled(delivered = true)
        assertEquals(ReceiptState.Idle, viewModel.state.value.receipt)
    }

    @Test
    fun receiptThatCannotBeOpenedOnTheDeviceIsReportedWithoutServerError() {
        val viewModel = viewModel()
        viewModel.onReceiptRequested(completed)
        viewModel.onReceiptHandled(delivered = false)
        val failed = assertIs<ReceiptState.Failed>(viewModel.state.value.receipt)
        assertEquals(null, failed.error)
        viewModel.onReceiptErrorShown()
        assertEquals(ReceiptState.Idle, viewModel.state.value.receipt)
    }

    @Test
    fun serverErrorIsKeptUntilTheUserDismissesIt() {
        bookings.receiptResult = failure(AppError.Conflict)
        val viewModel = viewModel()
        viewModel.onReceiptRequested(completed)
        val failed = assertIs<ReceiptState.Failed>(viewModel.state.value.receipt)
        assertEquals(AppError.Conflict, failed.error)
    }

    @Test
    fun receiptBytesAreHandedOverUnchanged() {
        val pdf = "%PDF-1.4 abc".encodeToByteArray()
        bookings.receiptResult = Outcome.Success(pdf)
        val viewModel = viewModel()
        viewModel.onReceiptRequested(completed)
        assertEquals(pdf.toList(), assertIs<ReceiptState.Ready>(viewModel.state.value.receipt).pdf.toList())
    }
}
