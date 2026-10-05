package app.chargeshare.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.chargeshare.data.GeoPoint
import app.chargeshare.platform.LocationPermissionStatus
import app.chargeshare.platform.LocationProvider
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gerätestandort über die Android-[LocationManager]-API (kein Play-Services nötig).
 *
 * Muss in `onCreate` der Activity erzeugt werden, weil der Permission-Launcher vor dem Start
 * registriert sein muss (wie [AndroidPhotoPicker]).
 */
class AndroidLocationProvider(private val activity: ComponentActivity) : LocationProvider {
    override val isSupported: Boolean = true

    private var pendingPermission: CancellableContinuation<Boolean>? = null

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pendingPermission?.resume(granted)
        pendingPermission = null
    }

    override fun permissionStatus(): LocationPermissionStatus =
        if (hasLocationPermission()) LocationPermissionStatus.Granted else LocationPermissionStatus.NotDetermined

    override suspend fun requestPermission(): LocationPermissionStatus {
        if (hasLocationPermission()) return LocationPermissionStatus.Granted
        val granted = suspendCancellableCoroutine { continuation ->
            pendingPermission?.resume(false)
            pendingPermission = continuation
            continuation.invokeOnCancellation { pendingPermission = null }
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
        return if (granted) LocationPermissionStatus.Granted else LocationPermissionStatus.Denied
    }

    override suspend fun currentLocation(): GeoPoint? {
        if (!hasLocationPermission()) return null
        return withContext(Dispatchers.IO) {
            runCatching { readLocation() }.getOrNull()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun readLocation(): GeoPoint? {
        val manager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = newestLastKnown(manager)
        if (last != null && ageMs(last) <= FRESH_LAST_KNOWN_MS) {
            return GeoPoint(lat = last.latitude, lng = last.longitude)
        }
        val fresh = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { awaitSingleUpdate(manager) }
        val best = fresh ?: last ?: return null
        return GeoPoint(lat = best.latitude, lng = best.longitude)
    }

    @SuppressLint("MissingPermission")
    private fun newestLastKnown(manager: LocationManager): Location? {
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        return providers
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitSingleUpdate(manager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
            }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) {
                        runCatching { manager.removeUpdates(this) }
                        continuation.resume(location)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) = Unit
            }
            continuation.invokeOnCancellation {
                runCatching { manager.removeUpdates(listener) }
            }
            runCatching {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }.onFailure {
                continuation.resume(null)
            }
        }

    private fun ageMs(location: Location): Long = (System.currentTimeMillis() - location.time).coerceAtLeast(0L)

    private companion object {
        /** Frisches Last-Known darf die teure Einzelabfrage ersetzen. */
        const val FRESH_LAST_KNOWN_MS = 5 * 60 * 1000L
        const val LOCATION_TIMEOUT_MS = 8_000L
    }
}
