package app.chargeshare.server.services

import app.chargeshare.server.db.Bookings
import app.chargeshare.server.db.Db
import app.chargeshare.server.db.PgState
import app.chargeshare.server.db.Users
import app.chargeshare.server.db.Wallboxes
import app.chargeshare.server.db.dbValue
import app.chargeshare.server.db.sqlState
import app.chargeshare.server.db.toBooking
import app.chargeshare.server.db.toUser
import app.chargeshare.server.db.toWallbox
import app.chargeshare.server.payments.PaymentEvent
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.server.util.AppException
import app.chargeshare.server.util.badRequest
import app.chargeshare.server.util.conflict
import app.chargeshare.server.util.forbidden
import app.chargeshare.server.util.internal
import app.chargeshare.server.util.notFound
import app.chargeshare.server.util.parseUuid
import app.chargeshare.server.util.validationError
import app.chargeshare.shared.model.Booking
import app.chargeshare.shared.model.BookingListResponse
import app.chargeshare.shared.model.BookingResponse
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.ErrorCodes
import app.chargeshare.shared.model.User
import io.ktor.http.HttpStatusCode
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/** Buchungen inkl. Zahlung (Go: `services/booking.go`, `handlers/bookings.go`). */
class BookingService(
    private val db: Db,
    private val payments: PaymentProvider,
    private val platformFeePercent: Int,
) {
    private val log = LoggerFactory.getLogger(BookingService::class.java)

    // ---- Anlegen ----------------------------------------------------------------------------

    suspend fun create(driverId: Uuid, req: CreateBookingRequest, now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): BookingResponse {
        val wallboxId = parseUuid(req.wallboxId, "wallbox_id")
        val start = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(req.startTime.epochSeconds, req.startTime.nanosecondsOfSecond.toLong()), ZoneOffset.UTC)
        val end = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(req.endTime.epochSeconds, req.endTime.nanosecondsOfSecond.toLong()), ZoneOffset.UTC)
        if (!end.isAfter(start)) throw badRequest("end_time must be after start_time")
        if (start.isBefore(now)) throw badRequest("start_time must be in the future")

        // 1) Buchung als `pending` anlegen – der Exclusion-Constraint serialisiert konkurrierende Anfragen
        val bookingId = Uuid.random()
        val (wallbox, hostAccount, customerId) = db.tx {
            // Buchungen pro Wallbox serialisieren (verhindert Deadlocks konkurrierender Exclusion-Constraint-Inserts);
            // der Constraint `no_overlap` bleibt als letzte Sicherung bestehen.
            exec("SELECT pg_advisory_xact_lock(hashtext('wallbox-booking:$wallboxId'))")
            val wb = Wallboxes.selectAll().where { Wallboxes.id eq wallboxId }.singleOrNull()?.toWallbox()
                ?: throw notFound("wallbox not found")
            if (!wb.isOnline) throw badRequest("wallbox is currently offline")
            val minutes = Duration.between(start, end).toMinutes()
            if (minutes < wb.minDurationMin) throw validationError("minimum booking duration is ${wb.minDurationMin} minutes")
            if (minutes > wb.maxDurationMin) throw validationError("maximum booking duration is ${wb.maxDurationMin} minutes")

            val overlap = !Bookings.selectAll().where {
                (Bookings.wallboxId eq wallboxId) and (Bookings.status notInList INACTIVE) and
                    (Bookings.startTime less end) and (Bookings.endTime greater start)
            }.empty()
            if (overlap) throw conflict(CONFLICT_MSG)

            val hours = Duration.between(start, end).toMillis() / 3_600_000.0
            val estKwh = BigDecimal.valueOf(wb.powerKw * hours).setScale(3, RoundingMode.HALF_UP)
            val total = estKwh.multiply(BigDecimal.valueOf(wb.pricePerKwh)).setScale(2, RoundingMode.HALF_UP)
            try {
                Bookings.insert {
                    it[Bookings.id] = bookingId
                    it[Bookings.wallboxId] = wallboxId
                    it[Bookings.driverId] = driverId
                    it[startTime] = start
                    it[endTime] = end
                    it[status] = BookingStatus.PENDING.dbValue()
                    it[estimatedKwh] = estKwh
                    it[pricePerKwh] = BigDecimal.valueOf(wb.pricePerKwh)
                    it[totalAmount] = total
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            } catch (e: Exception) {
                if (e.sqlState() == PgState.EXCLUSION_VIOLATION || e.sqlState() == PgState.DEADLOCK) throw conflict(CONFLICT_MSG)
                throw e
            }
            val account = Users.selectAll().where { Users.id eq Uuid.parse(wb.ownerId) }.singleOrNull()?.get(Users.stripeAccountId)
            val customer = Users.selectAll().where { Users.id eq driverId }.singleOrNull()?.get(Users.stripeCustomerId)
            Triple(wb to total, account, customer)
        }
        val (wb, total) = wallbox

        // 2) Zahlung anlegen (Stripe oder Fake); bei Fehler wird die Buchung auf `failed` gesetzt (Slot frei)
        val amountCents = maxOf(total.movePointRight(2).toLong(), 50L) // Stripe-Minimum
        val intent = try {
            payments.createPaymentIntent(amountCents, "eur", customerId, hostAccount, platformFeePercent)
        } catch (e: Exception) {
            log.error("payment intent creation failed", e)
            db.tx {
                Bookings.update({ Bookings.id eq bookingId }) {
                    it[status] = BookingStatus.FAILED.dbValue()
                    it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            throw internal("payment intent creation failed")
        }
        val confirmed = payments.autoConfirms && intent.paid
        val booking = db.tx {
            Bookings.update({ Bookings.id eq bookingId }) {
                it[stripePaymentIntentId] = intent.id
                if (confirmed) it[status] = BookingStatus.CONFIRMED.dbValue()
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
            load(bookingId, withWallbox = true)
        }
        return BookingResponse(booking, intent.clientSecret)
    }

    // ---- Lesen ------------------------------------------------------------------------------

    suspend fun list(driverId: Uuid, status: BookingStatus?, limit: Int, offset: Int): BookingListResponse = db.tx {
        val rows = Bookings.selectAll().where {
            var c: Op<Boolean> = Bookings.driverId eq driverId
            if (status != null) c = c and (Bookings.status eq status.dbValue())
            c
        }.orderBy(Bookings.createdAt to SortOrder.DESC).limit(limit).offset(offset.toLong()).toList()
        BookingListResponse(withRelations(rows, withDriver = false), limit, offset)
    }

    suspend fun get(bookingId: Uuid, userId: Uuid): Booking = db.tx {
        val row = Bookings.selectAll().where { Bookings.id eq bookingId }.singleOrNull() ?: throw notFound("booking not found")
        val wb = Wallboxes.selectAll().where { Wallboxes.id eq row[Bookings.wallboxId] }.single().toWallbox()
        val isDriver = row[Bookings.driverId] == userId
        val isOwner = wb.ownerId == userId.toString()
        if (!isDriver && !isOwner) {
            val role = Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.role)
            if (role != "admin") throw forbidden("access denied")
        }
        row.toBooking(wallbox = wb, driver = if (!isDriver) driverOf(row[Bookings.driverId]) else null)
    }

    // ---- Stornieren / Zahlung bestaetigen ---------------------------------------------------

    suspend fun cancel(bookingId: Uuid, userId: Uuid, reason: String?, now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)) {
        val (status, piId, start) = db.tx {
            val b = Bookings.selectAll().where { Bookings.id eq bookingId }.singleOrNull() ?: throw notFound("booking not found")
            if (b[Bookings.driverId] != userId) throw forbidden("only the booking owner can cancel")
            val st = b[Bookings.status]
            if (st == "cancelled" || st == "completed" || st == "failed") throw badRequest("booking cannot be cancelled in its current state")
            Bookings.update({ Bookings.id eq bookingId }) {
                it[Bookings.status] = BookingStatus.CANCELLED.dbValue()
                it[cancelReason] = reason
                it[updatedAt] = now
            }
            Triple(st, b[Bookings.stripePaymentIntentId], b[Bookings.startTime])
        }
        // Erstattung bei Stornierung > 30 Minuten vor Beginn (Go-Regel); nur wenn bereits bezahlt
        if (piId != null && status != "pending" && now.isBefore(start.minusMinutes(30))) {
            runCatching { payments.refund(piId) }.onFailure { log.warn("refund failed for booking $bookingId", it) }
        }
    }

    /** `pending` -> `confirmed`, nachdem der Client bezahlt hat (z. B. nach 3DS). Idempotent fuer bereits bestaetigte Buchungen. */
    suspend fun confirmPayment(bookingId: Uuid, userId: Uuid): Booking {
        val (row, _) = db.tx {
            val b = Bookings.selectAll().where { Bookings.id eq bookingId }.singleOrNull() ?: throw notFound("booking not found")
            if (b[Bookings.driverId] != userId) throw forbidden("not your booking")
            b to Unit
        }
        val status = row[Bookings.status]
        if (status == "confirmed" && payments.autoConfirms) return db.tx { load(bookingId, true) }
        if (status != "pending") throw badRequest("booking is not in pending state")
        // Anders als im Go-Backend (das blind bestaetigt) wird der Zahlungsstatus beim Provider geprueft.
        val pi = row[Bookings.stripePaymentIntentId]
        val paid = pi != null && runCatching { payments.isPaid(pi) }.getOrDefault(false)
        if (!paid) throw AppException(HttpStatusCode.PaymentRequired, ErrorCodes.PAYMENT_REQUIRED, "payment has not been completed")
        return db.tx {
            Bookings.update({ (Bookings.id eq bookingId) and (Bookings.status eq "pending") }) {
                it[Bookings.status] = BookingStatus.CONFIRMED.dbValue()
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
            load(bookingId, true)
        }
    }

    /** Stripe-Webhook-Ereignisse (Go: `HandleWebhook`). */
    suspend fun applyPaymentEvent(event: PaymentEvent) {
        db.tx {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            when (event) {
                is PaymentEvent.Succeeded -> Bookings.update({ (Bookings.stripePaymentIntentId eq event.paymentIntentId) and (Bookings.status eq "pending") }) {
                    it[status] = "confirmed"; it[updatedAt] = now
                }
                is PaymentEvent.Failed -> Bookings.update({ (Bookings.stripePaymentIntentId eq event.paymentIntentId) and (Bookings.status eq "pending") }) {
                    it[status] = "failed"; it[updatedAt] = now
                }
                is PaymentEvent.Refunded -> Bookings.update({
                    (Bookings.stripePaymentIntentId eq event.paymentIntentId) and (Bookings.status inList listOf("pending", "confirmed", "active"))
                }) { it[status] = "cancelled"; it[updatedAt] = now }
                PaymentEvent.Ignored -> Unit
            }
        }
    }

    // ---- Hilfsfunktionen --------------------------------------------------------------------

    private fun JdbcTransaction.load(id: Uuid, withWallbox: Boolean): Booking {
        val row = Bookings.selectAll().where { Bookings.id eq id }.singleOrNull() ?: throw notFound("booking not found")
        return withRelations(listOf(row), withDriver = false, withWallbox = withWallbox).single()
    }

    private fun JdbcTransaction.driverOf(id: Uuid): User? =
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()?.publicView()

    /** Betten Wallbox (und optional den Fahrer mit Name/Avatar) ein, damit Clients keine Folgeabfragen brauchen. */
    fun JdbcTransaction.withRelations(
        rows: List<ResultRow>,
        withDriver: Boolean,
        withWallbox: Boolean = true,
    ): List<Booking> {
        if (rows.isEmpty()) return emptyList()
        val boxes = if (withWallbox) {
            Wallboxes.selectAll().where { Wallboxes.id inList rows.map { it[Bookings.wallboxId] }.distinct() }
                .associate { it[Wallboxes.id] to it.toWallbox() }
        } else emptyMap()
        val drivers = if (withDriver) {
            Users.selectAll().where { Users.id inList rows.map { it[Bookings.driverId] }.distinct() }
                .associate { it[Users.id] to it.toUser().publicView() }
        } else emptyMap()
        return rows.map { it.toBooking(boxes[it[Bookings.wallboxId]], drivers[it[Bookings.driverId]]) }
    }

    companion object {
        val INACTIVE = listOf("cancelled", "failed")
        const val CONFLICT_MSG = "wallbox is not available for the requested time slot"

        /** Fahrer-Ansicht fuer Hosts: ohne Rolle/Stripe-IDs/Telefon. */
        fun User.publicView(): User = copy(phone = null, stripeCustomerId = null, stripeAccountId = null)
    }
}

