package app.chargeshare.data

import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.HostProfile
import app.chargeshare.shared.model.Review
import app.chargeshare.shared.model.SlotsResponse
import app.chargeshare.shared.model.TimeSlot
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.shared.model.WallboxSearch
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Geografischer Punkt (Suchzentrum). */
data class GeoPoint(val lat: Double, val lng: Double)

/** Suchfilter der Wallbox-Liste. */
data class WallboxFilter(
    val radiusM: Double = DEFAULT_RADIUS_M,
    val connector: ConnectorType? = null,
    val minPowerKw: Double? = null,
    val maxPricePerKwh: Double? = null,
    val onlyOnline: Boolean = false,
) {
    companion object {
        const val DEFAULT_RADIUS_M = 10_000.0
    }
}

/** Suche, Details, Bewertungen und freie Slots von Wallboxen. */
interface WallboxRepository {
    /** Wallboxen im Umkreis um [center], nach Entfernung sortiert. Das Online-Filter wirkt clientseitig in der UI. */
    suspend fun search(center: GeoPoint, filter: WallboxFilter): Outcome<List<Wallbox>>
    suspend fun wallbox(id: String): Outcome<Wallbox>

    /** Ortssuche (Ort, Stadtteil, PLZ) → neues Suchzentrum. [AppError.NotFound] = Ort unbekannt. */
    suspend fun findPlace(query: String): Outcome<GeoPoint>
    suspend fun reviews(wallboxId: String): Outcome<List<Review>>

    /** Öffentliches Profil des Hosts der Wallbox (gekürzter Name, Mitglied seit, Sessions, Superhost). */
    suspend fun hostProfile(wallboxId: String): Outcome<HostProfile>

    /** Slot-Startzeiten, deren Beginn in den lokalen Tag [day] fällt: frei buchbare und bereits belegte (je aufsteigend). */
    suspend fun daySlots(wallboxId: String, day: LocalDate, zone: TimeZone): Outcome<DaySlots>

    /** Nur die frei buchbaren Startzeiten von [daySlots]. */
    suspend fun freeSlotStarts(wallboxId: String, day: LocalDate, zone: TimeZone): Outcome<List<Instant>> =
        when (val result = daySlots(wallboxId, day, zone)) {
            is Outcome.Success -> Outcome.Success(result.value.free)
            is Outcome.Failure -> result
        }
}

/** Startzeiten eines Tages: [free] sind buchbar, [locked] liegen im Verfügbarkeitsfenster, sind aber belegt. */
data class DaySlots(val free: List<Instant>, val locked: List<Instant> = emptyList())

class ApiWallboxRepository(private val api: ChargeShareApi) : WallboxRepository {

    override suspend fun search(center: GeoPoint, filter: WallboxFilter): Outcome<List<Wallbox>> =
        runCatchingOutcome {
            api.searchWallboxes(
                WallboxSearch(
                    lat = center.lat,
                    lng = center.lng,
                    radiusM = filter.radiusM,
                    connector = filter.connector,
                    minPowerKw = filter.minPowerKw,
                    maxPricePerKwh = filter.maxPricePerKwh,
                    limit = SEARCH_LIMIT,
                ),
            ).wallboxes
        }

    override suspend fun wallbox(id: String): Outcome<Wallbox> = runCatchingOutcome { api.wallbox(id) }

    override suspend fun findPlace(query: String): Outcome<GeoPoint> =
        runCatchingOutcome { api.geocodePlace(query).let { GeoPoint(it.lat, it.lng) } }

    override suspend fun reviews(wallboxId: String): Outcome<List<Review>> =
        runCatchingOutcome { api.wallboxReviews(wallboxId, limit = REVIEW_LIMIT).reviews }

    override suspend fun hostProfile(wallboxId: String): Outcome<HostProfile> = runCatchingOutcome { api.wallboxHost(wallboxId) }

    override suspend fun daySlots(wallboxId: String, day: LocalDate, zone: TimeZone): Outcome<DaySlots> =
        runCatchingOutcome {
            val range = localDayRange(day, zone)
            val responses = utcDatesTouchedBy(day, zone).map { utcDate -> utcDate to api.wallboxSlots(wallboxId, utcDate.toString()) }
            fun starts(pick: (SlotsResponse) -> List<TimeSlot>): List<Instant> =
                responses.flatMap { (utcDate, response) -> pick(response).toStartInstants(utcDate) }
                    .filter { it in range }
                    .distinct()
                    .sorted()
            DaySlots(free = starts { it.slots }, locked = starts { it.locked })
        }

    private companion object {
        const val SEARCH_LIMIT = 50
        const val REVIEW_LIMIT = 20
    }
}
