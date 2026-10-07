package app.chargeshare.server

import app.chargeshare.server.geocoding.GeocoderUnavailableException
import app.chargeshare.server.geocoding.NominatimGeocoder
import java.net.URI
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Der Nominatim-Client: Anfrage-Format, User-Agent, Zwischenspeicher und Anfragefrequenz. */
class NominatimGeocoderTest {
    private class Recorder(var answer: String = HIT) {
        val calls = Collections.synchronizedList(mutableListOf<Triple<URI, String, Long>>())

        suspend fun transport(uri: URI, userAgent: String): String {
            calls += Triple(uri, userAgent, System.nanoTime() / NANOS_PER_MILLI)
            return answer
        }
    }

    private fun geocoder(recorder: Recorder, interval: Long = 0, now: () -> Long = System::currentTimeMillis) =
        NominatimGeocoder("https://nominatim.example/", "ChargeShareTest/1.0 (test@example.com)", "de", interval, now, recorder::transport)

    @Test
    fun sendsAStructuredFreeTextQueryWithOwnUserAgent() = runBlocking {
        val recorder = Recorder()
        val hit = geocoder(recorder).geocode("Marktplatz 1", "28195", "Bremen")
        assertEquals(53.0758, hit?.lat)
        assertEquals(8.8072, hit?.lng)
        assertEquals("Marktplatz 1, Bremen", hit?.displayName)
        val (uri, agent, _) = recorder.calls.single()
        assertEquals("ChargeShareTest/1.0 (test@example.com)", agent)
        assertEquals("nominatim.example", uri.host)
        assertEquals("/search", uri.path)
        assertTrue(uri.rawQuery.contains("format=jsonv2") && uri.rawQuery.contains("limit=1") && uri.rawQuery.contains("countrycodes=de"), uri.rawQuery)
        assertTrue(uri.rawQuery.contains("q=Marktplatz+1%2C+28195+Bremen"), uri.rawQuery)
    }

    @Test
    fun placeSearchSendsTheFreeTextAndSharesTheCache() = runBlocking {
        val recorder = Recorder()
        val geocoder = geocoder(recorder)
        assertEquals(53.0758, geocoder.searchPlace("  Lemwerder ")?.lat)
        geocoder.searchPlace("lemwerder")
        val (uri, agent, _) = recorder.calls.single()
        assertEquals("ChargeShareTest/1.0 (test@example.com)", agent)
        assertTrue(uri.rawQuery.contains("q=Lemwerder") && uri.rawQuery.contains("countrycodes=de"), uri.rawQuery)
    }

    @Test
    fun repeatedAddressesComeFromTheCacheEvenWithDifferentCase() = runBlocking {
        val recorder = Recorder()
        val geocoder = geocoder(recorder)
        geocoder.geocode("Marktplatz 1", "28195", "Bremen")
        geocoder.geocode("  marktplatz  1 ", "28195", "BREMEN")
        assertEquals(1, recorder.calls.size)
    }

    @Test
    fun unknownAddressesAreCachedToo() = runBlocking {
        val recorder = Recorder("[]")
        val geocoder = geocoder(recorder)
        assertNull(geocoder.geocode("Nirgendwo 1", "00000", "Nichts"))
        assertNull(geocoder.geocode("Nirgendwo 1", "00000", "Nichts"))
        assertEquals(1, recorder.calls.size)
    }

    @Test
    fun cacheEntriesExpireAfterADay() = runBlocking {
        val recorder = Recorder()
        var now = 1_000_000L
        val geocoder = geocoder(recorder, now = { now })
        geocoder.geocode("Marktplatz 1", "28195", "Bremen")
        now += 23 * MILLIS_PER_HOUR
        geocoder.geocode("Marktplatz 1", "28195", "Bremen")
        assertEquals(1, recorder.calls.size)
        now += 2 * MILLIS_PER_HOUR
        geocoder.geocode("Marktplatz 1", "28195", "Bremen")
        assertEquals(2, recorder.calls.size)
    }

    @Test
    fun upstreamCallsAreSpacedByTheMinimumInterval() = runBlocking {
        val recorder = Recorder()
        val geocoder = geocoder(recorder, interval = 250)
        val results = listOf("A 1", "B 2", "C 3").map { street -> async { geocoder.geocode(street, "28195", "Bremen") } }
        results.awaitAll()
        val times = recorder.calls.map { it.third }.sorted()
        assertEquals(3, times.size)
        // Etwas Spielraum für die Zeitmessung, aber klar über „parallel“.
        assertTrue(times[1] - times[0] >= 230 && times[2] - times[1] >= 230, "Abstände: $times")
    }

    @Test
    fun tooManyWaitingRequestsAreRefusedInsteadOfPilingUp() = runBlocking {
        val recorder = Recorder()
        val geocoder = geocoder(recorder, interval = 100)
        val outcomes = (1..9).map { i -> async { runCatching { geocoder.geocode("Straße $i", "28195", "Bremen") } } }.awaitAll()
        val refused = outcomes.count { it.exceptionOrNull() is GeocoderUnavailableException }
        assertTrue(refused >= 1, "mindestens eine Anfrage muss abgelehnt werden")
        assertTrue(outcomes.count { it.isSuccess } >= 1)
    }

    @Test
    fun transportErrorsAndGarbageAnswersBecomeUnavailable() = runBlocking {
        val failing = NominatimGeocoder("https://x.example", "UA", "de", 0, System::currentTimeMillis) { _, _ -> error("boom") }
        assertFailsWith<GeocoderUnavailableException> { failing.geocode("A 1", "1", "B") }
        val garbage = geocoder(Recorder("<html>429</html>"))
        assertFailsWith<GeocoderUnavailableException> { garbage.geocode("A 1", "1", "B") }
        val badCoordinates = geocoder(Recorder("""[{"lat":"abc","lon":"8.0"}]"""))
        assertFailsWith<GeocoderUnavailableException> { badCoordinates.geocode("A 1", "1", "B") }
        val outOfRange = geocoder(Recorder("""[{"lat":"123.0","lon":"8.0"}]"""))
        assertFailsWith<GeocoderUnavailableException> { outOfRange.geocode("A 1", "1", "B") }
    }

    @Test
    fun failuresAreNotCached() = runBlocking {
        val recorder = Recorder("nicht json")
        val geocoder = geocoder(recorder)
        assertFailsWith<GeocoderUnavailableException> { geocoder.geocode("A 1", "1", "B") }
        recorder.answer = HIT
        delay(1)
        assertEquals(53.0758, geocoder.geocode("A 1", "1", "B")?.lat)
        assertEquals(2, recorder.calls.size)
    }

    private companion object {
        const val HIT = """[{"lat":"53.0758","lon":"8.8072","display_name":"Marktplatz 1, Bremen"}]"""
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_HOUR = 3_600_000L
    }
}
