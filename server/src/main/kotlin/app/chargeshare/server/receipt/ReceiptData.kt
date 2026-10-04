package app.chargeshare.server.receipt

import java.math.BigDecimal
import java.time.ZonedDateTime

/** Alles, was auf der Quittung einer bezahlten Buchung steht. Beträge in Euro. */
data class ReceiptData(
    val bookingId: String,
    val issuedAt: ZonedDateTime,
    val driverName: String,
    val driverEmail: String,
    val wallboxName: String,
    val wallboxAddress: String,
    /** Anzeigename des Hosts; `null`, wenn nicht ermittelbar. */
    val hostName: String?,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    /** Gemessene kWh, sonst die gebuchte Schätzung ([kwhIsEstimate]). */
    val kwh: BigDecimal?,
    val kwhIsEstimate: Boolean,
    val pricePerKwh: BigDecimal,
    val totalAmount: BigDecimal,
    val platformFeePercent: Int,
    val platformFeeAmount: BigDecimal,
    val paymentReference: String?,
)
