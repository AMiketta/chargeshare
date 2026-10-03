package app.chargeshare.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.chargeshare.presentation.map.DEFAULT_ZOOM
import app.chargeshare.presentation.map.WallboxPin
import app.chargeshare.presentation.map.WebMercator
import app.chargeshare.presentation.map.bounds
import app.chargeshare.presentation.wallbox.DEFAULT_CENTER
import app.chargeshare.ui.AppIcons
import app.chargeshare.ui.ChargeShareColors
import app.chargeshare.ui.Spacing
import app.chargeshare.ui.Strings
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
private val PIN_SIZE = 36.dp
private val PIN_BORDER = 2.dp
private const val ATTRIBUTION_ALPHA = 0.8f

/**
 * Interaktive Karte (OpenStreetMap-Kacheln) mit einem Pin je Wallbox.
 * Die Kamera startet auf Bremen und springt auf den Umfang der Pins, sobald es welche gibt.
 *
 * @param onPinClick wird mit der Wallbox-ID aufgerufen, wenn ein Pin angetippt wird
 */
@Composable
fun WallboxMap(
    pins: List<WallboxPin>,
    tileSource: TileStreamProvider,
    onPinClick: (wallboxId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapState = remember(tileSource) { createMapState(tileSource) }
    DisposableEffect(mapState) { onDispose { mapState.shutdown() } }

    val currentOnPinClick by rememberUpdatedState(onPinClick)
    LaunchedEffect(mapState) { mapState.onMarkerClick { id, _, _ -> currentOnPinClick(id) } }

    LaunchedEffect(mapState, pins) {
        mapState.removeAllMarkers()
        pins.forEach { pin ->
            mapState.addMarker(pin.id, pin.x, pin.y, relativeOffset = Offset(-0.5f, -0.5f)) { PinMarker(pin) }
        }
        val bounds = pins.bounds()
        if (bounds != null) {
            mapState.snapScrollTo(BoundingBox(bounds.xLeft, bounds.yTop, bounds.xRight, bounds.yBottom), Offset(FIT_PADDING, FIT_PADDING))
        } else {
            mapState.snapScrollTo(WebMercator.x(DEFAULT_CENTER.lng), WebMercator.y(DEFAULT_CENTER.lat))
        }
    }

    Box(modifier) {
        MapUI(Modifier.matchParentSize(), state = mapState)
        Text(
            text = Strings.MapAttribution,
            style = MaterialTheme.typography.labelSmall,
            color = ChargeShareColors.OnSurface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.White.copy(alpha = ATTRIBUTION_ALPHA))
                .padding(horizontal = Spacing.Small, vertical = Spacing.XSmall),
        )
    }
}

private fun createMapState(tileSource: TileStreamProvider): MapState =
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
    }.apply { addLayer(tileSource) }

/** Pin einer Wallbox: grün = verfügbar, grau = offline (Status steht zusätzlich in der Beschreibung für Screenreader). */
@Composable
private fun PinMarker(pin: WallboxPin) {
    val color = if (pin.isOnline) ChargeShareColors.Primary else ChargeShareColors.OnSurfaceVariant
    Box(Modifier.size(PIN_TOUCH_TARGET), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(PIN_SIZE)
                .clip(CircleShape)
                .background(color)
                .border(PIN_BORDER, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Bolt, contentDescription = Strings.mapPinDescription(pin.title, pin.isOnline), tint = Color.White)
        }
    }
}
