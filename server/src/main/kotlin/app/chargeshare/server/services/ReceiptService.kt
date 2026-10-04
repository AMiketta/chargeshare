package app.chargeshare.server.services

import app.chargeshare.server.db.Bookings
import app.chargeshare.server.db.Db
import app.chargeshare.server.db.Users
import app.chargeshare.server.db.Wallboxes
import app.chargeshare.server.receipt.ReceiptData
import app.chargeshare.server.receipt.ReceiptPdf
import app.chargeshare.server.util.conflict
import app.chargeshare.server.util.notFound
import app.chargeshare.shared.model.BookingStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Quittung (PDF) für abgeschlossene, bezahlte Buchungen; nur der Fahrer der Buchung darf sie abrufen. */
class ReceiptService(private val db: Db, private val platformFeePercent: Int) {

    /** Zeitzone der Anzeige: Wallboxen stehen in Deutschland. */
    private val zone = ZoneId.of("Europe/Berlin")

    /**
     * @throws app.chargeshare.server.util.AppException `404`, wenn es die Buchung nicht gibt oder sie einem anderen Fahrer gehört;
     * `409`, solange die Buchung nicht abgeschlossen ist
     */
    suspend fun render(bookingId: Uuid, userId: Uuid, now: OffsetDateTime = OffsetDateTime.now(zone)): ByteArray =
        ReceiptPdf.render(load(bookingId, userId, now))

    private suspend fun load(bookingId: Uuid, userId: Uuid, now: OffsetDateTime): ReceiptData = db.tx {
        val booking = Bookings.selectAll().where { Bookings.id eq bookingId }.singleOrNull()
        if (booking == null || booking[Bookings.driverId] != userId) throw notFound("booking not found")
        if (booking[Bookings.status] != BookingStatus.COMPLETED.name.lowercase()) throw conflict("receipt is only available for completed bookings")
        val wallbox = Wallboxes.selectAll().where { Wallboxes.id eq booking[Bookings.wallboxId] }.single()
        val host = Users.selectAll().where { Users.id eq wallbox[Wallboxes.ownerId] }.singleOrNull()
        val driver = Users.selectAll().where { Users.id eq userId }.single()
        val total = booking[Bookings.totalAmount] ?: BigDecimal.ZERO
        val measured = booking[Bookings.actualKwh]
        ReceiptData(
            bookingId = bookingId.toString(),
            issuedAt = now.atZoneSameInstant(zone),
            driverName = driver[Users.name],
            driverEmail = driver[Users.email],
            wallboxName = wallbox[Wallboxes.name],
            wallboxAddress = "${wallbox[Wallboxes.address]}, ${wallbox[Wallboxes.postalCode]} ${wallbox[Wallboxes.city]}",
            hostName = host?.get(Users.name),
            start = booking[Bookings.startTime].atZoneSameInstant(zone),
            end = booking[Bookings.endTime].atZoneSameInstant(zone),
            kwh = measured ?: booking[Bookings.estimatedKwh],
            kwhIsEstimate = measured == null,
            pricePerKwh = booking[Bookings.pricePerKwh],
            totalAmount = total,
            platformFeePercent = platformFeePercent,
            platformFeeAmount = feeOf(total),
            paymentReference = booking[Bookings.stripePaymentIntentId],
        )
    }

    /** Gleiche Rechnung wie beim PaymentIntent (`amount * Prozent / 100` in Cent, abgerundet). */
    private fun feeOf(total: BigDecimal): BigDecimal {
        val cents = total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
        return BigDecimal(cents * platformFeePercent / 100).movePointLeft(2)
    }
}
