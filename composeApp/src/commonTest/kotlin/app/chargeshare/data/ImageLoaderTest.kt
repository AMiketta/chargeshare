package app.chargeshare.data

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImageLoaderTest {
    private val fetched = mutableListOf<String>()
    private var failing = false

    private fun loader(side: Int = 10, decoded: (ByteArray) -> ImageBitmap? = { ImageBitmap(side, side) }) = ImageLoader(
        apiBaseUrl = "http://host:8080/",
        fetch = { url ->
            fetched += url
            if (failing) null else byteArrayOf(1)
        },
        decode = decoded,
    )

    @Test
    fun relativePathsGetTheServerAddress() {
        val loader = loader()
        assertEquals("http://host:8080/api/v1/x.jpg", loader.urlOf("/api/v1/x.jpg"))
        assertEquals("http://host:8080/api/v1/x.jpg", loader.urlOf("api/v1/x.jpg"))
    }

    @Test
    fun absoluteAddressesStayUntouched() {
        assertEquals("https://cdn.example/x.jpg", loader().urlOf("https://cdn.example/x.jpg"))
    }

    @Test
    fun secondLoadComesFromMemory() = runTest {
        val loader = loader()
        assertNotNull(loader.load("/a.jpg"))
        assertNotNull(loader.load("/a.jpg"))
        assertEquals(listOf("http://host:8080/a.jpg"), fetched)
    }

    @Test
    fun failedFetchGivesNullAndIsNotCached() = runTest {
        val loader = loader()
        failing = true
        assertNull(loader.load("/a.jpg"))
        failing = false
        assertNotNull(loader.load("/a.jpg"))
        assertEquals(2, fetched.size)
    }

    @Test
    fun undecodableDataGivesNull() = runTest {
        assertNull(loader(decoded = { null }).load("/a.jpg"))
    }

    @Test
    fun oldestBitmapIsDroppedWhenThePixelBudgetIsExceeded() = runTest {
        // Zwei Bilder zu je 60 % des Budgets passen nicht gleichzeitig.
        val side = 3_100
        val loader = loader(side = side)
        loader.load("/a.jpg")
        loader.load("/b.jpg")
        assertNull(loader.cached("/a.jpg"))
        assertNotNull(loader.cached("/b.jpg"))
    }
}
