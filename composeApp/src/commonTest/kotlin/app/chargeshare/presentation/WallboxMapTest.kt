package app.chargeshare.presentation

import app.chargeshare.presentation.map.MIN_SPAN
import app.chargeshare.presentation.map.WallboxPin
import app.chargeshare.presentation.map.WebMercator
import app.chargeshare.presentation.map.bounds
import app.chargeshare.presentation.map.toPin
import app.chargeshare.presentation.wallbox.DEFAULT_CENTER
import app.chargeshare.testing.testWallbox
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WallboxMapTest {
    private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1e-9) =
        assertTrue(abs(expected - actual) < tolerance, "erwartet $expected, war $actual")

    @Test
    fun mercatorMapsEquatorAndMeridianToMapCenter() {
        assertNear(0.5, WebMercator.x(0.0))
        assertNear(0.5, WebMercator.y(0.0))
    }

    @Test
    fun mercatorMapsBremenToKnownTilePosition() {
        assertNear(0.52445, WebMercator.x(DEFAULT_CENTER.lng), 1e-5)
        assertNear(0.32539, WebMercator.y(DEFAULT_CENTER.lat), 1e-5)
    }

    @Test
    fun mercatorClampsPolesAndDateLineIntoMap() {
        assertNear(0.0, WebMercator.y(90.0), 1e-9)
        assertNear(1.0, WebMercator.y(-90.0), 1e-9)
        assertEquals(1.0, WebMercator.x(200.0))
        assertEquals(0.0, WebMercator.x(-200.0))
    }

    @Test
    fun scaleHalvesWithEveryZoomLevelBelowMax() {
        assertEquals(1.0, WebMercator.scaleForZoom(WebMercator.MAX_ZOOM))
        assertEquals(0.5, WebMercator.scaleForZoom(WebMercator.MAX_ZOOM - 1))
        assertEquals(1.0 / 64, WebMercator.scaleForZoom(WebMercator.MAX_ZOOM - 6))
    }

    @Test
    fun wallboxBecomesPinWithIdNameStatusAndProjectedPosition() {
        val pin = testWallbox("a", name = "Am Deich 22", isOnline = false).copy(lat = 53.0793, lng = 8.8017).toPin()
        assertEquals("a", pin.id)
        assertEquals("Am Deich 22", pin.title)
        assertEquals(false, pin.isOnline)
        assertNear(WebMercator.x(8.8017), pin.x)
        assertNear(WebMercator.y(53.0793), pin.y)
    }

    @Test
    fun noPinsHaveNoBounds() {
        assertNull(emptyList<WallboxPin>().bounds())
    }

    @Test
    fun singlePinGetsMinimumSpanAroundIt() {
        val pin = testWallbox("a").toPin()
        val bounds = assertNotNull(listOf(pin).bounds())
        assertNear(MIN_SPAN, bounds.xRight - bounds.xLeft, 1e-12)
        assertNear(MIN_SPAN, bounds.yBottom - bounds.yTop, 1e-12)
        assertNear(pin.x, (bounds.xLeft + bounds.xRight) / 2, 1e-12)
    }

    @Test
    fun boundsCoverAllPins() {
        val west = testWallbox("w").copy(lat = 53.05, lng = 8.70).toPin()
        val east = testWallbox("e").copy(lat = 53.10, lng = 8.90).toPin()
        val bounds = assertNotNull(listOf(west, east).bounds())
        assertTrue(bounds.xLeft <= west.x && bounds.xRight >= east.x)
        assertTrue(bounds.yTop <= east.y && bounds.yBottom >= west.y)
    }
}
