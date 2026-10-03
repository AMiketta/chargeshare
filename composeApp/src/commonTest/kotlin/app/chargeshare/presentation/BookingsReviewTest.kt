package app.chargeshare.presentation

import app.chargeshare.data.AppError
import app.chargeshare.data.Outcome
import app.chargeshare.presentation.booking.BookingsViewModel
import app.chargeshare.presentation.booking.isReviewable
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.ReviewLimits
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bewertungsdialog der Buchungsliste. */
class BookingsReviewTest {
    private val bookings = FakeBookingRepository()
    private val completed = testBooking("done", BookingStatus.COMPLETED)

    @BeforeTest
    fun setUp() {
        TestMain.install()
        bookings.listResult = Outcome.Success(listOf(completed))
    }

    @AfterTest fun tearDown() = TestMain.uninstall()

    private fun viewModel() = BookingsViewModel(bookings, FakePaymentRepository(), clock = FixedClock()).also { it.refresh() }

    @Test
    fun onlyCompletedUnreviewedBookingsAreReviewable() {
        assertTrue(completed.isReviewable())
        assertFalse(completed.copy(reviewed = true).isReviewable())
        assertFalse(testBooking(status = BookingStatus.CONFIRMED).isReviewable())
        assertFalse(testBooking(status = BookingStatus.CANCELLED).isReviewable())
    }

    @Test
    fun requestingAReviewOfAnOpenBookingDoesNothing() {
        val viewModel = viewModel()
        viewModel.onReviewRequested(testBooking(status = BookingStatus.CONFIRMED))
        assertNull(viewModel.state.value.reviewDraft)
    }

    @Test
    fun submitNeedsAtLeastOneStar() {
        val viewModel = viewModel()
        viewModel.onReviewRequested(completed)
        assertFalse(assertNotNull(viewModel.state.value.reviewDraft).canSubmit)
        viewModel.onReviewSubmitted()
        assertTrue(bookings.reviews.isEmpty())
        viewModel.onReviewRatingChanged(4)
        assertTrue(assertNotNull(viewModel.state.value.reviewDraft).canSubmit)
    }

    @Test
    fun tooLongCommentBlocksSubmit() {
        val viewModel = viewModel()
        viewModel.onReviewRequested(completed)
        viewModel.onReviewRatingChanged(5)
        viewModel.onReviewCommentChanged("x".repeat(ReviewLimits.MAX_COMMENT_LENGTH + 1))
        assertTrue(assertNotNull(viewModel.state.value.reviewDraft).isCommentTooLong)
        viewModel.onReviewSubmitted()
        assertTrue(bookings.reviews.isEmpty())
    }

    @Test
    fun successfulReviewSendsTrimmedCommentAndMarksBookingReviewed() {
        val viewModel = viewModel()
        viewModel.onReviewRequested(completed)
        viewModel.onReviewRatingChanged(5)
        viewModel.onReviewCommentChanged("  Super Box  ")
        viewModel.onReviewSubmitted()
        assertEquals(listOf(Triple<String, Int, String?>("done", 5, "Super Box")), bookings.reviews)
        assertNull(viewModel.state.value.reviewDraft)
        assertTrue(viewModel.state.value.bookings.single().reviewed)
    }

    @Test
    fun blankCommentIsSentAsNoComment() {
        val viewModel = viewModel()
        viewModel.onReviewRequested(completed)
        viewModel.onReviewRatingChanged(3)
        viewModel.onReviewCommentChanged("   ")
        viewModel.onReviewSubmitted()
        assertEquals(listOf(Triple<String, Int, String?>("done", 3, null)), bookings.reviews)
    }

    @Test
    fun serverErrorKeepsTheDraftAndShowsTheError() {
        bookings.reviewResult = failure(AppError.Server)
        val viewModel = viewModel()
        viewModel.onReviewRequested(completed)
        viewModel.onReviewRatingChanged(2)
        viewModel.onReviewCommentChanged("Hm")
        viewModel.onReviewSubmitted()
        val draft = assertNotNull(viewModel.state.value.reviewDraft)
        assertEquals(AppError.Server, draft.error)
        assertEquals("Hm", draft.comment)
        assertFalse(draft.isSubmitting)
        assertFalse(viewModel.state.value.bookings.single().reviewed)
    }

    @Test
    fun alreadyReviewedConflictClosesTheDialogAsReviewed() {
        bookings.reviewResult = failure(AppError.Conflict)
        val viewModel = viewModel()
        viewModel.onReviewRequested(completed)
        viewModel.onReviewRatingChanged(5)
        viewModel.onReviewSubmitted()
        assertNull(viewModel.state.value.reviewDraft)
        assertTrue(viewModel.state.value.bookings.single().reviewed)
    }

    @Test
    fun dismissingDiscardsTheDraft() {
        val viewModel = viewModel()
        viewModel.onReviewRequested(completed)
        viewModel.onReviewRatingChanged(1)
        viewModel.onReviewDismissed()
        assertNull(viewModel.state.value.reviewDraft)
        assertTrue(bookings.reviews.isEmpty())
    }
}
