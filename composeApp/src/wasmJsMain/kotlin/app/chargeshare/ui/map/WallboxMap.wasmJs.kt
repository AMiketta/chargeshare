package app.chargeshare.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.chargeshare.data.GeoPoint
import app.chargeshare.presentation.map.WallboxPin

/**
 * Web: Karte ist abgeschaltet ([app.chargeshare.platform.isMapSupported] = false).
 * Die Typen müssen trotzdem existieren, weil [app.chargeshare.di.AppContainer] den Holder hält.
 */
@Composable
actual fun WallboxMap(
    pins: List<WallboxPin>,
    map: WallboxMapHolder,
    onPinClick: (wallboxId: String) -> Unit,
    modifier: Modifier,
    activePinId: String?,
    center: GeoPoint,
    contentPadding: PaddingValues,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) { content() }
}

actual class WallboxMapHolder
