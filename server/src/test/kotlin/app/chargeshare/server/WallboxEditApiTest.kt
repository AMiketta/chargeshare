package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.TimeSlot
import app.chargeshare.shared.model.UpdateWallboxRequest
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.shared.model.WallboxLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Wallbox bearbeiten/deaktivieren: Validierung und Berechtigung (nur der Eigentümer). */
class WallboxEditApiTest {

    private suspend fun ServerScope.hostWithWallbox(): Triple<TestClient, TestClient, Wallbox> {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox("Marktplatz-Box", power = 11.0, price = 0.40))
        host.api.setWallboxOnline(wallbox.id, true)
        return Triple(host, registerUser("fremder-host@example.com", host = true), wallbox)
    }

    @Test
    fun ownerCanChangePriceDescriptionAndKeepsOtherFields() = serverTest {
        val (host, _, wallbox) = hostWithWallbox()
        val updated = host.api.updateWallbox(wallbox.id, UpdateWallboxRequest(pricePerKwh = 0.35, description = "  Einfahrt links, Tor offen  "))
        assertEquals(0.35, updated.pricePerKwh)
        assertEquals("Einfahrt links, Tor offen", updated.description)
        assertEquals("Marktplatz-Box", updated.name)
        assertEquals(11.0, updated.powerKw)
        assertTrue(updated.isOnline)
        assertEquals("Einfahrt links, Tor offen", host.api.wallbox(wallbox.id).description)
    }

    @Test
    fun emptyDescriptionRemovesTheTextAndMissingFieldKeepsIt() = serverTest {
        val (host, _, wallbox) = hostWithWallbox()
        host.api.updateWallbox(wallbox.id, UpdateWallboxRequest(description = "Hinweis"))
        assertEquals("Hinweis", host.api.updateWallbox(wallbox.id, UpdateWallboxRequest(name = "Neu")).description)
        assertNull(host.api.updateWallbox(wallbox.id, UpdateWallboxRequest(description = "   ")).description)
    }

    @Test
    fun availabilityCanBeChangedByTheOwner() = serverTest {
        val (host, _, wallbox) = hostWithWallbox()
        val updated = host.api.updateWallbox(wallbox.id, UpdateWallboxRequest(availability = mapOf("monday" to listOf(TimeSlot("09:00", "10:00")))))
        assertEquals(mapOf("monday" to listOf(TimeSlot("09:00", "10:00"))), updated.availability)
        assertEquals(updated.availability, host.api.wallboxAvailability(wallbox.id))
    }

    @Test
    fun invalidValuesAreRejected() = serverTest {
        val (host, _, wallbox) = hostWithWallbox()
        val invalid = listOf(
            UpdateWallboxRequest(pricePerKwh = 0.0),
            UpdateWallboxRequest(pricePerKwh = WallboxLimits.MAX_PRICE_PER_KWH + 0.01),
            UpdateWallboxRequest(powerKw = 0.0),
            UpdateWallboxRequest(powerKw = WallboxLimits.MAX_POWER_KW + 1),
            UpdateWallboxRequest(name = "  "),
            UpdateWallboxRequest(name = "x".repeat(WallboxLimits.MAX_NAME_LENGTH + 1)),
            UpdateWallboxRequest(address = " "),
            UpdateWallboxRequest(city = ""),
            UpdateWallboxRequest(postalCode = " "),
            UpdateWallboxRequest(description = "x".repeat(WallboxLimits.MAX_DESCRIPTION_LENGTH + 1)),
            UpdateWallboxRequest(availability = mapOf("monday" to listOf(TimeSlot("12:00", "08:00")))),
            UpdateWallboxRequest(minDurationMin = 90, maxDurationMin = 60),
        )
        invalid.forEach { request ->
            assertEquals(422, assertFailsWith<ApiException> { host.api.updateWallbox(wallbox.id, request) }.status, request.toString())
        }
        val unchanged = host.api.wallbox(wallbox.id)
        assertEquals(0.40, unchanged.pricePerKwh)
        assertEquals("Marktplatz-Box", unchanged.name)
    }

    @Test
    fun createAlsoEnforcesLimitsAndStoresDescription() = serverTest {
        val host = registerUser("host@example.com", host = true)
        assertEquals(422, assertFailsWith<ApiException> { host.api.createWallbox(newWallbox(power = WallboxLimits.MAX_POWER_KW + 1)) }.status)
        assertEquals(422, assertFailsWith<ApiException> { host.api.createWallbox(newWallbox(price = 34.0)) }.status)
        val created = host.api.createWallbox(newWallbox().copy(description = " Mit Kabel "))
        assertEquals("Mit Kabel", created.description)
    }

    @Test
    fun onlyTheOwnerMayEditOrDeactivate() = serverTest {
        val (host, stranger, wallbox) = hostWithWallbox()
        assertEquals(403, assertFailsWith<ApiException> { stranger.api.updateWallbox(wallbox.id, UpdateWallboxRequest(pricePerKwh = 0.01)) }.status)
        assertEquals(403, assertFailsWith<ApiException> { stranger.api.setWallboxOnline(wallbox.id, false) }.status)
        assertEquals(403, assertFailsWith<ApiException> { stranger.api.updateWallboxAvailability(wallbox.id, emptyMap()) }.status)
        assertEquals(403, assertFailsWith<ApiException> { stranger.api.updateWallbox(wallbox.id, UpdateWallboxRequest(description = "gehackt")) }.status)
        assertEquals(401, assertFailsWith<ApiException> { newApi().api.updateWallbox(wallbox.id, UpdateWallboxRequest(name = "x")) }.status)
        val untouched = host.api.wallbox(wallbox.id)
        assertEquals(0.40, untouched.pricePerKwh)
        assertNull(untouched.description)
        assertTrue(untouched.isOnline)
    }

    @Test
    fun priceChangeDoesNotAffectExistingBookings() = serverTest {
        val (host, _, wallbox) = hostWithWallbox()
        val driver = registerUser("fahrer@example.com")
        val booking = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        host.api.updateWallbox(wallbox.id, UpdateWallboxRequest(pricePerKwh = 0.10))
        assertEquals(booking.pricePerKwh, driver.api.booking(booking.id).pricePerKwh)
        assertEquals(booking.totalAmount, driver.api.booking(booking.id).totalAmount)
    }

    @Test
    fun deactivatedWallboxCannotBeBooked() = serverTest {
        val (host, _, wallbox) = hostWithWallbox()
        assertFalse(host.api.setWallboxOnline(wallbox.id, false).isOnline)
        val driver = registerUser("fahrer@example.com")
        val error = assertFailsWith<ApiException> { driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))) }
        assertEquals(400, error.status)
    }
}
