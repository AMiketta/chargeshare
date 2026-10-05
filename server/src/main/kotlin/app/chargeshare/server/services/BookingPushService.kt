package app.chargeshare.server.services

import app.chargeshare.server.db.Bookings
import app.chargeshare.server.db.Db
import app.chargeshare.server.db.Wallboxes
import app.chargeshare.server.util.badRequest
import app.chargeshare.server.util.forbidden
import app.chargeshare.server.util.notFound
import app.chargeshare.server.util.validationError
import app.chargeshare.shared.model.PushLimits
import app.chargeshare.shared.model.PushNotificationKind
import app.chargeshare.shared.model.ReportProblemRequest
import java.math.BigDecimal
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory

/**
 * Push-Benachrichtigungen entlang des Buchungslebenszyklus.
 * Fehler beim Versand werden geloggt und schlucken den Fachablauf nicht.
 */
class BookingPushService(
    private val db: Db,
    private val devices: PushDeviceService,
) {
    private val log = LoggerFactory.getLogger(BookingPushService::class.java)

    suspend fun onBookingConfirmed(bookingId: Uuid) {
        val ctx = loadContext(bookingId) ?: return
        notifyQuietly(ctx.hostId, PushNotificationKind.HostUnlockReminder(ctx.bookingId, ctx.wallboxName))
        notifyQuietly(ctx.driverId, PushNotificationKind.DriverBookingReminder(ctx.bookingId, ctx.wallboxName))
    }

    suspend fun onBookingCancelled(bookingId: Uuid) {
        val ctx = loadContext(bookingId) ?: return
        notifyQuietly(ctx.hostId, PushNotificationKind.HostBookingCancelled(ctx.bookingId, ctx.wallboxName))
    }

    suspend fun onChargingStarted(bookingId: Uuid) {
        val ctx = loadContext(bookingId) ?: return
        notifyQuietly(ctx.hostId, PushNotificationKind.ChargingStarted(ctx.bookingId, ctx.wallboxName, forHost = true))
        notifyQuietly(ctx.driverId, PushNotificationKind.ChargingStarted(ctx.bookingId, ctx.wallboxName, forHost = false))
    }

    suspend fun onChargingEnded(bookingId: Uuid, actualKwh: BigDecimal?) {
        val ctx = loadContext(bookingId) ?: return
        val kwh = actualKwh?.toDouble()
        notifyQuietly(ctx.hostId, PushNotificationKind.ChargingEnded(ctx.bookingId, ctx.wallboxName, forHost = true, actualKwh = kwh))
        notifyQuietly(ctx.driverId, PushNotificationKind.ChargingEnded(ctx.bookingId, ctx.wallboxName, forHost = false, actualKwh = kwh))
        notifyQuietly(ctx.hostId, PushNotificationKind.HostEnterKwhReminder(ctx.bookingId, ctx.wallboxName))
    }

    /**
     * Fahrer meldet dem Host ein Problem zur Buchung.
     * Erlaubt in den Zuständen `confirmed`, `active` und `completed`.
     */
    suspend fun reportProblem(bookingId: Uuid, driverId: Uuid, req: ReportProblemRequest) {
        val message = req.message.trim()
        if (message.isEmpty()) throw validationError("message: must not be empty")
        if (message.length > PushLimits.PROBLEM_MESSAGE_MAX) {
            throw validationError("message: must be at most ${PushLimits.PROBLEM_MESSAGE_MAX} characters")
        }
        val ctx = db.tx {
            val row = Bookings.selectAll().where { Bookings.id eq bookingId }.singleOrNull()
                ?: throw notFound("booking not found")
            if (row[Bookings.driverId] != driverId) throw forbidden("only the booking owner can report a problem")
            val status = row[Bookings.status]
            if (status !in PROBLEM_STATUSES) throw badRequest("booking cannot receive problem reports in its current state")
            val wb = Wallboxes.select(Wallboxes.ownerId, Wallboxes.name)
                .where { Wallboxes.id eq row[Bookings.wallboxId] }.single()
            BookingContext(
                bookingId = bookingId.toString(),
                driverId = row[Bookings.driverId],
                hostId = wb[Wallboxes.ownerId],
                wallboxName = wb[Wallboxes.name],
            )
        }
        notifyQuietly(
            ctx.hostId,
            PushNotificationKind.HostProblemReported(ctx.bookingId, ctx.wallboxName, message),
        )
    }

    private suspend fun notifyQuietly(userId: Uuid, kind: PushNotificationKind) {
        try {
            devices.notifyUser(userId, kind)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("push {} to {} failed: {}", kind.type, userId, e.javaClass.simpleName)
        }
    }

    private suspend fun loadContext(bookingId: Uuid): BookingContext? = db.tx {
        val row = Bookings.selectAll().where { Bookings.id eq bookingId }.singleOrNull() ?: return@tx null
        val wb = Wallboxes.select(Wallboxes.ownerId, Wallboxes.name)
            .where { Wallboxes.id eq row[Bookings.wallboxId] }.singleOrNull() ?: return@tx null
        BookingContext(
            bookingId = bookingId.toString(),
            driverId = row[Bookings.driverId],
            hostId = wb[Wallboxes.ownerId],
            wallboxName = wb[Wallboxes.name],
        )
    }

    private data class BookingContext(
        val bookingId: String,
        val driverId: Uuid,
        val hostId: Uuid,
        val wallboxName: String,
    )

    companion object {
        private val PROBLEM_STATUSES = setOf("confirmed", "active", "completed")
    }
}
