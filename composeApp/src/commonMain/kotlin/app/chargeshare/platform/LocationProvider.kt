package app.chargeshare.platform

import app.chargeshare.data.GeoPoint

/**
 * Gerätestandort für die Umkreissuche.
 *
 * Standort nur nach Einwilligung (DSGVO); ohne Freigabe oder bei Fehlern liefert [currentLocation] `null`.
 * Der Standort wird nicht gespeichert oder protokolliert – die Implementierungen geben nur die aktuelle
 * Koordinate zurück.
 *
 * Android und iOS sind angebunden; Desktop und Web nutzen [UnsupportedLocationProvider].
 */
interface LocationProvider {
    /** `false` auf Desktop/Web: die Suche zeigt keinen Standort-Dialog und bleibt beim Standardzentrum. */
    val isSupported: Boolean

    /** Aktueller Freigabestatus, ohne den Systemdialog zu öffnen. */
    fun permissionStatus(): LocationPermissionStatus

    /**
     * Fragt die Plattform nach When-In-Use bzw. Fine/Coarse Location.
     * Darf den Systemdialog zeigen; bei Dauer-Ablehnung kommt sofort [LocationPermissionStatus.Denied].
     */
    suspend fun requestPermission(): LocationPermissionStatus

    /**
     * Aktuelle Koordinaten, oder `null` wenn verweigert, nicht verfügbar oder die Abfrage scheitert.
     * Wirft keine Exceptions an den Aufrufer.
     */
    suspend fun currentLocation(): GeoPoint?
}

/** Freigabestatus für den Gerätestandort. */
sealed interface LocationPermissionStatus {
    /** Noch nicht gefragt (oder Android: noch keine Entscheidung). */
    data object NotDetermined : LocationPermissionStatus

    /** Freigabe erteilt (When-In-Use / Fine oder Coarse). */
    data object Granted : LocationPermissionStatus

    /** Abgelehnt oder dauerhaft gesperrt. */
    data object Denied : LocationPermissionStatus

    /** Plattform bietet keinen Standort (Desktop/Web). */
    data object Unavailable : LocationPermissionStatus
}

/** Rückfall: kein Standort, Suche bleibt beim Standardzentrum (Bremen). */
object UnsupportedLocationProvider : LocationProvider {
    override val isSupported: Boolean = false

    override fun permissionStatus(): LocationPermissionStatus = LocationPermissionStatus.Unavailable

    override suspend fun requestPermission(): LocationPermissionStatus = LocationPermissionStatus.Unavailable

    override suspend fun currentLocation(): GeoPoint? = null
}
