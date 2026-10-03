package app.chargeshare.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import ovh.plrapps.mapcompose.core.TileStreamProvider

/**
 * Lädt Kartenkacheln von OpenStreetMap (`https://tile.openstreetmap.org/{z}/{x}/{y}.png`, kein API-Key nötig).
 *
 * - Netzwerkfehler (kein Internet, Timeout, HTTP-Fehler) liefern `null`: die Karte zeigt dann leere Flächen, die Pins bleiben bedienbar.
 *   Die Karten-Komponente selbst behandelt Exceptions aus dem Provider als nicht behebbar, deshalb werden sie hier abgefangen.
 * - Bereits geladene Kacheln bleiben begrenzt im Speicher ([CACHE_SIZE] Stück), damit Verschieben/Zoomen nicht jedes Mal neu lädt.
 * - Die OSM-Nutzungsbedingungen (Kennung per User-Agent, Quellenangabe, keine Massenabfragen) gelten; für Produktion
 *   muss ein eigener Kachelserver oder ein kommerzieller Anbieter her (siehe README „Karte“).
 */
class OsmTileSource(
    private val client: HttpClient = createTileHttpClient(),
    private val baseUrl: String = OSM_BASE_URL,
) : TileStreamProvider {

    private val cacheLock = Mutex()
    private val cache = LinkedHashMap<String, ByteArray>()

    override suspend fun getTileStream(row: Int, col: Int, zoomLvl: Int): RawSource? {
        val url = tileUrl(zoomLvl, col, row)
        val bytes = cached(url) ?: download(url)?.also { store(url, it) }
        return bytes?.let { Buffer().apply { write(it) } }
    }

    /** URL einer Kachel im OSM-Schema `{z}/{x}/{y}.png`. */
    fun tileUrl(zoom: Int, x: Int, y: Int): String = "$baseUrl/$zoom/$x/$y.png"

    private suspend fun download(url: String): ByteArray? = try {
        val response = client.get(url)
        if (response.status.isSuccess()) response.bodyAsBytes() else null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        null
    }

    private suspend fun cached(url: String): ByteArray? = cacheLock.withLock {
        // Zugriff verschiebt die Kachel ans Ende (LRU).
        cache.remove(url)?.also { cache[url] = it }
    }

    private suspend fun store(url: String, bytes: ByteArray) = cacheLock.withLock {
        cache[url] = bytes
        while (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
    }

    companion object {
        const val OSM_BASE_URL = "https://tile.openstreetmap.org"

        /** Kennung laut OSM-Tile-Usage-Policy (Browser ignorieren den User-Agent). */
        const val USER_AGENT = "ChargeShare/0.1 (+https://github.com/AMiketta/chargeshare)"

        /** Obergrenze der im Speicher gehaltenen Kacheln (je ca. 10–30 KB). */
        const val CACHE_SIZE = 256
    }
}

/** Http-Client für Kacheln: eigene Kennung, keine Weiterleitungs-/Auth-Logik des API-Clients. */
fun createTileHttpClient(): HttpClient = HttpClient {
    install(UserAgent) { agent = OsmTileSource.USER_AGENT }
}
