package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.CreateReviewRequest
import app.chargeshare.shared.model.ReviewLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bewertungen: nur der Fahrer einer abgeschlossenen Buchung, einmal pro Buchung, Durchschnitt an der Wallbox. */
class ReviewApiTest {

    private suspend fun ServerScope.bookedWallbox(): Triple<TestClient, TestClient, app.chargeshare.shared.model.Booking> {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox())
        host.api.setWallboxOnline(wallbox.id, true)
        val driver = registerUser("fahrer@example.com")
        val booking = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        return Triple(host, driver, booking)
    }

    @Test
    fun reviewedFlagIsFalseUntilTheDriverReviews() = serverTest {
        val (_, driver, booking) = bookedWallbox()
        forceBookingStatus(booking.id, "completed")
        assertFalse(driver.api.booking(booking.id).reviewed)
        assertFalse(driver.api.bookings().bookings.single().reviewed)

        driver.api.createReview(CreateReviewRequest(booking.id, 5, "Top"))
        assertTrue(driver.api.booking(booking.id).reviewed)
        assertTrue(driver.api.bookings().bookings.single().reviewed)
    }

    @Test
    fun onlyTheDriverOfACompletedBookingMayReviewOnce() = serverTest {
        val (host, driver, booking) = bookedWallbox()
        // Noch nicht abgeschlossen
        assertEquals(400, assertFailsWith<ApiException> { driver.api.createReview(CreateReviewRequest(booking.id, 5)) }.status)
        forceBookingStatus(booking.id, "completed")
        // Fremde (auch der Host) dürfen nicht bewerten
        assertEquals(404, assertFailsWith<ApiException> { host.api.createReview(CreateReviewRequest(booking.id, 5)) }.status)
        driver.api.createReview(CreateReviewRequest(booking.id, 4))
        assertEquals(409, assertFailsWith<ApiException> { driver.api.createReview(CreateReviewRequest(booking.id, 5)) }.status)
    }

    @Test
    fun ratingAndCommentAreValidated() = serverTest {
        val (_, driver, booking) = bookedWallbox()
        forceBookingStatus(booking.id, "completed")
        assertEquals(422, assertFailsWith<ApiException> { driver.api.createReview(CreateReviewRequest(booking.id, 0)) }.status)
        assertEquals(422, assertFailsWith<ApiException> { driver.api.createReview(CreateReviewRequest(booking.id, ReviewLimits.MAX_RATING + 1)) }.status)
        val tooLong = "x".repeat(ReviewLimits.MAX_COMMENT_LENGTH + 1)
        assertEquals(422, assertFailsWith<ApiException> { driver.api.createReview(CreateReviewRequest(booking.id, 3, tooLong)) }.status)
        assertFalse(driver.api.booking(booking.id).reviewed, "abgelehnte Bewertungen werden nicht gespeichert")
    }

    @Test
    fun blankCommentIsStoredAsNoComment() = serverTest {
        val (_, driver, booking) = bookedWallbox()
        forceBookingStatus(booking.id, "completed")
        assertNull(driver.api.createReview(CreateReviewRequest(booking.id, 3, "   ")).comment)
    }

    @Test
    fun wallboxAverageIsRecomputedFromAllReviews() = serverTest {
        val (host, driver, booking) = bookedWallbox()
        forceBookingStatus(booking.id, "completed")
        driver.api.createReview(CreateReviewRequest(booking.id, 5))

        val second = registerUser("zweiter@example.com")
        val other = second.api.createBooking(CreateBookingRequest(booking.wallboxId, inFuture(2, 10), inFuture(2, 12))).booking!!
        forceBookingStatus(other.id, "completed")
        second.api.createReview(CreateReviewRequest(other.id, 2))

        val wallbox = host.api.wallbox(booking.wallboxId)
        assertEquals(2, wallbox.ratingCount)
        assertEquals(3.5, wallbox.ratingAvg)
    }
}
