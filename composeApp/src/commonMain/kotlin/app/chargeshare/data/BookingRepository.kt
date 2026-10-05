package app.chargeshare.data

import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.model.Booking
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.CreateReviewRequest
import app.chargeshare.shared.model.ReportProblemRequest
import app.chargeshare.shared.model.Review
import kotlin.time.Instant

/**
 * Ergebnis von `POST /bookings`.
 *
 * @property clientSecret Stripe-`client_secret` für das Zahlungsfenster; im Dev-Modus mit Fake-Zahlung wertlos,
 * weil die Buchung dort schon `confirmed` ist
 */
data class BookingCreation(val booking: Booking, val clientSecret: String?)

/** Buchungen des angemeldeten Fahrers. */
interface BookingRepository {
    suspend fun create(wallboxId: String, start: Instant, end: Instant): Outcome<BookingCreation>

    /** Meldet dem Server, dass das Zahlungsfenster erfolgreich war; der Server prüft die Zahlung bei Stripe nach. */
    suspend fun confirmPayment(bookingId: String): Outcome<Booking>

    /**
     * „Jetzt bezahlen“ für eine offene Buchung: holt das `client_secret` für das Zahlungsfenster.
     * Ist die Buchung inzwischen bezahlt, ist ihr Status nicht mehr `pending` und [BookingCreation.clientSecret] leer.
     */
    suspend fun startPayment(bookingId: String): Outcome<BookingCreation>

    /** Bewertet die abgeschlossene Buchung [bookingId] mit [rating] (1..5) und optionalem [comment]. Eine Bewertung pro Buchung. */
    suspend fun review(bookingId: String, rating: Int, comment: String?): Outcome<Review>

    /** Quittung (PDF) einer abgeschlossenen Buchung. */
    suspend fun receipt(bookingId: String): Outcome<ByteArray>

    /** Alle Buchungen des Fahrers (neueste zuerst, wie vom Server geliefert). */
    suspend fun list(): Outcome<List<Booking>>
    suspend fun cancel(bookingId: String): Outcome<Booking>

    /** Meldet dem Host ein Problem zur Buchung (löst Push an den Host aus). */
    suspend fun reportProblem(bookingId: String, message: String): Outcome<Unit>
}

class ApiBookingRepository(private val api: ChargeShareApi) : BookingRepository {

    override suspend fun create(wallboxId: String, start: Instant, end: Instant): Outcome<BookingCreation> =
        when (val result = runCatchingOutcome { api.createBooking(CreateBookingRequest(wallboxId, start, end)) }) {
            // Eine Antwort ohne Buchung widerspricht dem API-Vertrag.
            is Outcome.Success -> result.value.booking
                ?.let { Outcome.Success(BookingCreation(it, result.value.clientSecret)) }
                ?: Outcome.Failure(AppError.Unknown)
            is Outcome.Failure -> result
        }

    override suspend fun confirmPayment(bookingId: String): Outcome<Booking> =
        runCatchingOutcome { api.confirmPayment(bookingId) }

    override suspend fun startPayment(bookingId: String): Outcome<BookingCreation> =
        when (val result = runCatchingOutcome { api.payBooking(bookingId) }) {
            is Outcome.Success -> result.value.booking
                ?.let { Outcome.Success(BookingCreation(it, result.value.clientSecret)) }
                ?: Outcome.Failure(AppError.Unknown)
            is Outcome.Failure -> result
        }

    override suspend fun review(bookingId: String, rating: Int, comment: String?): Outcome<Review> =
        runCatchingOutcome { api.createReview(CreateReviewRequest(bookingId, rating, comment)) }

    override suspend fun receipt(bookingId: String): Outcome<ByteArray> =
        runCatchingOutcome { api.bookingReceipt(bookingId) }

    override suspend fun list(): Outcome<List<Booking>> =
        runCatchingOutcome { api.bookings(limit = LIST_LIMIT).bookings }

    override suspend fun cancel(bookingId: String): Outcome<Booking> =
        runCatchingOutcome { api.cancelBooking(bookingId) }

    override suspend fun reportProblem(bookingId: String, message: String): Outcome<Unit> =
        runCatchingOutcome { api.reportBookingProblem(bookingId, ReportProblemRequest(message)) }

    private companion object {
        const val LIST_LIMIT = 100
    }
}
