package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.PhotoPaths
import app.chargeshare.shared.model.UpdateWallboxRequest
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.shared.model.WallboxLimits
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Foto-Upload, Auslieferung und Entfernen (`/wallboxes/{id}/photos`). */
class PhotoApiTest {

    private fun image(width: Int, height: Int, format: String, type: Int = BufferedImage.TYPE_INT_RGB): ByteArray {
        val img = BufferedImage(width, height, type)
        val g = img.createGraphics()
        g.color = Color(30, 140, 90)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        check(ImageIO.write(img, format, out))
        return out.toByteArray()
    }

    private fun jpeg(width: Int = 400, height: Int = 300) = image(width, height, "jpg")

    private suspend fun ServerScope.hostWithWallbox(): Pair<TestClient, Wallbox> {
        val host = registerUser("host@example.com", host = true)
        return host to host.api.createWallbox(newWallbox())
    }

    @Test
    fun ownerUploadsAndEveryoneCanLoadTheImage() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        val updated = host.api.uploadWallboxPhoto(wallbox.id, jpeg())
        val path = updated.photos.single()
        assertTrue(path.startsWith("/api/v1/wallboxes/${wallbox.id}/photos/") && path.endsWith(".jpg"), path)

        val response = raw.get(path) // ohne Login
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("image/jpeg", response.headers[HttpHeaders.ContentType])
        assertTrue(response.headers[HttpHeaders.CacheControl].orEmpty().contains("immutable"))
        assertNotNull(ImageIO.read(ByteArrayInputStream(response.bodyAsBytes())))
        assertEquals(listOf(path), newApi().api.wallbox(wallbox.id).photos)
    }

    @Test
    fun everyPhotoAlsoHasASmallVersionForListsAndMap() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        val path = host.api.uploadWallboxPhoto(wallbox.id, jpeg(2400, 1200)).photos.single()
        val thumbnail = ImageIO.read(ByteArrayInputStream(raw.get(PhotoPaths.thumbnail(path)).bodyAsBytes()))
        assertEquals(480, thumbnail.width)
        assertEquals(240, thumbnail.height)
        assertEquals(HttpStatusCode.NotFound, raw.get(PhotoPaths.thumbnail(path).replace("-s.jpg", "-x.jpg")).status)
    }

    @Test
    fun largeImagesAreScaledDownAndPngIsConvertedToJpeg() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        val path = host.api.uploadWallboxPhoto(wallbox.id, image(3200, 2000, "png", BufferedImage.TYPE_INT_ARGB), "x.png", "image/png").photos.single()
        val stored = ImageIO.read(ByteArrayInputStream(raw.get(path).bodyAsBytes()))
        assertEquals(1600, stored.width)
        assertEquals(1000, stored.height)
    }

    @Test
    fun exifMetadataIsStrippedFromStoredPhotos() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        val original = jpeg()
        val marker = "Exif\u0000\u0000SECRET-GPS-POSITION".encodeToByteArray()
        val segmentLength = marker.size + 2
        val withExif = original.copyOfRange(0, 2) +
            byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (segmentLength shr 8).toByte(), segmentLength.toByte()) + marker +
            original.copyOfRange(2, original.size)
        assertTrue(String(withExif, Charsets.ISO_8859_1).contains("SECRET-GPS-POSITION"))

        val path = host.api.uploadWallboxPhoto(wallbox.id, withExif).photos.single()
        assertFalse(String(raw.get(path).bodyAsBytes(), Charsets.ISO_8859_1).contains("SECRET-GPS-POSITION"))
    }

    @Test
    fun onlyTheOwnerMayUploadAndAnonymousUsersCannot() = serverTest {
        val (_, wallbox) = hostWithWallbox()
        val stranger = registerUser("fremd@example.com", host = true)
        assertEquals(403, assertFailsWith<ApiException> { stranger.api.uploadWallboxPhoto(wallbox.id, jpeg()) }.status)
        assertEquals(401, assertFailsWith<ApiException> { newApi().api.uploadWallboxPhoto(wallbox.id, jpeg()) }.status)
        assertEquals(404, assertFailsWith<ApiException> { stranger.api.uploadWallboxPhoto(Uuid.random().toString(), jpeg()) }.status)
        assertEquals(0, photoStorage.size)
    }

    @Test
    fun filesThatAreNotJpegOrPngAreRejected() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        val webp = "RIFF".encodeToByteArray() + ByteArray(4) + "WEBPVP8 ".encodeToByteArray() + ByteArray(32)
        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + "kaputt".encodeToByteArray()
        listOf("hallo".encodeToByteArray(), webp, fakeJpeg, ByteArray(0)).forEach { bytes ->
            assertEquals(422, assertFailsWith<ApiException> { host.api.uploadWallboxPhoto(wallbox.id, bytes) }.status)
        }
        assertEquals(0, photoStorage.size)
        assertTrue(host.api.wallbox(wallbox.id).photos.isEmpty())
    }

    @Test
    fun oversizedUploadsAreRejected() = serverTest(config = testConfig.copy(photoMaxUploadBytes = 2_000)) {
        val (host, wallbox) = hostWithWallbox()
        val big = image(600, 600, "png").let { it + ByteArray(5_000) }
        assertEquals(422, assertFailsWith<ApiException> { host.api.uploadWallboxPhoto(wallbox.id, big) }.status)
        assertEquals(0, photoStorage.size)
    }

    @Test
    fun requestWithoutFileFieldIsABadRequest() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        val token = requireNotNull(host.store.accessToken())
        val response = raw.post("/api/v1/wallboxes/${wallbox.id}/photos") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData { append("other", "x") }))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun aWallboxHoldsAtMostEightPhotosAndRejectedFilesAreCleanedUp() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        repeat(WallboxLimits.MAX_PHOTOS) { host.api.uploadWallboxPhoto(wallbox.id, jpeg(50 + it, 50)) }
        assertEquals(409, assertFailsWith<ApiException> { host.api.uploadWallboxPhoto(wallbox.id, jpeg()) }.status)
        assertEquals(WallboxLimits.MAX_PHOTOS, host.api.wallbox(wallbox.id).photos.size)
        assertEquals(WallboxLimits.MAX_PHOTOS * FILES_PER_PHOTO, photoStorage.size, "das abgelehnte Foto darf keine Datei hinterlassen")
    }

    @Test
    fun ownerCanRemoveAPhotoOthersCannot() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        val first = host.api.uploadWallboxPhoto(wallbox.id, jpeg(100, 100)).photos.single()
        val second = host.api.uploadWallboxPhoto(wallbox.id, jpeg(120, 100)).photos.last()
        val stranger = registerUser("fremd@example.com", host = true)
        assertEquals(403, assertFailsWith<ApiException> { stranger.api.deleteWallboxPhoto(wallbox.id, first) }.status)
        assertEquals(2 * FILES_PER_PHOTO, photoStorage.size)

        val after = host.api.deleteWallboxPhoto(wallbox.id, first)
        assertEquals(listOf(second), after.photos)
        assertEquals(FILES_PER_PHOTO, photoStorage.size)
        assertEquals(HttpStatusCode.NotFound, raw.get(first).status)
        assertEquals(HttpStatusCode.NotFound, raw.get(PhotoPaths.thumbnail(first)).status)
        assertEquals(404, assertFailsWith<ApiException> { host.api.deleteWallboxPhoto(wallbox.id, first) }.status)
    }

    @Test
    fun invalidPhotoNamesNeverReachTheStorage() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        listOf("..%2F..%2Fetc%2Fpasswd", "x.jpg", "..", "%2e%2e%2f%2e%2e%2fsecret.jpg").forEach { name ->
            assertEquals(HttpStatusCode.NotFound, raw.get("/api/v1/wallboxes/${wallbox.id}/photos/$name").status, name)
        }
        assertEquals(404, assertFailsWith<ApiException> { host.api.deleteWallboxPhoto(wallbox.id, "../../etc/passwd") }.status)
    }

    @Test
    fun photosCanBeReorderedButNotReplacedByForeignAddresses() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        val a = host.api.uploadWallboxPhoto(wallbox.id, jpeg(100, 100)).photos.single()
        val b = host.api.uploadWallboxPhoto(wallbox.id, jpeg(110, 100)).photos.last()

        assertEquals(listOf(b, a), host.api.updateWallbox(wallbox.id, UpdateWallboxRequest(photos = listOf(b, a))).photos)
        assertEquals(422, assertFailsWith<ApiException> {
            host.api.updateWallbox(wallbox.id, UpdateWallboxRequest(photos = listOf(b, "https://evil.example/x.jpg")))
        }.status)
        assertEquals(422, assertFailsWith<ApiException> { host.api.updateWallbox(wallbox.id, UpdateWallboxRequest(photos = listOf(a, a))) }.status)
        assertEquals(listOf(b, a), host.api.wallbox(wallbox.id).photos)
    }

    @Test
    fun photosInCreateRequestsAreIgnored() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val created = host.api.createWallbox(newWallbox().copy(photos = listOf("https://evil.example/x.jpg")))
        assertTrue(created.photos.isEmpty())
    }

    @Test
    fun deletingTheWallboxRemovesItsFiles() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        host.api.uploadWallboxPhoto(wallbox.id, jpeg())
        assertEquals(FILES_PER_PHOTO, photoStorage.size)
        host.api.deleteWallbox(wallbox.id)
        assertEquals(0, photoStorage.size)
    }

    @Test
    fun storedBytesStayIdenticalOnRepeatedReads() = serverTest {
        val (host, wallbox) = hostWithWallbox()
        val path = host.api.uploadWallboxPhoto(wallbox.id, jpeg()).photos.single()
        assertContentEquals(raw.get(path).bodyAsBytes(), raw.get(path).bodyAsBytes())
    }

    private companion object {
        /** Große und kleine Fassung. */
        const val FILES_PER_PHOTO = 2
    }
}
