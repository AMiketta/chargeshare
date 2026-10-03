package app.chargeshare.data

import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.model.Booking
import app.chargeshare.shared.model.CreateBookingRequest
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

    /** Alle Buchungen des Fahrers (neueste zuerst, wie vom Server geliefert). */
    suspend fun list(): Outcome<List<Booking>>
    suspend fun cancel(bookingId: String): Outcome<Booking>
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

    override suspend fun list(): Outcome<List<Booking>> =
        runCatchingOutcome { api.bookings(limit = LIST_LIMIT).bookings }

    override suspend fun cancel(bookingId: String): Outcome<Booking> =
        runCatchingOutcome { api.cancelBooking(bookingId) }

    private companion object {
        const val LIST_LIMIT = 100
    }
}
