package app.chargeshare.platform

import app.chargeshare.data.GeoPoint
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * Gerätestandort über CoreLocation (When-In-Use).
 *
 * Braucht `NSLocationWhenInUseUsageDescription` in Info.plist (siehe `iosApp/project.yml`).
 * Ungetestet im Simulator auf der Linux-Box – manueller Check auf dem Mac.
 */
@OptIn(ExperimentalForeignApi::class)
class IosLocationProvider : LocationProvider {
    override val isSupported: Boolean = true

    private val manager = CLLocationManager().apply {
        desiredAccuracy = kCLLocationAccuracyHundredMeters
    }
    private val delegate = Delegate()

    init {
        manager.delegate = delegate
    }

    override fun permissionStatus(): LocationPermissionStatus =
        mapStatus(manager.authorizationStatus)

    override suspend fun requestPermission(): LocationPermissionStatus {
        val current = permissionStatus()
        if (current != LocationPermissionStatus.NotDetermined) return current
        return suspendCancellableCoroutine { continuation ->
            delegate.waitForAuthorization(continuation)
            manager.requestWhenInUseAuthorization()
        }
    }

    override suspend fun currentLocation(): GeoPoint? {
        if (permissionStatus() != LocationPermissionStatus.Granted) return null
        return suspendCancellableCoroutine { continuation ->
            delegate.waitForLocation(continuation)
            manager.requestLocation()
        }
    }

    private fun mapStatus(status: CLAuthorizationStatus): LocationPermissionStatus = when (status) {
        kCLAuthorizationStatusNotDetermined -> LocationPermissionStatus.NotDetermined
        kCLAuthorizationStatusAuthorizedWhenInUse,
        kCLAuthorizationStatusAuthorizedAlways,
        -> LocationPermissionStatus.Granted
        kCLAuthorizationStatusDenied,
        kCLAuthorizationStatusRestricted,
        -> LocationPermissionStatus.Denied
        else -> LocationPermissionStatus.Denied
    }

    private inner class Delegate : NSObject(), CLLocationManagerDelegateProtocol {
        private var authWaiter: CancellableContinuation<LocationPermissionStatus>? = null
        private var locationWaiter: CancellableContinuation<GeoPoint?>? = null

        fun waitForAuthorization(continuation: CancellableContinuation<LocationPermissionStatus>) {
            authWaiter?.cancel()
            authWaiter = continuation
            continuation.invokeOnCancellation { authWaiter = null }
        }

        fun waitForLocation(continuation: CancellableContinuation<GeoPoint?>) {
            locationWaiter?.cancel()
            locationWaiter = continuation
            continuation.invokeOnCancellation { locationWaiter = null }
        }

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            val waiter = authWaiter ?: return
            authWaiter = null
            if (waiter.isActive) waiter.resume(mapStatus(manager.authorizationStatus))
        }

        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val waiter = locationWaiter ?: return
            locationWaiter = null
            val location = didUpdateLocations.lastOrNull() as? CLLocation
            val point = location?.coordinate?.useContents {
                GeoPoint(lat = latitude, lng = longitude)
            }
            if (waiter.isActive) waiter.resume(point)
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            val waiter = locationWaiter ?: return
            locationWaiter = null
            if (waiter.isActive) waiter.resume(null)
        }
    }
}
