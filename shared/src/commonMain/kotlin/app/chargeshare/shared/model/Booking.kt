package app.chargeshare.shared.model

import app.chargeshare.shared.util.InstantAsStringSerializer
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BookingStatus {
    @SerialName("pending") PENDING,
    @SerialName("confirmed") CONFIRMED,
    @SerialName("active") ACTIVE,
    @SerialName("completed") COMPLETED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("failed") FAILED,
}

/** Bekannte Werte von [Booking.cancelReason], die der Server selbst setzt. */
object CancelReasons {
    /** Die Buchung wurde nicht rechtzeitig bezahlt und automatisch storniert. */
    const val PAYMENT_EXPIRED = "payment_expired"
}

/** Go: `models.Booking`. */
@Serializable
data class Booking(
    val id: String,
    @SerialName("wallbox_id") val wallboxId: String,
    @SerialName("driver_id") val driverId: String,
    @SerialName("start_time") @Serializable(InstantAsStringSerializer::class) val startTime: Instant,
    @SerialName("end_time") @Serializable(InstantAsStringSerializer::class) val endTime: Instant,
    val status: BookingStatus,
    @SerialName("estimated_kwh") val estimatedKwh: Double? = null,
    @SerialName("actual_kwh") val actualKwh: Double? = null,
    @SerialName("price_per_kwh") val pricePerKwh: Double,
    @SerialName("total_amount") val totalAmount: Double? = null,
    @SerialName("stripe_payment_intent_id") val stripePaymentIntentId: String? = null,
    @SerialName("stripe_charge_id") val stripeChargeId: String? = null,
    @SerialName("ocpp_transaction_id") val ocppTransactionId: Int? = null,
    @SerialName("cancel_reason") val cancelReason: String? = null,
    /** `true`, wenn diese Buchung bereits bewertet wurde (eine Bewertung pro Buchung). */
    val reviewed: Boolean = false,
    @SerialName("created_at") @Serializable(InstantAsStringSerializer::class) val createdAt: Instant,
    @SerialName("updated_at") @Serializable(InstantAsStringSerializer::class) val updatedAt: Instant,
    // Optional eager-geladene Relationen
    val wallbox: Wallbox? = null,
    val driver: User? = null,
)

/** Go: `models.CreateBookingRequest` — Zeiten als RFC3339. */
@Serializable
data class CreateBookingRequest(
    @SerialName("wallbox_id") val wallboxId: String,
    @SerialName("start_time") @Serializable(InstantAsStringSerializer::class) val startTime: Instant,
    @SerialName("end_time") @Serializable(InstantAsStringSerializer::class) val endTime: Instant,
)

@Serializable
data class CancelBookingRequest(val reason: String? = null)

/** Antwort von `POST /bookings`: Buchung plus Stripe-`client_secret` fuer die Zahlungsbestaetigung. */
@Serializable
data class BookingResponse(
    val booking: Booking? = null,
    @SerialName("client_secret") val clientSecret: String? = null,
)

/** Antwort von `GET /bookings` und `GET /host/bookings`. */
@Serializable
data class BookingListResponse(
    val bookings: List<Booking> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
)

/** Go: `models.ChargingSession` (OCPP-Telemetrie). */
@Serializable
data class ChargingSession(
    val id: String,
    @SerialName("booking_id") val bookingId: String? = null,
    @SerialName("ocpp_id") val ocppId: String,
    @SerialName("transaction_id") val transactionId: Int? = null,
    @SerialName("meter_start") val meterStart: Int? = null,
    @SerialName("meter_stop") val meterStop: Int? = null,
    @SerialName("started_at") @Serializable(InstantAsStringSerializer::class) val startedAt: Instant? = null,
    @SerialName("stopped_at") @Serializable(InstantAsStringSerializer::class) val stoppedAt: Instant? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
    @SerialName("created_at") @Serializable(InstantAsStringSerializer::class) val createdAt: Instant,
    val booking: Booking? = null,
    val driver: User? = null,
)

/** Antwort von `GET /host/sessions`. */
@Serializable
data class SessionListResponse(
    val sessions: List<ChargingSession> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
)

/**
 * Einnahmen eines Hosts (`GET /host/earnings?from=&to=&tz=`; Go: `models.HostEarnings`, hier um Dashboard-Werte erweitert).
 * Gezählt werden nur abgeschlossene (`completed`) Buchungen auf den eigenen Wallboxes, zugeordnet nach dem **Beginn der Buchung**
 * (Ladetag) in der Zeitzone `tz`. Alle Felder außer [avgRating] beziehen sich auf die per `from`/`to` gefilterten Buchungen.
 *
 * @property total Summe der Beträge in Euro
 * @property month Einnahmen im laufenden Kalendermonat
 * @property sessions Anzahl der Buchungen
 * @property kwh geladene Energie in kWh (Summe `actual_kwh`)
 * @property monthSessions Buchungen im laufenden Monat
 * @property monthKwh geladene kWh im laufenden Monat
 * @property previousMonth Einnahmen im Vormonat
 * @property monthChangePct Veränderung von [month] gegenüber [previousMonth] in Prozent (eine Nachkommastelle);
 * `null`, wenn der Vormonat 0 € hatte und sich daher kein Prozentwert bilden lässt
 * @property week Einnahmen je Tag der laufenden Woche (Montag bis Sonntag, immer 7 Einträge; spätere Tage 0)
 * @property avgRating Durchschnitt aller Bewertungen auf den Wallboxes des Hosts, 0 ohne Bewertungen
 */
@Serializable
data class HostEarnings(
    val total: Double = 0.0,
    val month: Double = 0.0,
    val sessions: Int = 0,
    @SerialName("avg_rating") val avgRating: Double = 0.0,
    val kwh: Double = 0.0,
    @SerialName("month_sessions") val monthSessions: Int = 0,
    @SerialName("month_kwh") val monthKwh: Double = 0.0,
    @SerialName("previous_month") val previousMonth: Double = 0.0,
    @SerialName("month_change_pct") val monthChangePct: Double? = null,
    val week: List<DailyEarnings> = emptyList(),
)

/** Einnahmen eines Kalendertags; [date] ist `YYYY-MM-DD` in der angefragten Zeitzone. */
@Serializable
data class DailyEarnings(
    val date: String,
    val amount: Double = 0.0,
)

/** `POST /payments/onboard` -> `{"url": "..."}` (Stripe-Connect-Onboarding-Link). */
@Serializable
data class OnboardingLink(val url: String)

@Serializable
data class MessageResponse(val message: String)
