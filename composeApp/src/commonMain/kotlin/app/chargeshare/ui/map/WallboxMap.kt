package app.chargeshare.ui.map

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.chargeshare.data.GeoPoint
import app.chargeshare.presentation.map.WallboxPin
import app.chargeshare.presentation.wallbox.DEFAULT_CENTER

/**
 * Interaktive Karte mit einem Pin je Wallbox.
 *
 * - **Android / Desktop:** MapCompose mit OpenStreetMap-Kacheln
 * - **iOS:** native Apple MapKit (`MKMapView`)
 * - **Web:** Platzhalter (Karte ist dort abgeschaltet, `isMapSupported = false`)
 *
 * Die Kamera startet auf [center] (Gerätestandort oder Bremen) und springt auf den Umfang der Pins, sobald es welche gibt.
 * Jeder Pin ist ein Preisschild; der aktive Pin ist hervorgehoben.
 *
 * Der Kartenzustand lebt in [WallboxMapHolder] für die gesamte App-Laufzeit (kein hartes Abschalten beim Tab-Wechsel,
 * siehe PR #38 / iOS-Absturz mit MapCompose-`shutdown`).
 *
 * @param activePinId Wallbox-ID des hervorgehobenen Pins (oder `null`)
 * @param onPinClick wird mit der Wallbox-ID aufgerufen, wenn ein Pin angetippt wird
 * @param content Überlagerung über der Karte (z. B. das Stationen-Fenster); wird unten ausgerichtet
 */
@Composable
expect fun WallboxMap(
    pins: List<WallboxPin>,
    map: WallboxMapHolder,
    onPinClick: (wallboxId: String) -> Unit,
    modifier: Modifier = Modifier,
    activePinId: String? = null,
    center: GeoPoint = DEFAULT_CENTER,
    content: @Composable BoxScope.() -> Unit = {},
)

/**
 * Hält den Kartenzustand der Suche einmal pro Prozess (Exemplar in `AppContainer`).
 *
 * Auf Android/Desktop: langlebiger MapCompose-`MapState` ohne `shutdown`.
 * Auf iOS: langlebige `MKMapView`, die beim Tab-Wechsel nicht zerstört wird.
 */
expect class WallboxMapHolder()
