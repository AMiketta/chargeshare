package app.chargeshare.data

import app.chargeshare.shared.api.ApiException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Ergebnis einer Operation, die fehlschlagen kann.
 *
 * Erwartbare Fehler (Netz weg, falsches Passwort, Slot vergeben) sind Werte und keine Exceptions im Programmfluss.
 */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

/** Fachliche Fehlerarten, die die UI in verständliche deutsche Meldungen übersetzt. */
sealed interface AppError {
    /** Server nicht erreichbar oder Verbindung abgebrochen. */
    data object Network : AppError

    /** Nicht angemeldet bzw. Zugangsdaten falsch (HTTP 401). */
    data object Unauthorized : AppError

    /** Keine Berechtigung für diese Aktion (HTTP 403). */
    data object Forbidden : AppError

    /** Ressource gibt es nicht (mehr) (HTTP 404). */
    data object NotFound : AppError

    /** Konflikt, z. B. der Zeitslot wurde zwischenzeitlich vergeben (HTTP 409). */
    data object Conflict : AppError

    /** Eingabe vom Server abgelehnt (HTTP 400/422). [detail] ist der technische, englische Servertext. */
    data class Validation(val detail: String?) : AppError

    /** Zahlung nicht abgeschlossen (HTTP 402). */
    data object PaymentRequired : AppError

    /** Serverfehler (HTTP 5xx). */
    data object Server : AppError

    /** Alles andere, z. B. unerwartetes Antwortformat. */
    data object Unknown : AppError
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.fold(onSuccess: (T) -> R, onFailure: (AppError) -> R): R = when (this) {
    is Outcome.Success -> onSuccess(value)
    is Outcome.Failure -> onFailure(error)
}

/** Wert bei Erfolg, sonst `null`. */
fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

/**
 * Führt [block] aus und übersetzt Exceptions in [Outcome.Failure].
 *
 * [CancellationException] wird bewusst **weitergeworfen**, damit Structured Concurrency funktioniert.
 */
suspend fun <T> runCatchingOutcome(block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        Outcome.Failure(e.toAppError())
    } catch (e: SerializationException) {
        Outcome.Failure(AppError.Unknown)
    } catch (e: Exception) {
        // Alle übrigen Ausnahmen kommen aus der Netzwerkschicht (je Plattform unterschiedliche Typen).
        Outcome.Failure(AppError.Network)
    }

internal fun ApiException.toAppError(): AppError = when (status) {
    HTTP_UNAUTHORIZED -> AppError.Unauthorized
    HTTP_PAYMENT_REQUIRED -> AppError.PaymentRequired
    HTTP_FORBIDDEN -> AppError.Forbidden
    HTTP_NOT_FOUND -> AppError.NotFound
    HTTP_CONFLICT -> AppError.Conflict
    HTTP_BAD_REQUEST, HTTP_UNPROCESSABLE -> AppError.Validation(message)
    in HTTP_SERVER_ERROR_RANGE -> AppError.Server
    else -> AppError.Unknown
}

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_PAYMENT_REQUIRED = 402
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409
private const val HTTP_UNPROCESSABLE = 422
private val HTTP_SERVER_ERROR_RANGE = 500..599
