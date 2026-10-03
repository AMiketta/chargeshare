package app.chargeshare.shared.model

import app.chargeshare.shared.util.InstantAsStringSerializer
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ConnectorType(val displayName: String) {
    @SerialName("type2") TYPE2("Type 2"),
    @SerialName("ccs") CCS("CCS"),
    @SerialName("chademo") CHADEMO("CHAdeMO"),
    @SerialName("type1") TYPE1("Type 1"),
}

/** Zeitfenster innerhalb eines Tages, `"HH:MM"` (Go: `models.TimeSlot`). */
@Serializable
data class TimeSlot(val start: String, val end: String)

/**
 * Wochenverfügbarkeit: Wochentag (`"monday"`, ...) -> Zeitfenster.
 * Go: `models.WeeklyAvailability` = `map[string][]TimeSlot`.
 */
typealias WeeklyAvailability = Map<String, List<TimeSlot>>

/** Grenzen der Wallbox-Felder; Server und Apps prüfen dieselben Werte. */
object WallboxLimits {
    const val MAX_NAME_LENGTH = 200
    const val MAX_DESCRIPTION_LENGTH = 1000

    /** Obergrenze der Ladeleistung (Schnellladesäulen liegen bei 350 kW); schützt auch vor Tippfehlern. */
    const val MAX_POWER_KW = 350.0

    /** Obergrenze des Preises in Euro pro kWh (Tippfehler-Schutz, z. B. „34“ statt „0,34“). */
    const val MAX_PRICE_PER_KWH = 5.0
}

/** Go: `models.Wallbox`. */
@Serializable
data class Wallbox(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val address: String,
    val city: String,
    @SerialName("postal_code") val postalCode: String,
    val lat: Double,
    val lng: Double,
    @SerialName("power_kw") val powerKw: Double,
    @SerialName("connector_type") val connectorType: ConnectorType,
    @SerialName("price_per_kwh") val pricePerKwh: Double,
    @SerialName("min_duration_min") val minDurationMin: Int = 30,
    @SerialName("max_duration_min") val maxDurationMin: Int = 480,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("is_metered") val isMetered: Boolean = false,
    @SerialName("ocpp_id") val ocppId: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    /** Freitext des Hosts (max. [WallboxLimits.MAX_DESCRIPTION_LENGTH] Zeichen). */
    val description: String? = null,
    val photos: List<String> = emptyList(),
    val availability: WeeklyAvailability = emptyMap(),
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("created_at") @Serializable(InstantAsStringSerializer::class) val createdAt: Instant,
    @SerialName("updated_at") @Serializable(InstantAsStringSerializer::class) val updatedAt: Instant,
    /** Entfernung in Metern, nur bei Umkreissuche gesetzt. */
    @SerialName("distance_m") val distanceM: Double? = null,
) {
    val fullAddress: String get() = "$address, $postalCode $city"
}

@Serializable
data class CreateWallboxRequest(
    val name: String,
    val address: String,
    val city: String,
    @SerialName("postal_code") val postalCode: String,
    val lat: Double,
    val lng: Double,
    @SerialName("power_kw") val powerKw: Double,
    @SerialName("connector_type") val connectorType: ConnectorType,
    @SerialName("price_per_kwh") val pricePerKwh: Double,
    @SerialName("min_duration_min") val minDurationMin: Int? = null,
    @SerialName("max_duration_min") val maxDurationMin: Int? = null,
    @SerialName("is_metered") val isMetered: Boolean = false,
    @SerialName("ocpp_id") val ocppId: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val description: String? = null,
    val photos: List<String>? = null,
    val availability: WeeklyAvailability = emptyMap(),
)

@Serializable
data class UpdateWallboxRequest(
    val name: String? = null,
    val address: String? = null,
    val city: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("power_kw") val powerKw: Double? = null,
    @SerialName("connector_type") val connectorType: ConnectorType? = null,
    @SerialName("price_per_kwh") val pricePerKwh: Double? = null,
    @SerialName("min_duration_min") val minDurationMin: Int? = null,
    @SerialName("max_duration_min") val maxDurationMin: Int? = null,
    @SerialName("is_metered") val isMetered: Boolean? = null,
    @SerialName("ocpp_id") val ocppId: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    /** Leerer Text entfernt die Beschreibung. */
    val description: String? = null,
    val photos: List<String>? = null,
    val availability: WeeklyAvailability? = null,
)

@Serializable
data class WallboxStatusRequest(@SerialName("is_online") val isOnline: Boolean)

@Serializable
data class WallboxStatusResponse(@SerialName("is_online") val isOnline: Boolean)

/** Antwort von `GET /wallboxes`. */
@Serializable
data class WallboxListResponse(
    val wallboxes: List<Wallbox> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
)

/** Antwort von `GET /wallboxes/:id/slots?date=YYYY-MM-DD` (freie 30-Minuten-Slots, `HH:MM`, UTC). */
@Serializable
data class SlotsResponse(
    val date: String,
    val slots: List<TimeSlot> = emptyList(),
)

/** Suchfilter fuer `GET /wallboxes` (Go: `models.WallboxListFilter`, Query-Parameter). */
data class WallboxSearch(
    val lat: Double? = null,
    val lng: Double? = null,
    val radiusM: Double? = null,
    val connector: ConnectorType? = null,
    val minPowerKw: Double? = null,
    val maxPricePerKwh: Double? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)
