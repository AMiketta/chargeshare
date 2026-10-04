package app.chargeshare.data

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Lädt Wallbox-Fotos und hält die zuletzt benutzten als fertige Bitmaps im Speicher.
 *
 * Fotos sind öffentlich (kein Login nötig); [apiBaseUrl] ergänzt die relativen Pfade des Servers
 * (`/api/v1/wallboxes/{id}/photos/…`). Fehler (offline, 404) liefern `null`, die UI zeigt dann ihren Platzhalter.
 *
 * Der Speicher ist nach Pixeln begrenzt ([MAX_CACHED_PIXELS]), weil ein 1600-px-Foto dekodiert rund 10 MB belegt,
 * die kleinen Fassungen für Listen und Karte (480 px) aber nur etwa 1 MB.
 *
 * @param fetch holt die Rohbytes einer Adresse (`null` bei Fehler); für Tests austauschbar
 * @param decode macht aus den Bytes eine Bitmap (`null` bei unlesbaren Daten); für Tests austauschbar
 */
class ImageLoader(
    private val apiBaseUrl: String,
    private val fetch: suspend (String) -> ByteArray? = HttpFetcher()::get,
    private val decode: (ByteArray) -> ImageBitmap? = ::decodeOrNull,
) {
    private val lock = Mutex()
    private val cache = LinkedHashMap<String, ImageBitmap>()
    private var cachedPixels = 0L

    /** Absolute Adresse zu einem Server-Pfad; bereits absolute `http(s)`-Adressen bleiben unverändert. */
    fun urlOf(path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path else apiBaseUrl.trimEnd('/') + "/" + path.trimStart('/')

    /** Bitmap aus dem Speicher, ohne Netzwerk. */
    suspend fun cached(path: String): ImageBitmap? = lock.withLock {
        val key = urlOf(path)
        // Zugriff verschiebt den Eintrag ans Ende (LRU).
        cache.remove(key)?.also { cache[key] = it }
    }

    suspend fun load(path: String): ImageBitmap? {
        cached(path)?.let { return it }
        val url = urlOf(path)
        val bytes = fetch(url) ?: return null
        val bitmap = decode(bytes) ?: return null
        store(url, bitmap)
        return bitmap
    }

    private suspend fun store(url: String, bitmap: ImageBitmap) = lock.withLock {
        cache.remove(url)?.let { cachedPixels -= it.pixels() }
        cache[url] = bitmap
        cachedPixels += bitmap.pixels()
        while (cachedPixels > MAX_CACHED_PIXELS && cache.size > 1) {
            val eldest = cache.keys.first()
            cache.remove(eldest)?.let { cachedPixels -= it.pixels() }
        }
    }

    private fun ImageBitmap.pixels(): Long = width.toLong() * height

    companion object {
        /** Etwa 64 MB dekodierte Bitmaps (4 Byte pro Pixel). */
        const val MAX_CACHED_PIXELS = 16_000_000L
    }
}

private fun decodeOrNull(bytes: ByteArray): ImageBitmap? = try {
    bytes.decodeToImageBitmap()
} catch (e: IllegalArgumentException) {
    null
} catch (e: IllegalStateException) {
    null
}

/** Ktor-Abruf für [ImageLoader]; ohne Login, damit nie Zugangsdaten an fremde Adressen gehen. */
internal class HttpFetcher(private val client: HttpClient = HttpClient()) {
    suspend fun get(url: String): ByteArray? = try {
        val response = client.get(url)
        if (response.status.isSuccess()) response.bodyAsBytes() else null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        null
    }
}
