package app.chargeshare.data

import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.Review
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
    suspend fun reviews(wallboxId: String): Outcome<List<Review>>

    /** Freie Slot-Startzeiten, deren Beginn in den lokalen Tag [day] fällt (aufsteigend). */
    suspend fun freeSlotStarts(wallboxId: String, day: LocalDate, zone: TimeZone): Outcome<List<Instant>>
}

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

    override suspend fun reviews(wallboxId: String): Outcome<List<Review>> =
        runCatchingOutcome { api.wallboxReviews(wallboxId, limit = REVIEW_LIMIT).reviews }

    override suspend fun freeSlotStarts(wallboxId: String, day: LocalDate, zone: TimeZone): Outcome<List<Instant>> =
        runCatchingOutcome {
            val range = localDayRange(day, zone)
            utcDatesTouchedBy(day, zone)
                .flatMap { utcDate -> api.wallboxSlots(wallboxId, utcDate.toString()).slots.toStartInstants(utcDate) }
                .filter { it in range }
                .distinct()
                .sorted()
        }

    private companion object {
        const val SEARCH_LIMIT = 50
        const val REVIEW_LIMIT = 20
    }
}
