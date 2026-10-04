package app.chargeshare.server

import app.chargeshare.server.services.abbreviateName
import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.CreateReviewRequest
import app.chargeshare.shared.model.SuperhostCriteria
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Host-Karte der Detailseite: `GET /wallboxes/{id}/host`. */
class HostProfileApiTest {

    @Test
    fun abbreviatesNamesToFirstNameAndInitial() {
        assertEquals("Andreas M.", abbreviateName("Andreas Mustermann"))
        assertEquals("Anna M.", abbreviateName("  Anna   Maria  müller "))
        assertEquals("Madonna", abbreviateName("Madonna"))
        assertEquals("Demo H.", abbreviateName("Demo Host (Beispieldaten)"))
        assertEquals("", abbreviateName("   "))
    }

    @Test
    fun newHostHasNoSessionsAndIsNoSuperhost() = serverTest {
        val host = registerUser("andreas@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox())

        val profile = newApi().api.wallboxHost(wallbox.id)
        assertEquals("andreas@example.com".substringBefore('@'), profile.name, "Ein-Wort-Name bleibt unverändert")
        assertEquals(0, profile.sessions)
        assertNull(profile.ratingAvg)
        assertEquals(0, profile.ratingCount)
        assertFalse(profile.isSuperhost)
    }

    @Test
    fun profileIsPublicAndHidesContactData() = serverTest {
        val host = registerUser("host@example.com", host = true)
        host.api.updateMe(app.chargeshare.shared.model.UpdateUserRequest(name = "Andreas Mustermann", phone = "+49 421 123456"))
        val wallbox = host.api.createWallbox(newWallbox())

        val body = raw.getAuth("/api/v1/wallboxes/${wallbox.id}/host", token = null).text()
        assertTrue("\"name\":\"Andreas M.\"" in body, body)
        assertFalse("Mustermann" in body || "123456" in body || "host@example.com" in body, body)
    }

    @Test
    fun countsOnlyCompletedBookingsOfAllHostWallboxes() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val first = host.api.createWallbox(newWallbox(name = "Eins")).also { host.api.setWallboxOnline(it.id, true) }
        val second = host.api.createWallbox(newWallbox(name = "Zwei")).also { host.api.setWallboxOnline(it.id, true) }
        val otherHost = registerUser("anderer@example.com", host = true)
        val foreign = otherHost.api.createWallbox(newWallbox(name = "Fremd")).also { otherHost.api.setWallboxOnline(it.id, true) }
        val driver = registerUser("fahrer@example.com")
        val a = driver.api.createBooking(CreateBookingRequest(first.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        val b = driver.api.createBooking(CreateBookingRequest(second.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        driver.api.createBooking(CreateBookingRequest(second.id, inFuture(2, 10), inFuture(2, 12)))
        val c = driver.api.createBooking(CreateBookingRequest(foreign.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        listOf(a, b, c).forEach { forceBookingStatus(it.id, "completed") }

        assertEquals(2, newApi().api.wallboxHost(first.id).sessions, "beide Wallboxen des Hosts zählen, offene und fremde nicht")
        assertEquals(1, newApi().api.wallboxHost(foreign.id).sessions)
    }

    @Test
    fun superhostNeedsSessionsAndGoodRatings() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox()).also { host.api.setWallboxOnline(it.id, true) }
        val driver = registerUser("fahrer@example.com")
        val bookings = (1..SuperhostCriteria.MIN_SESSIONS).map { day ->
            driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(day.toLong(), 10), inFuture(day.toLong(), 12))).booking!!
                .also { forceBookingStatus(it.id, "completed") }
        }
        assertFalse(newApi().api.wallboxHost(wallbox.id).isSuperhost, "ohne Bewertungen kein Superhost")

        bookings.take(SuperhostCriteria.MIN_RATING_COUNT).forEach { driver.api.createReview(CreateReviewRequest(it.id, 5)) }
        val profile = newApi().api.wallboxHost(wallbox.id)
        assertEquals(SuperhostCriteria.MIN_SESSIONS, profile.sessions)
        assertEquals(5.0, profile.ratingAvg)
        assertEquals(SuperhostCriteria.MIN_RATING_COUNT, profile.ratingCount)
        assertTrue(profile.isSuperhost)
    }

    @Test
    fun lowRatingsPreventSuperhost() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox()).also { host.api.setWallboxOnline(it.id, true) }
        val driver = registerUser("fahrer@example.com")
        (1..SuperhostCriteria.MIN_SESSIONS).forEach { day ->
            val booking = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(day.toLong(), 10), inFuture(day.toLong(), 12))).booking!!
            forceBookingStatus(booking.id, "completed")
            if (day <= SuperhostCriteria.MIN_RATING_COUNT) driver.api.createReview(CreateReviewRequest(booking.id, 4))
        }
        val profile = newApi().api.wallboxHost(wallbox.id)
        assertEquals(4.0, profile.ratingAvg)
        assertFalse(profile.isSuperhost, "Durchschnitt 4,0 liegt unter ${SuperhostCriteria.MIN_RATING_AVG}")
    }

    @Test
    fun unknownWallboxIsNotFound() = serverTest {
        val error = assertFailsWith<ApiException> { newApi().api.wallboxHost("00000000-0000-0000-0000-000000000000") }
        assertEquals(404, error.status)
    }
}
