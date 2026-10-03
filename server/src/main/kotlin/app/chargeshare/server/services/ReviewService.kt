package app.chargeshare.server.services

import app.chargeshare.server.db.Bookings
import app.chargeshare.server.db.Db
import app.chargeshare.server.db.PgState
import app.chargeshare.server.db.Reviews
import app.chargeshare.server.db.Users
import app.chargeshare.server.db.Wallboxes
import app.chargeshare.server.db.sqlState
import app.chargeshare.server.db.toReview
import app.chargeshare.server.util.badRequest
import app.chargeshare.server.util.conflict
import app.chargeshare.server.util.notFound
import app.chargeshare.server.util.parseUuid
import app.chargeshare.server.util.validationError
import app.chargeshare.shared.model.CreateReviewRequest
import app.chargeshare.shared.model.Review
import app.chargeshare.shared.model.ReviewLimits
import app.chargeshare.shared.model.ReviewListResponse
import app.chargeshare.shared.model.User
import app.chargeshare.shared.model.UserRole
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.avg
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ReviewService(private val db: Db) {

    suspend fun listForWallbox(wallboxId: Uuid, limit: Int, offset: Int): ReviewListResponse = db.tx {
        val total = Reviews.selectAll().where { Reviews.wallboxId eq wallboxId }.count().toInt()
        val reviews = (Reviews innerJoin Users).selectAll()
            .where { Reviews.wallboxId eq wallboxId }
            .orderBy(Reviews.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { row ->
                // Go liefert nur Name + Avatar des Autors
                row.toReview(
                    User(
                        id = "", email = "", name = row[Users.name], avatarUrl = row[Users.avatarUrl],
                        createdAt = row[Users.createdAt].toInstant().toKotlin(), updatedAt = row[Users.updatedAt].toInstant().toKotlin(),
                    ),
                )
            }
        ReviewListResponse(reviews, total, limit, offset)
    }

    /** Nur der Fahrer einer abgeschlossenen Buchung darf (einmal) bewerten. */
    suspend fun create(userId: Uuid, req: CreateReviewRequest): Review {
        val bookingId = parseUuid(req.bookingId, "booking_id")
        if (req.rating !in ReviewLimits.MIN_RATING..ReviewLimits.MAX_RATING) {
            throw validationError("rating: must be between ${ReviewLimits.MIN_RATING} and ${ReviewLimits.MAX_RATING}")
        }
        val comment = req.comment?.trim()?.takeIf { it.isNotEmpty() }
        if ((comment?.length ?: 0) > ReviewLimits.MAX_COMMENT_LENGTH) {
            throw validationError("comment: must be at most ${ReviewLimits.MAX_COMMENT_LENGTH} characters")
        }
        try {
            return db.tx {
                val b = Bookings.selectAll().where { (Bookings.id eq bookingId) and (Bookings.driverId eq userId) }.singleOrNull()
                    ?: throw notFound("booking not found or not yours")
                if (b[Bookings.status] != "completed") throw badRequest("can only review completed bookings")
                val wbId = b[Bookings.wallboxId]
                val id = Uuid.random()
                val now = OffsetDateTime.now(ZoneOffset.UTC)
                Reviews.insert {
                    it[Reviews.id] = id
                    it[Reviews.bookingId] = bookingId
                    it[wallboxId] = wbId
                    it[authorId] = userId
                    it[rating] = req.rating.toShort()
                    it[Reviews.comment] = comment
                    it[createdAt] = now
                }
                // Aggregat exakt aus den Bewertungen neu berechnen (statt Go-Inkrement)
                val stats = Reviews.select(Reviews.rating.avg(), Reviews.id.count()).where { Reviews.wallboxId eq wbId }.single()
                Wallboxes.update({ Wallboxes.id eq wbId }) {
                    it[ratingCount] = stats[Reviews.id.count()].toInt()
                    it[ratingAvg] = (stats[Reviews.rating.avg()] ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                    it[updatedAt] = now
                }
                Reviews.selectAll().where { Reviews.id eq id }.single().toReview()
            }
        } catch (e: Exception) {
            if (e.sqlState() == PgState.UNIQUE_VIOLATION) throw conflict("booking already reviewed")
            throw e
        }
    }

}

private fun java.time.Instant.toKotlin() = kotlin.time.Instant.fromEpochSeconds(epochSecond, nano)
