package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.CreateReviewRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Profil-Kennzahlen (`GET /users/me/stats`). */
class UserStatsApiTest {

    @Test
    fun newUserHasEmptyStats() = serverTest {
        val stats = registerUser("neu@example.com").api.myStats()
        assertEquals(0, stats.sessions)
        assertEquals(0.0, stats.kwhCharged)
        assertNull(stats.ratingAvg)
        assertEquals(0, stats.ratingCount)
    }

    @Test
    fun driverCountsOnlyCompletedBookingsAndSumsKwh() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox())
        host.api.setWallboxOnline(wallbox.id, true)
        val driver = registerUser("fahrer@example.com")
        val first = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        val second = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(2, 10), inFuture(2, 12))).booking!!
        driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(3, 10), inFuture(3, 12)))
        forceBookingStatus(first.id, "completed")
        forceBookingActualKwh(first.id, 10.25)
        forceBookingStatus(second.id, "completed")
        forceBookingActualKwh(second.id, 5.5)

        val stats = driver.api.myStats()
        assertEquals(2, stats.sessions, "die dritte Buchung ist noch nicht abgeschlossen")
        assertEquals(15.8, stats.kwhCharged, "auf eine Nachkommastelle gerundet")
        assertNull(stats.ratingAvg, "als reiner Fahrer gibt es keine Bewertungen")
        assertEquals(0, host.api.myStats().sessions, "Buchungen anderer Fahrer zaehlen nicht")
    }

    @Test
    fun hostGetsAverageRatingOfOwnWallboxes() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox())
        host.api.setWallboxOnline(wallbox.id, true)
        val driver = registerUser("fahrer@example.com")
        val booking = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        val other = registerUser("zweiter@example.com")
        val otherBooking = other.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(2, 10), inFuture(2, 12))).booking!!
        forceBookingStatus(booking.id, "completed")
        forceBookingStatus(otherBooking.id, "completed")
        driver.api.createReview(CreateReviewRequest(booking.id, 5))
        other.api.createReview(CreateReviewRequest(otherBooking.id, 4))

        val stats = host.api.myStats()
        assertEquals(4.5, stats.ratingAvg)
        assertEquals(2, stats.ratingCount)
        assertNull(driver.api.myStats().ratingAvg, "Bewertungen gehoeren dem Host, nicht dem Fahrer")
    }

    @Test
    fun statsRequireLogin() = serverTest {
        assertEquals(401, assertFailsWith<ApiException> { newApi().api.myStats() }.status)
    }
}
