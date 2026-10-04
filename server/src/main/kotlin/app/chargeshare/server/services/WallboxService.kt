package app.chargeshare.server.services

import app.chargeshare.server.db.Bookings
import app.chargeshare.server.db.Db
import app.chargeshare.server.db.PgState
import app.chargeshare.server.db.Users
import app.chargeshare.server.db.Wallboxes
import app.chargeshare.server.db.dbValue
import app.chargeshare.server.db.sqlState
import app.chargeshare.server.db.toConnector
import app.chargeshare.server.db.toWallbox
import app.chargeshare.server.util.conflict
import app.chargeshare.server.util.forbidden
import app.chargeshare.server.util.notFound
import app.chargeshare.server.util.toInstant2
import app.chargeshare.server.util.validationError
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.CreateWallboxRequest
import app.chargeshare.shared.model.SlotsResponse
import app.chargeshare.shared.model.TimeSlot
import app.chargeshare.shared.model.WallboxLimits
import app.chargeshare.shared.model.UpdateWallboxRequest
import app.chargeshare.shared.model.Wallbox
import app.chargeshare.shared.model.WallboxListResponse
import app.chargeshare.shared.model.WallboxSearch
import app.chargeshare.shared.model.WeeklyAvailability
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** `earth_distance(ll_to_earth(lat,lng), ll_to_earth(?,?))` in Metern (Erweiterungen cube + earthdistance). */
private class EarthDistance(private val lat: Double, private val lng: Double) : org.jetbrains.exposed.v1.core.Function<Double>(DoubleColumnType()) {
    init {
        require(lat.isFinite() && lng.isFinite())
    }

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            append("earth_distance(ll_to_earth(")
            append(Wallboxes.lat)
            append(", ")
            append(Wallboxes.lng)
            append("), ll_to_earth($lat, $lng))")
        }
    }
}

class WallboxService(private val db: Db, private val payouts: PayoutService) {

    // ---- Suche / Lesen ----------------------------------------------------------------------

    suspend fun search(f: WallboxSearch): WallboxListResponse {
        val limit = f.limit?.takeIf { it in 1..100 } ?: 20
        val offset = f.offset?.takeIf { it >= 0 } ?: 0
        val radius = f.radiusM ?: 10_000.0
        val qLat = f.lat
        val qLng = f.lng
        val geo = if (qLat != null && qLng != null) {
            if (qLat !in -90.0..90.0 || qLng !in -180.0..180.0) throw validationError("lat/lng out of range")
            EarthDistance(qLat, qLng)
        } else null

        val list = db.tx {
            val dist = geo?.alias("distance_m")
            val q = if (dist != null) Wallboxes.select(Wallboxes.columns + dist) else Wallboxes.selectAll()
            q.where {
                var cond: org.jetbrains.exposed.v1.core.Op<Boolean> = org.jetbrains.exposed.v1.core.Op.TRUE
                if (geo != null) cond = cond and (geo less radius)
                f.connector?.let { cond = cond and (Wallboxes.connectorType eq it.dbValue()) }
                f.minPowerKw?.let { cond = cond and (Wallboxes.powerKw greaterEq BigDecimal.valueOf(it)) }
                f.maxPricePerKwh?.let { cond = cond and (Wallboxes.pricePerKwh lessEq BigDecimal.valueOf(it)) }
                cond
            }
            if (geo != null) q.orderBy(geo to SortOrder.ASC)
            q.orderBy(Wallboxes.createdAt to SortOrder.DESC)
            q.limit(limit).offset(offset.toLong())
                .map { row -> row.toWallbox(if (dist != null) row[dist] else null) }
                .let { markPayoutState(it) }
        }
        return WallboxListResponse(list, limit, offset)
    }

    suspend fun get(id: Uuid): Wallbox = db.tx { markPayoutState(listOf(fetch(id))).single() }

    /** Markiert Wallboxes von Hosts ohne eingerichtetes Auszahlungskonto (`payouts_ready = false`). */
    private fun JdbcTransaction.markPayoutState(list: List<Wallbox>): List<Wallbox> {
        val ready = with(payouts) { readyOwners(list.map { it.ownerId }) }
        return list.map { if (it.ownerId in ready) it else it.copy(payoutsReady = false) }
    }

    fun JdbcTransaction.fetch(id: Uuid): Wallbox =
        Wallboxes.selectAll().where { Wallboxes.id eq id }.singleOrNull()?.toWallbox() ?: throw notFound("wallbox not found")

    private fun JdbcTransaction.requireOwnerOrAdmin(wb: Wallbox, userId: Uuid) {
        if (wb.ownerId != userId.toString()) {
            val role = Users.select(Users.role).where { Users.id eq userId }.singleOrNull()?.get(Users.role)
            if (role != "admin") throw forbidden("not the wallbox owner")
        }
    }

    // ---- Schreiben --------------------------------------------------------------------------

    suspend fun create(ownerId: Uuid, req: CreateWallboxRequest): Wallbox {
        validateFields(
            req.name, req.address, req.city, req.postalCode, req.lat, req.lng, req.powerKw, req.pricePerKwh,
            req.minDurationMin, req.maxDurationMin, req.availability,
        )
        val description = normalizedDescription(req.description)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = Uuid.random()
        try {
            db.tx {
                Wallboxes.insert {
                    it[Wallboxes.id] = id
                    it[Wallboxes.ownerId] = ownerId
                    it[name] = req.name.trim()
                    it[address] = req.address.trim()
                    it[city] = req.city.trim()
                    it[postalCode] = req.postalCode.trim()
                    it[lat] = req.lat
                    it[lng] = req.lng
                    it[powerKw] = BigDecimal.valueOf(req.powerKw)
                    it[connectorType] = req.connectorType.dbValue()
                    it[pricePerKwh] = BigDecimal.valueOf(req.pricePerKwh)
                    it[minDurationMin] = req.minDurationMin ?: 30
                    it[maxDurationMin] = req.maxDurationMin ?: 480
                    it[isOnline] = false
                    it[isMetered] = req.isMetered
                    it[ocppId] = req.ocppId?.takeIf { s -> s.isNotBlank() }
                    it[model] = req.model
                    it[manufacturer] = req.manufacturer
                    it[Wallboxes.description] = description
                    it[photos] = req.photos
                    it[availability] = req.availability
                    it[ratingAvg] = BigDecimal.ZERO
                    it[ratingCount] = 0
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        } catch (e: Exception) {
            if (e.sqlState() == PgState.UNIQUE_VIOLATION) throw conflict("ocpp_id already in use")
            throw e
        }
        return get(id)
    }

    suspend fun update(id: Uuid, userId: Uuid, req: UpdateWallboxRequest): Wallbox {
        req.name?.let { validateName(it) }
        req.address?.let { if (it.isBlank()) throw validationError("address: must not be blank") }
        req.city?.let { if (it.isBlank()) throw validationError("city: must not be blank") }
        req.postalCode?.let { if (it.isBlank()) throw validationError("postal_code: must not be blank") }
        val description = req.description?.let { normalizedDescription(it) }
        req.lat?.let { if (it !in -90.0..90.0) throw validationError("lat: must be between -90 and 90") }
        req.lng?.let { if (it !in -180.0..180.0) throw validationError("lng: must be between -180 and 180") }
        req.powerKw?.let { validatePower(it) }
        req.pricePerKwh?.let { validatePrice(it) }
        req.availability?.let(::validateAvailability)
        try {
            return db.tx {
                val wb = fetch(id)
                requireOwnerOrAdmin(wb, userId)
                val min = req.minDurationMin ?: wb.minDurationMin
                val max = req.maxDurationMin ?: wb.maxDurationMin
                if (min < 1 || max < min) throw validationError("min_duration_min/max_duration_min: invalid range")
                Wallboxes.update({ Wallboxes.id eq id }) {
                    req.name?.let { v -> it[name] = v.trim() }
                    req.address?.let { v -> it[address] = v.trim() }
                    req.city?.let { v -> it[city] = v.trim() }
                    req.postalCode?.let { v -> it[postalCode] = v.trim() }
                    req.lat?.let { v -> it[lat] = v }
                    req.lng?.let { v -> it[lng] = v }
                    req.powerKw?.let { v -> it[powerKw] = BigDecimal.valueOf(v) }
                    req.connectorType?.let { v -> it[connectorType] = v.dbValue() }
                    req.pricePerKwh?.let { v -> it[pricePerKwh] = BigDecimal.valueOf(v) }
                    req.minDurationMin?.let { v -> it[minDurationMin] = v }
                    req.maxDurationMin?.let { v -> it[maxDurationMin] = v }
                    req.isMetered?.let { v -> it[isMetered] = v }
                    req.ocppId?.let { v -> it[ocppId] = v.ifBlank { null } }
                    req.model?.let { v -> it[model] = v }
                    req.manufacturer?.let { v -> it[manufacturer] = v }
                    // Leere Beschreibung entfernt den Text; null (Feld fehlt) laesst ihn unveraendert.
                    if (req.description != null) it[Wallboxes.description] = description
                    req.photos?.let { v -> it[photos] = v }
                    req.availability?.let { v -> it[availability] = v }
                    it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                }
                fetch(id)
            }
        } catch (e: Exception) {
            if (e.sqlState() == PgState.UNIQUE_VIOLATION) throw conflict("ocpp_id already in use")
            throw e
        }
    }

    suspend fun delete(id: Uuid, userId: Uuid) {
        try {
            db.tx {
                requireOwnerOrAdmin(fetch(id), userId)
                Wallboxes.deleteWhere { Wallboxes.id eq id }
            }
        } catch (e: Exception) {
            // Buchungen/Bewertungen referenzieren die Wallbox (kein ON DELETE CASCADE)
            if (e.sqlState() == PgState.FOREIGN_KEY_VIOLATION) throw conflict("wallbox has bookings and cannot be deleted; set it offline instead")
            throw e
        }
    }

    suspend fun setOnline(id: Uuid, userId: Uuid, online: Boolean): Boolean = db.tx {
        requireOwnerOrAdmin(fetch(id), userId)
        Wallboxes.update({ Wallboxes.id eq id }) {
            it[isOnline] = online
            it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        online
    }

    // ---- Verfuegbarkeit / Slots -------------------------------------------------------------

    suspend fun availability(id: Uuid): WeeklyAvailability = db.tx { fetch(id).availability }

    suspend fun updateAvailability(id: Uuid, userId: Uuid, avail: WeeklyAvailability): WeeklyAvailability {
        validateAvailability(avail)
        return db.tx {
            requireOwnerOrAdmin(fetch(id), userId)
            Wallboxes.update({ Wallboxes.id eq id }) {
                it[availability] = avail
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
            avail
        }
    }

    /**
     * Freie 30-Minuten-Slots (`HH:MM`, UTC) eines Tages aus Wochenplan minus bestehende Buchungen
     * (Go: `WallboxService.GetAvailableSlots`). Abweichung: Slots in der Vergangenheit werden nicht angeboten;
     * durch Buchungen belegte Slots der Zukunft stehen separat in `locked` (fuer die ausgegraute Darstellung).
     */
    suspend fun slots(id: Uuid, date: LocalDate, now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): SlotsResponse {
        val (avail, booked) = db.tx {
            val wb = fetch(id)
            val dayStart = date.atStartOfDay().atOffset(ZoneOffset.UTC)
            val dayEnd = dayStart.plusDays(1)
            val rows = Bookings.select(Bookings.startTime, Bookings.endTime).where {
                (Bookings.wallboxId eq id) and (Bookings.status notInList listOf("cancelled", "failed")) and
                    (Bookings.startTime less dayEnd) and (Bookings.endTime greater dayStart)
            }.map { it[Bookings.startTime].toInstant() to it[Bookings.endTime].toInstant() }
            wb.availability to rows
        }
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
        val configured = avail.entries.firstOrNull { (k, _) -> k.isNotEmpty() && weekday.startsWith(k.lowercase()) }?.value.orEmpty()
        val out = mutableListOf<TimeSlot>()
        val locked = mutableListOf<TimeSlot>()
        for (cfg in configured) {
            val s = runCatching { LocalTime.parse(cfg.start) }.getOrNull() ?: continue
            val e = slotEndMinutes(cfg.end) ?: continue
            var cursor = date.atTime(s).toInstant(ZoneOffset.UTC)
            val endOfSlot = date.atStartOfDay().toInstant(ZoneOffset.UTC).plus(Duration.ofMinutes(e.toLong()))
            while (!cursor.plus(Duration.ofMinutes(30)).isAfter(endOfSlot)) {
                val next = cursor.plus(Duration.ofMinutes(30))
                val overlap = booked.any { (bs, be) -> cursor.isBefore(be) && next.isAfter(bs) }
                if (!cursor.isBefore(now.toInstant())) {
                    val slot = TimeSlot(
                        LocalTime.ofInstant(cursor, ZoneOffset.UTC).toString(),
                        LocalTime.ofInstant(next, ZoneOffset.UTC).toString(),
                    )
                    if (overlap) locked += slot else out += slot
                }
                cursor = next
            }
        }
        return SlotsResponse(date.toString(), out, locked)
    }

    // ---- Validierung ------------------------------------------------------------------------

    private fun validateName(name: String) {
        if (name.isBlank() || name.length > WallboxLimits.MAX_NAME_LENGTH) {
            throw validationError("name: must be 1-${WallboxLimits.MAX_NAME_LENGTH} characters")
        }
    }

    private fun validatePower(powerKw: Double) {
        if (powerKw <= 0 || powerKw > WallboxLimits.MAX_POWER_KW) {
            throw validationError("power_kw: must be greater than 0 and at most ${WallboxLimits.MAX_POWER_KW}")
        }
    }

    private fun validatePrice(pricePerKwh: Double) {
        if (pricePerKwh <= 0 || pricePerKwh > WallboxLimits.MAX_PRICE_PER_KWH) {
            throw validationError("price_per_kwh: must be greater than 0 and at most ${WallboxLimits.MAX_PRICE_PER_KWH}")
        }
    }

    /** Getrimmte Beschreibung; leer = keine. */
    private fun normalizedDescription(text: String?): String? {
        val trimmed = text?.trim()?.takeIf { it.isNotEmpty() }
        if ((trimmed?.length ?: 0) > WallboxLimits.MAX_DESCRIPTION_LENGTH) {
            throw validationError("description: must be at most ${WallboxLimits.MAX_DESCRIPTION_LENGTH} characters")
        }
        return trimmed
    }

    /** Ende eines Zeitfensters in Minuten seit Mitternacht; `24:00` (Tagesende) ist erlaubt. */
    private fun slotEndMinutes(text: String): Int? {
        if (text == END_OF_DAY) return MINUTES_PER_DAY
        return runCatching { LocalTime.parse(text) }.getOrNull()?.let { it.toSecondOfDay() / SECONDS_PER_MINUTE }
    }

    private fun validateAvailability(a: WeeklyAvailability) {
        for ((day, slots) in a) {
            if (day.isBlank()) throw validationError("availability: empty weekday key")
            for (s in slots) {
                val st = runCatching { LocalTime.parse(s.start) }.getOrNull()
                val en = slotEndMinutes(s.end)
                if (st == null || en == null) throw validationError("availability: times must be HH:MM")
                if (en <= st.toSecondOfDay() / SECONDS_PER_MINUTE) throw validationError("availability: end must be after start")
            }
        }
    }

    private fun validateFields(
        name: String, address: String, city: String, postalCode: String, lat: Double, lng: Double,
        powerKw: Double, pricePerKwh: Double, minDur: Int?, maxDur: Int?, availability: WeeklyAvailability,
    ) {
        validateName(name)
        if (address.isBlank()) throw validationError("address: is required")
        if (city.isBlank()) throw validationError("city: is required")
        if (postalCode.isBlank()) throw validationError("postal_code: is required")
        if (lat !in -90.0..90.0) throw validationError("lat: must be between -90 and 90")
        if (lng !in -180.0..180.0) throw validationError("lng: must be between -180 and 180")
        validatePower(powerKw)
        validatePrice(pricePerKwh)
        val min = minDur ?: 30
        val max = maxDur ?: 480
        if (min < 1 || max < min) throw validationError("min_duration_min/max_duration_min: invalid range")
        validateAvailability(availability)
    }
}

private const val END_OF_DAY = "24:00"
private const val MINUTES_PER_DAY = 24 * 60
private const val SECONDS_PER_MINUTE = 60
