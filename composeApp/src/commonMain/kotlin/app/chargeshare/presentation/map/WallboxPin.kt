package app.chargeshare.presentation.map

import app.chargeshare.shared.model.Wallbox
import kotlin.math.max
import kotlin.math.min

/**
 * Markierung einer Wallbox auf der Karte.
 *
 * @property lat geografische Breite (für MapKit und Geodaten)
 * @property lng geografische Länge
 * @property x horizontale Position, normalisiert `0.0..1.0` (siehe [WebMercator], MapCompose)
 * @property y vertikale Position, normalisiert `0.0..1.0`
 */
data class WallboxPin(
    val id: String,
    val title: String,
    val isOnline: Boolean,
    val lat: Double,
    val lng: Double,
    val x: Double,
    val y: Double,
    /** Preis in Euro je kWh; steht im Pin als Preisschild. */
    val pricePerKwh: Double,
)

/** Wallbox -> Pin. Die Karte zeigt nur diese Daten; Tipp auf den Pin öffnet die Wallbox mit der [WallboxPin.id]. */
fun Wallbox.toPin(): WallboxPin = WallboxPin(
    id = id,
    title = name,
    isOnline = isOnline,
    lat = lat,
    lng = lng,
    x = WebMercator.x(lng),
    y = WebMercator.y(lat),
    pricePerKwh = pricePerKwh,
)

/** Rechteck in normalisierten Kartenkoordinaten. */
data class MapBounds(val xLeft: Double, val yTop: Double, val xRight: Double, val yBottom: Double)

/**
 * Umfang aller Pins, mindestens [MIN_SPAN] breit/hoch (ein einzelner Pin soll nicht auf die höchste Zoomstufe springen).
 * `null`, wenn es keine Pins gibt – dann bleibt die Kamera auf dem Standardstandort.
 */
fun List<WallboxPin>.bounds(): MapBounds? {
    if (isEmpty()) return null
    val xs = map { it.x }
    val ys = map { it.y }
    val centerX = (xs.min() + xs.max()) / 2
    val centerY = (ys.min() + ys.max()) / 2
    val halfWidth = max(xs.max() - xs.min(), MIN_SPAN) / 2
    val halfHeight = max(ys.max() - ys.min(), MIN_SPAN) / 2
    return MapBounds(
        xLeft = max(0.0, centerX - halfWidth),
        yTop = max(0.0, centerY - halfHeight),
        xRight = min(1.0, centerX + halfWidth),
        yBottom = min(1.0, centerY + halfHeight),
    )
}

/** Kleinster angezeigter Ausschnitt: 0,0001 der Kartenbreite (am Äquator ca. 4 km). */
const val MIN_SPAN = 0.0001

/** Startzoom ohne Pins (Stadtbild von Bremen). */
const val DEFAULT_ZOOM = 12
