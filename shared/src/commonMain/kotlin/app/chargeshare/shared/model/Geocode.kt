package app.chargeshare.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /geocode`: Adresse, zu der die Koordinaten gesucht werden. */
@Serializable
data class GeocodeRequest(
    val address: String,
    @SerialName("postal_code") val postalCode: String,
    val city: String,
)

/** Treffer von `POST /geocode`. [displayName] ist die Adresse, wie die Kartendaten sie schreiben (zur Kontrolle durch den Host). */
@Serializable
data class GeocodeResponse(
    val lat: Double,
    val lng: Double,
    @SerialName("display_name") val displayName: String? = null,
)
