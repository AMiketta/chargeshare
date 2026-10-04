package app.chargeshare.server.services

import app.chargeshare.server.db.Bookings
import app.chargeshare.server.db.ChargingSessions
import app.chargeshare.server.db.Db
import app.chargeshare.server.db.Reviews
import app.chargeshare.server.db.Users
import app.chargeshare.server.db.Wallboxes
import app.chargeshare.server.db.toChargingSession
import app.chargeshare.server.db.toUser
import app.chargeshare.server.util.badRequest
import app.chargeshare.server.util.forbidden
import app.chargeshare.server.util.notFound
import app.chargeshare.server.util.toInstant2
import app.chargeshare.server.util.unauthorized
import app.chargeshare.shared.model.BookingListResponse
import app.chargeshare.shared.model.HostEarnings
import app.chargeshare.shared.model.HostProfile
import app.chargeshare.shared.model.SuperhostCriteria
import app.chargeshare.shared.model.SessionListResponse
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Host-Dashboard (Go: `handlers/host.go`). Zugriff: Rolle host/both/admin (aus der DB, nicht aus dem Token). */
class HostService(private val db: Db, private val bookings: BookingService) {

    private fun org.jetbrains.exposed.v1.jdbc.JdbcTransaction.requireHost(userId: Uuid) {
        val role = Users.select(Users.role).where { Users.id eq userId }.singleOrNull()?.get(Users.role)
            ?: throw unauthorized("user no longer exists")
        if (role !in setOf("host", "both", "admin")) throw forbidden("insufficient role")
    }

    suspend fun bookings(hostId: Uuid, limit: Int, offset: Int): BookingListResponse = db.tx {
        requireHost(hostId)
        val rows = (Bookings innerJoin Wallboxes).select(Bookings.columns)
            .where { Wallboxes.ownerId eq hostId }
            .orderBy(Bookings.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong()).toList()
        with(bookings) { BookingListResponse(withRelations(rows, withDriver = true), limit, offset) }
    }

    /** @param from/to `YYYY-MM-DD` (inklusive, nach `created_at` der abgeschlossenen Buchungen) */
    suspend fun earnings(hostId: Uuid, from: String?, to: String?): HostEarnings {
        val fromAt = from?.takeIf { it.isNotEmpty() }?.let { parse(it).atStartOfDay().atOffset(ZoneOffset.UTC) }
        val toAt = to?.takeIf { it.isNotEmpty() }?.let { parse(it).plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) }
        return db.tx {
            requireHost(hostId)
            val rows = (Bookings innerJoin Wallboxes).select(Bookings.totalAmount, Bookings.createdAt)
                .where {
                    var c: org.jetbrains.exposed.v1.core.Op<Boolean> = (Wallboxes.ownerId eq hostId) and (Bookings.status eq "completed")
                    if (fromAt != null) c = c and (Bookings.createdAt greaterEq fromAt)
                    if (toAt != null) c = c and (Bookings.createdAt less toAt)
                    c
                }.toList()
            val monthStart = OffsetDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC)
            val total = rows.sumOf { it[Bookings.totalAmount]?.toDouble() ?: 0.0 }
            val month = rows.filter { !it[Bookings.createdAt].isBefore(monthStart) }.sumOf { it[Bookings.totalAmount]?.toDouble() ?: 0.0 }
            val ratings = Reviews.select(Reviews.rating)
                .where { Reviews.wallboxId inSubQuery Wallboxes.select(Wallboxes.id).where { Wallboxes.ownerId eq hostId } }
                .map { it[Reviews.rating].toInt() }
            HostEarnings(
                total = round2(total), month = round2(month), sessions = rows.size,
                avgRating = if (ratings.isEmpty()) 0.0 else round2(ratings.average()),
            )
        }
    }

    /**
     * Öffentliches Profil des Hosts der Wallbox [wallboxId] (ohne Login abrufbar, daher nur gekürzter Name und Kennzahlen).
     *
     * @throws app.chargeshare.server.util.AppException 404, wenn die Wallbox nicht existiert
     */
    suspend fun publicProfile(wallboxId: Uuid): HostProfile = db.tx {
        val ownerId = Wallboxes.select(Wallboxes.ownerId).where { Wallboxes.id eq wallboxId }.singleOrNull()?.get(Wallboxes.ownerId)
            ?: throw notFound("wallbox not found")
        val owner = Users.select(Users.name, Users.createdAt).where { Users.id eq ownerId }.single()
        val ownBoxes = Wallboxes.select(Wallboxes.id).where { Wallboxes.ownerId eq ownerId }
        val sessions = Bookings.selectAll()
            .where { (Bookings.wallboxId inSubQuery ownBoxes) and (Bookings.status eq "completed") }
            .count().toInt()
        val ratings = Reviews.select(Reviews.rating).where { Reviews.wallboxId inSubQuery ownBoxes }.map { it[Reviews.rating].toInt() }
        val ratingAvg = ratings.takeIf { it.isNotEmpty() }?.let { Math.round(it.average() * RATING_DECIMAL_FACTOR) / RATING_DECIMAL_FACTOR }
        HostProfile(
            name = abbreviateName(owner[Users.name]),
            memberSince = owner[Users.createdAt].toInstant2(),
            sessions = sessions,
            ratingAvg = ratingAvg,
            ratingCount = ratings.size,
            isSuperhost = sessions >= SuperhostCriteria.MIN_SESSIONS &&
                ratings.size >= SuperhostCriteria.MIN_RATING_COUNT &&
                (ratingAvg ?: 0.0) >= SuperhostCriteria.MIN_RATING_AVG,
        )
    }

    suspend fun sessions(hostId: Uuid, limit: Int, offset: Int): SessionListResponse = db.tx {
        requireHost(hostId)
        val rows = (ChargingSessions innerJoin Bookings innerJoin Wallboxes)
            .join(Users, JoinType.INNER, Bookings.driverId, Users.id)
            .selectAll()
            .where { Wallboxes.ownerId eq hostId }
            .orderBy(ChargingSessions.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { row ->
                val u = row.toUser()
                row.toChargingSession().copy(driver = u.copy(email = u.email, role = app.chargeshare.shared.model.UserRole.DRIVER, phone = null, stripeCustomerId = null, stripeAccountId = null))
            }
        SessionListResponse(rows, limit, offset)
    }

    private fun parse(s: String): LocalDate = try {
        LocalDate.parse(s)
    } catch (e: DateTimeParseException) {
        throw badRequest("invalid date, use YYYY-MM-DD")
    }

    private companion object {
        const val RATING_DECIMAL_FACTOR = 10.0
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0
}

/**
 * Kürzt einen Namen auf „Vorname N.“ (Datensparsamkeit: die Detailseite ist ohne Login lesbar).
 * Ein einzelnes Wort bleibt unverändert; Wörter ohne Buchstaben am Anfang (z. B. „(Beispiel)“) zählen nicht als Nachname.
 */
internal fun abbreviateName(fullName: String): String {
    val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val first = parts.firstOrNull() ?: return ""
    val lastInitial = parts.drop(1).lastOrNull { it.first().isLetter() }?.first()?.uppercaseChar()
    return if (lastInitial == null) first else "$first $lastInitial."
}
