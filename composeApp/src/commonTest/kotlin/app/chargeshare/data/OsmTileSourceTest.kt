package app.chargeshare.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.UserAgent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

class OsmTileSourceTest {
    private val png = byteArrayOf(1, 2, 3, 4)

    private fun source(engine: MockEngine) = OsmTileSource(
        client = HttpClient(engine) { install(UserAgent) { agent = OsmTileSource.USER_AGENT } },
        baseUrl = "https://tiles.test",
    )

    @Test
    fun tileUrlUsesZoomColumnRowOrder() = runTest {
        val requests = mutableListOf<String>()
        val source = source(MockEngine { request -> requests += request.url.toString(); respond(png) })
        source.getTileStream(row = 340, col = 270, zoomLvl = 10)
        assertEquals(listOf("https://tiles.test/10/270/340.png"), requests)
    }

    @Test
    fun successfulDownloadReturnsTheTileBytes() = runTest {
        val stream = source(MockEngine { respond(png) }).getTileStream(0, 0, 0)
        assertEquals(png.toList(), (stream as Buffer).readByteArray().toList())
    }

    @Test
    fun requestsIdentifyTheApp() = runTest {
        var userAgent: String? = null
        source(MockEngine { request -> userAgent = request.headers[HttpHeaders.UserAgent]; respond(png) }).getTileStream(0, 0, 0)
        assertEquals(OsmTileSource.USER_AGENT, userAgent)
    }

    @Test
    fun httpErrorYieldsNoTileInsteadOfException() = runTest {
        assertNull(source(MockEngine { respondError(HttpStatusCode.NotFound) }).getTileStream(0, 0, 0))
    }

    @Test
    fun networkFailureYieldsNoTileInsteadOfException() = runTest {
        assertNull(source(MockEngine { error("kein Internet") }).getTileStream(0, 0, 0))
    }

    @Test
    fun secondRequestForSameTileComesFromCache() = runTest {
        var downloads = 0
        val source = source(MockEngine { downloads++; respond(png) })
        assertNotNull(source.getTileStream(1, 2, 3))
        assertNotNull(source.getTileStream(1, 2, 3))
        assertEquals(1, downloads)
    }

    @Test
    fun cacheKeepsOnlyTheNewestTiles() = runTest {
        var downloads = 0
        val source = source(MockEngine { downloads++; respond(png) })
        repeat(OsmTileSource.CACHE_SIZE + 1) { source.getTileStream(row = it, col = 0, zoomLvl = 18) }
        source.getTileStream(row = 0, col = 0, zoomLvl = 18)
        assertEquals(OsmTileSource.CACHE_SIZE + 2, downloads, "die älteste Kachel wurde verdrängt und muss neu geladen werden")
    }
}
