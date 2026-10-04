package app.chargeshare.server.geocoding

/** Treffer einer Adresssuche. */
data class GeocodeHit(val lat: Double, val lng: Double, val displayName: String?)

/** Der Dienst ist nicht erreichbar, überlastet oder abgeschaltet (HTTP 503 für die App). */
class GeocoderUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Adresse → Koordinaten. */
interface Geocoder {
    /** @return den besten Treffer oder `null`, wenn die Adresse unbekannt ist
     * @throws GeocoderUnavailableException wenn der Dienst nicht antwortet */
    suspend fun geocode(address: String, postalCode: String, city: String): GeocodeHit?

    companion object {
        /** Nominatim laut Konfiguration, sonst [Disabled]. */
        fun from(config: app.chargeshare.server.config.AppConfig): Geocoder =
            config.geocoderUrl?.let { NominatimGeocoder(it, config.geocoderUserAgent, config.geocoderCountryCodes) } ?: Disabled
    }

    /** Ersatz, wenn kein Dienst konfiguriert ist (`GEOCODER_URL` leer). */
    object Disabled : Geocoder {
        override suspend fun geocode(address: String, postalCode: String, city: String): GeocodeHit =
            throw GeocoderUnavailableException("geocoding is not configured")
    }
}
