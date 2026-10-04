package app.chargeshare.server

import app.chargeshare.server.geocoding.GeocodeHit
import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.GeocodeRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `POST /geocode`: Adresse → Koordinaten (mit Testdouble statt Nominatim). */
class GeocodeApiTest {
    private val request = GeocodeRequest("Marktplatz 1", "28195", "Bremen")

    @Test
    fun returnsCoordinatesForAKnownAddress() = serverTest {
        geocoder.hits["Marktplatz 1"] = GeocodeHit(53.0758, 8.8072, "Marktplatz 1, Bremen")
        val user = registerUser("host@example.com", host = true)
        val result = user.api.geocode(request)
        assertEquals(53.0758, result.lat)
        assertEquals(8.8072, result.lng)
        assertEquals("Marktplatz 1, Bremen", result.displayName)
        assertEquals(listOf(Triple("Marktplatz 1", "28195", "Bremen")), geocoder.calls)
    }

    @Test
    fun unknownAddressIsNotFound() = serverTest {
        val user = registerUser("host@example.com", host = true)
        assertEquals(404, assertFailsWith<ApiException> { user.api.geocode(request) }.status)
    }

    @Test
    fun unavailableServiceAnswers503() = serverTest {
        geocoder.unavailable = true
        val user = registerUser("host@example.com", host = true)
        assertEquals(503, assertFailsWith<ApiException> { user.api.geocode(request) }.status)
    }

    @Test
    fun blankFieldsAreRejectedWithoutCallingTheService() = serverTest {
        val user = registerUser("host@example.com", host = true)
        listOf(request.copy(address = " "), request.copy(postalCode = ""), request.copy(city = "  ")).forEach {
            assertEquals(422, assertFailsWith<ApiException> { user.api.geocode(it) }.status)
        }
        assertTrue(geocoder.calls.isEmpty())
    }

    @Test
    fun requiresLogin() = serverTest {
        assertEquals(401, assertFailsWith<ApiException> { newApi().api.geocode(request) }.status)
    }

    @Test
    fun eachUserMayLookUpTwentyAddressesPerMinute() = serverTest {
        geocoder.hits["Marktplatz 1"] = GeocodeHit(53.0, 8.0, null)
        val user = registerUser("host@example.com", host = true)
        repeat(20) { user.api.geocode(request) }
        assertEquals(429, assertFailsWith<ApiException> { user.api.geocode(request) }.status)
        val other = registerUser("anderer@example.com", host = true)
        assertEquals(53.0, other.api.geocode(request).lat)
    }
}
