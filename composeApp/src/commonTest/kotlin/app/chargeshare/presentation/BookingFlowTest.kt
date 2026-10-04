package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.BookingCreation
import app.chargeshare.data.Outcome
import app.chargeshare.data.SLOT_MINUTES
import app.chargeshare.presentation.booking.BookingFlowViewModel
import app.chargeshare.presentation.booking.BookingSubmitState
import app.chargeshare.presentation.booking.SlotPlanner
import app.chargeshare.testing.FakeBookingRepository
import app.chargeshare.testing.FakePaymentRepository
import app.chargeshare.testing.FakeWallboxRepository
import app.chargeshare.testing.FixedClock
import app.chargeshare.testing.TestMain
import app.chargeshare.testing.failure
import app.chargeshare.testing.testBooking
import app.chargeshare.testing.testWallbox
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class BookingFlowTest {
    private val wallboxes = FakeWallboxRepository()
    private val bookings = FakeBookingRepository()
    private val payments = FakePaymentRepository()
    private val wallbox = testWallbox(powerKw = 11.0, pricePerKwh = 0.34)
    private val today = LocalDate(2026, 10, 5)
    private val ten = Instant.parse("2026-10-05T10:00:00Z")

    @BeforeTest
    fun setUp() {
        TestMain.install()
        // Freie Slots 10:00–11:30 (4 Stück) und 13:00
        wallboxes.slotsByDay[today] = Outcome.Success(
            listOf(0, 30, 60, 90).map { ten + it.minutes } + Instant.parse("2026-10-05T13:00:00Z"),
        )
    }

    @AfterTest fun tearDown() = TestMain.uninstall()

    private fun viewModel() = BookingFlowViewModel(wallbox, wallboxes, bookings, payments, clock = FixedClock(), zone = TimeZone.UTC)

    @Test
    fun startsWithTodayAndSevenDaysAndLoadsSlots() {
        val state = viewModel().state.value
        assertEquals(7, state.days.size)
        assertEquals(today, state.selectedDay)
        assertEquals(5, state.freeStarts.size)
        assertFalse(state.isLoadingSlots)
    }

    @Test
    fun lockedStartsAreExposedSeparatelyAndNotSelectable() {
        val locked = Instant.parse("2026-10-05T09:00:00Z")
        wallboxes.lockedByDay[today] = listOf(locked, locked + 30.minutes)
        val viewModel = viewModel()
        assertEquals(listOf(locked, locked + 30.minutes), viewModel.state.value.lockedStarts)
        assertFalse(locked in viewModel.state.value.freeStarts)
        viewModel.onStartSelected(locked)
        assertNull(viewModel.state.value.selectedStart)
    }

    @Test
    fun selectingStartOffersOnlyContiguousDurationsAndEstimatesCost() {
        val viewModel = viewModel()
        viewModel.onStartSelected(ten)
        val state = viewModel.state.value
        assertEquals(listOf(30, 60, 90, 120), state.durationOptions)
        assertEquals(30, state.selectedDurationMinutes)
        val estimate = assertNotNull(state.estimate)
        assertEquals(5.5, estimate.kwh)
        assertEquals(1.87, estimate.totalEur)
    }

    @Test
    fun isolatedSlotOnlyAllowsThirtyMinutes() {
        val viewModel = viewModel()
        viewModel.onStartSelected(Instant.parse("2026-10-05T13:00:00Z"))
        assertEquals(listOf(30), viewModel.state.value.durationOptions)
    }

    @Test
    fun changingDurationUpdatesEstimate() {
        val viewModel = viewModel()
        viewModel.onStartSelected(ten)
        viewModel.onDurationSelected(120)
        assertEquals(7.48, viewModel.state.value.estimate?.totalEur)
    }

    @Test
    fun submitCreatesBookingWithSelectedTimes() {
        val viewModel = viewModel()
        viewModel.onStartSelected(ten)
        viewModel.onDurationSelected(60)
        viewModel.onSubmit()
        assertEquals(Triple("wb-1", ten, ten + 60.minutes), bookings.created.single())
        assertIs<BookingSubmitState.Success>(viewModel.state.value.submit)
    }

    @Test
    fun submitWithoutSelectionDoesNothing() {
        val viewModel = viewModel()
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onSubmit()
        assertTrue(bookings.created.isEmpty())
    }

    @Test
    fun conflictShowsErrorAndReloadsSlotsClearingSelection() {
        bookings.createResult = failure(AppError.Conflict)
        val viewModel = viewModel()
        viewModel.onStartSelected(ten)
        viewModel.onSubmit()
        val state = viewModel.state.value
        assertEquals(BookingSubmitState.Failed(AppError.Conflict), state.submit)
        assertNull(state.selectedStart)
        assertNull(state.estimate)
    }

    @Test
    fun networkFailureKeepsSelectionSoUserCanRetry() {
        bookings.createResult = failure(AppError.Network)
        val viewModel = viewModel()
        viewModel.onStartSelected(ten)
        viewModel.onSubmit()
        val state = viewModel.state.value
        assertEquals(BookingSubmitState.Failed(AppError.Network), state.submit)
        assertEquals(ten, state.selectedStart)
        viewModel.onFailureDismissed()
        assertEquals(BookingSubmitState.Idle, viewModel.state.value.submit)
    }

    @Test
    fun selectingAnotherDayResetsSelectionAndReloads() {
        val viewModel = viewModel()
        viewModel.onStartSelected(ten)
        viewModel.onDaySelected(LocalDate(2026, 10, 6))
        val state = viewModel.state.value
        assertNull(state.selectedStart)
        assertEquals(emptyList(), state.freeStarts)
        assertEquals(LocalDate(2026, 10, 6), state.selectedDay)
    }

    @Test
    fun slotLoadingErrorIsReported() {
        wallboxes.slotsByDay[today] = failure(AppError.Network)
        assertEquals(AppError.Network, viewModel().state.value.slotsError)
    }

    @Test
    fun plannerRespectsWallboxMinAndMaxDuration() {
        val free = (0 until 12).map { ten + (it * SLOT_MINUTES).minutes }.toSet()
        val limited = testWallbox(minDurationMin = 60, maxDurationMin = 150)
        assertEquals(listOf(60, 90, 120, 150), SlotPlanner.durationOptions(ten, free, limited))
        assertEquals(0, SlotPlanner.contiguousFreeSlots(Instant.parse("2026-10-05T20:00:00Z"), free))
    }

    @Test
    fun confirmedBookingResponseIsExposed() {
        bookings.createResult = Outcome.Success(BookingCreation(testBooking(id = "neu"), clientSecret = "pi_fake_secret"))
        val viewModel = viewModel()
        viewModel.onStartSelected(ten)
        viewModel.onSubmit()
        val submit = assertIs<BookingSubmitState.Success>(viewModel.state.value.submit)
        assertEquals("neu", submit.booking.id)
    }
}
