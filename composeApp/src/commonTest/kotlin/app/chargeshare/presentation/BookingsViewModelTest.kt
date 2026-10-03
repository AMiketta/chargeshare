package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.Outcome
import app.chargeshare.presentation.booking.BookingFilter
import app.chargeshare.presentation.booking.BookingsViewModel
import app.chargeshare.presentation.booking.isCancellable
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.testing.FakeBookingRepository
import app.chargeshare.testing.FixedClock
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.testBooking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class BookingsViewModelTest {
    private val repository = FakeBookingRepository()
    private val upcoming = testBooking("up", BookingStatus.CONFIRMED)
    private val past = testBooking(
        "past",
        BookingStatus.COMPLETED,
        start = Instant.parse("2026-10-01T10:00:00Z"),
        end = Instant.parse("2026-10-01T12:00:00Z"),
    )
    private val cancelled = testBooking("c", BookingStatus.CANCELLED)

    @BeforeTest fun setUp() = TestMain.install()

    @AfterTest fun tearDown() = TestMain.uninstall()

    private fun viewModel() = BookingsViewModel(repository, FixedClock()).also {
        repository.listResult = Outcome.Success(listOf(past, upcoming, cancelled))
        it.refresh()
    }

    @Test
    fun upcomingTabShowsOnlyActiveFutureBookings() {
        assertEquals(listOf("up"), viewModel().state.value.visibleBookings.map { it.id })
    }

    @Test
    fun pastTabShowsFinishedAndCancelledBookings() {
        val viewModel = viewModel()
        viewModel.onFilterChange(BookingFilter.Past)
        assertEquals(setOf("past", "c"), viewModel.state.value.visibleBookings.map { it.id }.toSet())
    }

    @Test
    fun loadErrorIsExposed() {
        repository.listResult = failure(AppError.Network)
        val viewModel = BookingsViewModel(repository, FixedClock())
        viewModel.refresh()
        assertEquals(AppError.Network, viewModel.state.value.error)
    }

    @Test
    fun cancelRequiresConfirmationBeforeCallingServer() {
        val viewModel = viewModel()
        viewModel.onCancelRequested(upcoming)
        assertNotNull(viewModel.state.value.cancelCandidate)
        assertTrue(repository.cancelled.isEmpty())
        viewModel.onCancelDismissed()
        assertNull(viewModel.state.value.cancelCandidate)
        assertTrue(repository.cancelled.isEmpty())
    }

    @Test
    fun confirmedCancelUpdatesBookingAndKeepsWallboxName() {
        val viewModel = viewModel()
        viewModel.onCancelRequested(upcoming)
        viewModel.onCancelConfirmed()
        assertEquals(listOf("up"), repository.cancelled)
        val updated = viewModel.state.value.bookings.first { it.id == "up" }
        assertEquals(BookingStatus.CANCELLED, updated.status)
        assertNotNull(updated.wallbox)
        assertTrue(viewModel.state.value.visibleBookings.isEmpty())
        assertNull(viewModel.state.value.cancellingId)
    }

    @Test
    fun failedCancelShowsErrorAndKeepsBooking() {
        repository.cancelResult = failure(AppError.Server)
        val viewModel = viewModel()
        viewModel.onCancelRequested(upcoming)
        viewModel.onCancelConfirmed()
        assertEquals(AppError.Server, viewModel.state.value.cancelError)
        assertEquals(BookingStatus.CONFIRMED, viewModel.state.value.bookings.first { it.id == "up" }.status)
    }

    @Test
    fun onlyPendingOrConfirmedBookingsAreCancellable() {
        assertTrue(testBooking(status = BookingStatus.PENDING).isCancellable())
        assertTrue(testBooking(status = BookingStatus.CONFIRMED).isCancellable())
        assertFalse(testBooking(status = BookingStatus.ACTIVE).isCancellable())
        assertFalse(testBooking(status = BookingStatus.COMPLETED).isCancellable())
        assertFalse(testBooking(status = BookingStatus.CANCELLED).isCancellable())
    }
}
