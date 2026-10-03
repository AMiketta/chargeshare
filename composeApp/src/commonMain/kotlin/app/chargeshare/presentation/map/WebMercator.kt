package app.chargeshare.presentation.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Web-Mercator-Projektion wie bei OpenStreetMap-Kacheln: WGS84 (Grad) -> normalisierte Kartenkoordinaten.
 * `x` und `y` liegen in `0.0..1.0` (links oben = 0/0), so rechnet auch die Karten-Komponente.
 */
object WebMercator {
    /** Höchster Kachel-Zoom, den die Karte lädt. Bei diesem Zoom ist die Karte 256 · 2^18 Pixel breit (Maßstab 1.0). */
    const val MAX_ZOOM = 18

    /** Kantenlänge einer OSM-Kachel in Pixeln. */
    const val TILE_SIZE_PX = 256

    /** Anzahl der Zoomstufen 0..[MAX_ZOOM]. */
    const val LEVEL_COUNT = MAX_ZOOM + 1

    /** Breite/Höhe der Gesamtkarte bei Maßstab 1.0 (Zoom [MAX_ZOOM]). */
    const val FULL_SIZE_PX = TILE_SIZE_PX * (1 shl MAX_ZOOM)

    /** Breitengrad-Grenze der Projektion (darüber/darunter gibt es keine Kacheln). */
    private const val MAX_LATITUDE = 85.05112878
    private const val FULL_CIRCLE_DEG = 360.0
    private const val HALF_CIRCLE_DEG = 180.0

    fun x(lng: Double): Double = (lng.coerceIn(-HALF_CIRCLE_DEG, HALF_CIRCLE_DEG) + HALF_CIRCLE_DEG) / FULL_CIRCLE_DEG

    fun y(lat: Double): Double {
        val radians = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / HALF_CIRCLE_DEG
        return (1 - ln(tan(radians) + 1 / cos(radians)) / PI) / 2
    }

    /** Maßstab der Karten-Komponente für einen OSM-Zoom (Zoom [MAX_ZOOM] = 1.0, jede Stufe darunter halbiert). */
    fun scaleForZoom(zoom: Int): Double = 2.0.pow(zoom - MAX_ZOOM)
}
