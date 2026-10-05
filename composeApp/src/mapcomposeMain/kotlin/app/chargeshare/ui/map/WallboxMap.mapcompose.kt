package app.chargeshare.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.chargeshare.data.GeoPoint
import app.chargeshare.data.OsmTileSource
import app.chargeshare.presentation.map.DEFAULT_ZOOM
import app.chargeshare.presentation.map.WallboxPin
import app.chargeshare.presentation.map.WebMercator
import app.chargeshare.presentation.map.bounds
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Elevation
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
import app.chargeshare.ui.formatEuro
import ovh.plrapps.mapcompose.api.BoundingBox
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.onMarkerClick
import ovh.plrapps.mapcompose.api.removeAllMarkers
import ovh.plrapps.mapcompose.api.snapScrollTo
import ovh.plrapps.mapcompose.core.TileStreamProvider
import ovh.plrapps.mapcompose.ui.MapUI
import ovh.plrapps.mapcompose.ui.layout.Forced
import ovh.plrapps.mapcompose.ui.state.MapState

/** Kleinster Zoom (Deutschland/Europa-Überblick), weiter heraus ist für die Suche sinnlos. */
private const val MIN_ZOOM = 5

/** Gleichzeitige Kachel-Downloads; OSM erlaubt nur wenige parallele Verbindungen. */
private const val TILE_WORKERS = 4

/** Abstand der Kamera zum Rand des Pin-Umfangs, als Anteil der Kartengröße. */
private const val FIT_PADDING = 0.15f

private val PIN_TOUCH_TARGET = Spacing.MinTouchTarget
private val PIN_BORDER = 1.5.dp
private const val ATTRIBUTION_ALPHA = 0.8f

/**
 * MapCompose + OpenStreetMap (Android und Desktop).
 *
 * Bewusst kein `remember { MapState(…) }` + `shutdown()` beim Verlassen: siehe [WallboxMapHolder].
 */
@Composable
actual fun WallboxMap(
    pins: List<WallboxPin>,
    map: WallboxMapHolder,
    onPinClick: (wallboxId: String) -> Unit,
    modifier: Modifier,
    activePinId: String?,
    center: GeoPoint,
    content: @Composable BoxScope.() -> Unit,
) {
    val mapState = map.state

    val currentOnPinClick by rememberUpdatedState(onPinClick)
    val currentActivePinId = rememberUpdatedState(activePinId)
    LaunchedEffect(mapState) { mapState.onMarkerClick { id, _, _ -> currentOnPinClick(id) } }

    LaunchedEffect(mapState, pins, center) {
        mapState.removeAllMarkers()
        pins.forEach { pin ->
            mapState.addMarker(pin.id, pin.x, pin.y, relativeOffset = Offset(-0.5f, -0.5f)) {
                PinMarker(pin, isActive = currentActivePinId.value == pin.id)
            }
        }
        val bounds = pins.bounds()
        if (bounds != null) {
            mapState.snapScrollTo(
                BoundingBox(bounds.xLeft, bounds.yTop, bounds.xRight, bounds.yBottom),
                Offset(FIT_PADDING, FIT_PADDING),
            )
        } else {
            mapState.snapScrollTo(WebMercator.x(center.lng), WebMercator.y(center.lat))
        }
    }

    Box(modifier) {
        MapUI(Modifier.matchParentSize(), state = mapState)
        content()
        Text(
            text = Strings.MapAttribution,
            style = MaterialTheme.typography.labelSmall,
            color = ChargeShareColors.OnSurface,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(ChargeShareColors.Surface.copy(alpha = ATTRIBUTION_ALPHA))
                .padding(horizontal = Spacing.Small, vertical = Spacing.XSmall),
        )
    }
}

/**
 * Hält **einen** [MapState] für die gesamte App-Laufzeit.
 *
 * Hintergrund (früher iOS mit MapCompose): `MapState.shutdown` schloss den Tile-Dispatcher, während Worker noch liefen –
 * auf Kotlin/Native Absturz. Deshalb kein `shutdown`; der Zustand bleibt bis Prozessende.
 */
actual class WallboxMapHolder {
    private val tileSource by lazy { OsmTileSource() }

    /** Wird beim ersten Anzeigen der Karte (Main-Thread) angelegt und danach nie beendet. */
    val state: MapState by lazy { createMapState(tileSource) }
}

private fun createMapState(tileSource: OsmTileSource): MapState =
    MapState(
        levelCount = WebMercator.LEVEL_COUNT,
        fullWidth = WebMercator.FULL_SIZE_PX,
        fullHeight = WebMercator.FULL_SIZE_PX,
        tileSize = WebMercator.TILE_SIZE_PX,
        workerCount = TILE_WORKERS,
    ) {
        scale(WebMercator.scaleForZoom(DEFAULT_ZOOM))
        minimumScaleMode(Forced(WebMercator.scaleForZoom(MIN_ZOOM)))
        maxScale(WebMercator.scaleForZoom(WebMercator.MAX_ZOOM))
    }.apply {
        addLayer(TileStreamProvider { row, col, zoomLvl -> tileSource.getTileStream(row, col, zoomLvl) })
    }

/**
 * Pin einer Wallbox als Preisschild. Aktiv: tiefgrün mit weißer Schrift; sonst weiß mit grauem Rand
 * (Offline-Wallboxes mit gedämpfter Schrift).
 */
@Composable
private fun PinMarker(pin: WallboxPin, isActive: Boolean) {
    val container = if (isActive) ChargeShareColors.Primary else ChargeShareColors.Surface
    val border = if (isActive) ChargeShareColors.Primary else ChargeShareColors.Outline
    val textColor = when {
        isActive -> ChargeShareColors.OnPrimary
        pin.isOnline -> ChargeShareColors.OnSurface
        else -> ChargeShareColors.OnSurfaceMuted
    }
    val description = "${Strings.mapPinDescription(pin.title, pin.isOnline)}, ${formatEuro(pin.pricePerKwh)}"
    Box(
        modifier = Modifier.defaultMinSize(PIN_TOUCH_TARGET, PIN_TOUCH_TARGET).clearAndSetSemantics {
            contentDescription = description
        },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = container,
            shadowElevation = Elevation.Raised,
            border = BorderStroke(PIN_BORDER, border),
        ) {
            Text(
                formatEuro(pin.pricePerKwh),
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                maxLines = 1,
                modifier = Modifier.padding(
                    horizontal = Spacing.Small + Spacing.XSmall,
                    vertical = Spacing.XSmall + Spacing.XSmall / 2,
                ),
            )
        }
    }
}
