package app.chargeshare.server.geocoding

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Semaphore
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Adresssuche über [Nominatim](https://nominatim.org/release-docs/latest/api/Search/) (OpenStreetMap).
 *
 * Hält die [Nutzungsrichtlinie](https://operations.osmfoundation.org/policies/nominatim/) des öffentlichen Servers ein:
 * - **höchstens eine Anfrage pro Sekunde** für die ganze Instanz (`minInterval`, Standard 1,1 s; Wartende stehen in einer Schlange)
 * - **eigener `User-Agent`** mit Kontakt (`GEOCODER_USER_AGENT`), nie der Standard einer HTTP-Bibliothek
 * - **Zwischenspeicher** für Treffer und „nicht gefunden“ (24 Stunden), damit dieselbe Adresse nicht erneut angefragt wird
 * - keine Suche-beim-Tippen: aufgerufen wird nur auf ausdrücklichen Knopfdruck des Hosts
 *
 * Ist die Schlange zu lang oder die Wartezeit zu groß, antwortet der Aufrufer mit [GeocoderUnavailableException]; der Host
 * kann die Koordinaten dann von Hand eintragen.
 *
 * @param baseUrl Server-Adresse ohne Pfad, z. B. `https://nominatim.openstreetmap.org`
 * @param transport führt die HTTP-Anfrage aus (für Tests austauschbar); Rückgabe ist der Antwort-Body bei Status 200
 */
class NominatimGeocoder(
    private val baseUrl: String,
    private val userAgent: String,
    private val countryCodes: String = "de",
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val transport: suspend (URI, String) -> String = ::javaTransport,
) : Geocoder {
    private val gate = Mutex()
    private val queue = Semaphore(MAX_QUEUE)
    private var lastRequestAt = 0L
    private val cache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > MAX_CACHE_ENTRIES
    }

    private class CacheEntry(val hit: GeocodeHit?, val expiresAt: Long)

    @Serializable
    private data class NominatimPlace(val lat: String, val lon: String, @SerialName("display_name") val displayName: String? = null)

    override suspend fun geocode(address: String, postalCode: String, city: String): GeocodeHit? {
        val query = "${address.trim()}, ${postalCode.trim()} ${city.trim()}"
        val key = query.lowercase().replace(Regex("\\s+"), " ")
        cached(key)?.let { return it.hit }
        if (!queue.tryAcquire()) throw GeocoderUnavailableException("geocoder is busy")
        try {
            val hit = gate.withLock {
                // Während des Wartens kann eine andere Anfrage dieselbe Adresse schon geholt haben.
                cached(key)?.let { return@withLock it.hit }
                waitForTurn()
                val result = fetch(query)
                synchronized(cache) { cache[key] = CacheEntry(result, nowMillis() + CACHE_TTL.inWholeMilliseconds) }
                result
            }
            return hit
        } finally {
            queue.release()
        }
    }

    private fun cached(key: String): CacheEntry? = synchronized(cache) {
        cache[key]?.takeIf { it.expiresAt > nowMillis() }
    }

    private suspend fun waitForTurn() {
        val wait = lastRequestAt + minIntervalMillis - nowMillis()
        if (wait > MAX_WAIT.inWholeMilliseconds) throw GeocoderUnavailableException("geocoder is busy")
        if (wait > 0) delay(wait.milliseconds)
        lastRequestAt = nowMillis()
    }

    private suspend fun fetch(query: String): GeocodeHit? {
        val url = "${baseUrl.trimEnd('/')}/search?format=jsonv2&limit=1&addressdetails=0" +
            "&countrycodes=${encode(countryCodes)}&q=${encode(query)}"
        val body = try {
            transport(URI.create(url), userAgent)
        } catch (e: GeocoderUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw GeocoderUnavailableException("geocoder request failed", e)
        }
        val places = try {
            JSON.decodeFromString<List<NominatimPlace>>(body)
        } catch (e: Exception) {
            throw GeocoderUnavailableException("geocoder returned an unreadable answer", e)
        }
        val place = places.firstOrNull() ?: return null
        val lat = place.lat.toDoubleOrNull()
        val lng = place.lon.toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            throw GeocoderUnavailableException("geocoder returned invalid coordinates")
        }
        return GeocodeHit(lat, lng, place.displayName)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    companion object {
        /** Nominatim erlaubt 1 Anfrage/s; 100 ms Reserve gegen Zeitschwankungen. */
        const val DEFAULT_MIN_INTERVAL_MILLIS = 1_100L
        private const val MAX_QUEUE = 5
        private const val MAX_CACHE_ENTRIES = 1_000
        private val CACHE_TTL = 24.hours
        private val MAX_WAIT = 5.seconds
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(8)
        private val JSON = Json { ignoreUnknownKeys = true }
        private val CLIENT: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build() }

        /** Standard-Transport über den HTTP-Client des JDK. */
        suspend fun javaTransport(uri: URI, userAgent: String): String = withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build()
            val response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) throw GeocoderUnavailableException("geocoder answered HTTP ${response.statusCode()}")
            response.body()
        }
    }
}
