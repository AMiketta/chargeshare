package app.chargeshare.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import app.chargeshare.data.GeoPoint
import app.chargeshare.presentation.map.WallboxPin
import app.chargeshare.ui.formatDecimal
import app.chargeshare.ui.formatEuro
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.darwin.NSObject

/** Markersichtweite ohne Pins: ca. Stadtausschnitt (~5 km), analog Zoom 12 bei MapCompose. */
private const val EMPTY_REGION_METERS = 5_000.0

/** ChargeShare-Grün (#0B6E4F) für aktive Pins. */
private val ACTIVE_MARKER = UIColor(red = 0x0B / 255.0, green = 0x6E / 255.0, blue = 0x4F / 255.0, alpha = 1.0)
private val INACTIVE_MARKER = UIColor.whiteColor
private val OFFLINE_MARKER = UIColor(red = 0x9E / 255.0, green = 0x9B / 255.0, blue = 0x98 / 255.0, alpha = 1.0)

/**
 * Native Apple-Karte (MapKit) für die Wallbox-Suche.
 *
 * Die [MKMapView] lebt in [WallboxMapHolder] über Tab-Wechsel hinweg (kein Destroy/Recreate, analog PR #38).
 * Apple blendet die eigene Quellenangabe ein – kein OSM-Hinweis.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
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
    val currentOnPinClick by rememberUpdatedState(onPinClick)
    val pinsState = rememberUpdatedState(pins)
    val activeState = rememberUpdatedState(activePinId)
    val centerState = rememberUpdatedState(center)
    // Delegate hält onPinClick – UIKit ruft ihn schwach, deshalb in der Komposition merken.
    val coordinator = remember(map) { map.coordinator }.also { it.onPinClick = { id -> currentOnPinClick(id) } }

    // Kompass und „Rechtliches“ von MapKit richten sich nach den layoutMargins der MKMapView: oben unter die
    // schwebenden Filter-Chips, unten über die Tab-Leiste. Auf iOS entspricht 1 dp genau 1 pt.
    val marginTop = contentPadding.calculateTopPadding().value.toDouble()
    val marginBottom = contentPadding.calculateBottomPadding().value.toDouble()
    LaunchedEffect(map, marginTop, marginBottom) {
        map.mapView.layoutMargins = UIEdgeInsetsMake(marginTop, 0.0, marginBottom, 0.0)
    }

    Box(modifier) {
        UIKitView(
            factory = {
                map.mapView.apply {
                    delegate = coordinator
                    showsCompass = true
                    showsScale = false
                }
            },
            modifier = Modifier.matchParentSize(),
            update = { view ->
                coordinator.syncAnnotations(
                    mapView = view,
                    pins = pinsState.value,
                    activePinId = activeState.value,
                    center = centerState.value,
                )
            },
            // NonCooperative: Karten-Gesten (Pinch/Pan) gehen direkt an MapKit, ohne Compose-Verzögerung.
            // placedAsOverlay = false (Standard, hier bewusst explizit): Die MKMapView liegt UNTER der Compose-Fläche.
            // Nur so werden Compose-Overlays (schwebende Filter-Chips, Stationen-Fenster) über der Karte gezeichnet und
            // bekommen deren Tipps; wo kein Compose-Element liegt, gehen die Gesten an MapKit.
            properties = UIKitInteropProperties(
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = true,
                placedAsOverlay = false,
            ),
        )
        content()
    }
}

/**
 * Eine langlebige [MKMapView] plus Delegate – analog zum MapCompose-Holder ohne `shutdown`.
 * Beim Verlassen des Such-Tabs wird die View nur aus der Hierarchie genommen, nicht freigegeben.
 */
actual class WallboxMapHolder {
    internal val coordinator = MapKitCoordinator()

    @OptIn(ExperimentalForeignApi::class)
    internal val mapView: MKMapView by lazy {
        MKMapView().apply {
            delegate = coordinator
        }
    }
}

/** Pin-Annotation mit Wallbox-ID für Tipps und Preis-Glyph. */
@OptIn(ExperimentalForeignApi::class)
private class PriceAnnotation : MKPointAnnotation() {
    lateinit var wallboxId: String
    var pricePerKwh: Double = 0.0
    var isActive: Boolean = false
    var isOnline: Boolean = true
}

/**
 * MapKit-Delegate: Pins als [MKMarkerAnnotationView] mit Preis-Glyph, Tipp → [onPinClick].
 */
@OptIn(ExperimentalForeignApi::class)
internal class MapKitCoordinator : NSObject(), MKMapViewDelegateProtocol {
    var onPinClick: ((String) -> Unit)? = null

    /** Letzter Stand der Pins/Kamera, damit `update` die Region nicht bei jedem Recompose zurücksetzt. */
    private var lastSignature: String? = null

    fun syncAnnotations(mapView: MKMapView, pins: List<WallboxPin>, activePinId: String?, center: GeoPoint) {
        val signature = buildString {
            append(center.lat).append(',').append(center.lng).append('|').append(activePinId).append('|')
            pins.forEach { append(it.id).append(':').append(it.pricePerKwh).append(':').append(it.isOnline).append(';') }
        }
        if (signature == lastSignature) return
        lastSignature = signature

        val existing = mapView.annotations.mapNotNull { it as? PriceAnnotation }
        if (existing.isNotEmpty()) mapView.removeAnnotations(existing)

        val annotations = pins.map { pin ->
            PriceAnnotation().apply {
                wallboxId = pin.id
                pricePerKwh = pin.pricePerKwh
                isActive = pin.id == activePinId
                isOnline = pin.isOnline
                setCoordinate(CLLocationCoordinate2DMake(pin.lat, pin.lng))
                setTitle(formatEuro(pin.pricePerKwh))
                setSubtitle(pin.title)
            }
        }
        if (annotations.isNotEmpty()) {
            mapView.addAnnotations(annotations)
            mapView.showAnnotations(annotations, animated = true)
        } else {
            val region = MKCoordinateRegionMakeWithDistance(
                CLLocationCoordinate2DMake(center.lat, center.lng),
                EMPTY_REGION_METERS,
                EMPTY_REGION_METERS,
            )
            mapView.setRegion(region, animated = true)
        }
    }

    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
        val price = viewForAnnotation as? PriceAnnotation ?: return null
        val reuseId = "chargeshare.price"
        val view = (mapView.dequeueReusableAnnotationViewWithIdentifier(reuseId) as? MKMarkerAnnotationView)
            ?: MKMarkerAnnotationView(annotation = price, reuseIdentifier = reuseId)
        view.annotation = price
        // Glyph: Preis ohne „€“, sonst zu lang für den Marker-Kopf.
        view.glyphText = formatDecimal(price.pricePerKwh, 2)
        view.markerTintColor = when {
            price.isActive -> ACTIVE_MARKER
            price.isOnline -> INACTIVE_MARKER
            else -> OFFLINE_MARKER
        }
        view.glyphTintColor = when {
            price.isActive -> UIColor.whiteColor
            else -> UIColor.blackColor
        }
        view.canShowCallout = false
        view.setEnabled(true)
        return view
    }

    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val price = didSelectAnnotationView.annotation as? PriceAnnotation ?: return
        onPinClick?.invoke(price.wallboxId)
        mapView.deselectAnnotation(price, animated = false)
    }
}
