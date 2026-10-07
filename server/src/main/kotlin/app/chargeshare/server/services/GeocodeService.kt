package app.chargeshare.server.services

import app.chargeshare.server.geocoding.Geocoder
import app.chargeshare.server.geocoding.GeocoderUnavailableException
import app.chargeshare.server.plugins.RateLimiter
import app.chargeshare.server.util.notFound
import app.chargeshare.server.util.serviceUnavailable
import app.chargeshare.server.util.tooManyRequests
import app.chargeshare.server.util.validationError
import app.chargeshare.shared.model.GeocodeRequest
import app.chargeshare.shared.model.GeocodeResponse
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * Koordinaten zu einer Wallbox-Adresse. Der Dienst fragt im Namen des Servers an (siehe [app.chargeshare.server.geocoding.NominatimGeocoder]),
 * damit die App keine eigene `User-Agent`-/Rate-Limit-Pflicht gegenüber OpenStreetMap hat. Je Nutzer sind
 * [PER_USER_LIMIT] Anfragen pro Minute erlaubt.
 */
class GeocodeService(
    private val geocoder: Geocoder,
    private val limiter: RateLimiter = RateLimiter(PER_USER_LIMIT, 1.minutes),
) {
    suspend fun lookup(userId: Uuid, request: GeocodeRequest): GeocodeResponse {
        if (request.address.isBlank()) throw validationError("address: must not be blank")
        if (request.postalCode.isBlank()) throw validationError("postal_code: must not be blank")
        if (request.city.isBlank()) throw validationError("city: must not be blank")
        if (listOf(request.address, request.postalCode, request.city).any { it.length > MAX_FIELD_LENGTH }) {
            throw validationError("address fields are too long")
        }
        if (!limiter.tryAcquire(userId.toString()).allowed) throw tooManyRequests("too many address lookups, please try again later")
        val hit = try {
            geocoder.geocode(request.address, request.postalCode, request.city)
        } catch (e: GeocoderUnavailableException) {
            throw serviceUnavailable("address lookup is currently unavailable")
        } ?: throw notFound("address not found")
        return GeocodeResponse(hit.lat, hit.lng, hit.displayName)
    }

    /** Ortssuche für das Suchzentrum (`GET /geocode/place?q=`). Teilt sich das Kontingent mit [lookup]. */
    suspend fun searchPlace(userId: Uuid, query: String?): GeocodeResponse {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) throw validationError("q: must not be blank")
        if (q.length > MAX_FIELD_LENGTH) throw validationError("q: is too long")
        if (!limiter.tryAcquire(userId.toString()).allowed) throw tooManyRequests("too many place lookups, please try again later")
        val hit = try {
            geocoder.searchPlace(q)
        } catch (e: GeocoderUnavailableException) {
            throw serviceUnavailable("place search is currently unavailable")
        } ?: throw notFound("place not found")
        return GeocodeResponse(hit.lat, hit.lng, hit.displayName)
    }

    private companion object {
        const val PER_USER_LIMIT = 20
        const val MAX_FIELD_LENGTH = 200
    }
}
