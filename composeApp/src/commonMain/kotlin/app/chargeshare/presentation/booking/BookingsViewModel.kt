package app.chargeshare.presentation.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.BookingRepository
import app.chargeshare.data.BookingCreation
import app.chargeshare.data.Outcome
import app.chargeshare.data.PaymentRepository
import app.chargeshare.payment.PaymentMethodChoice
import app.chargeshare.payment.PaymentRequest
import app.chargeshare.payment.PaymentResult
import app.chargeshare.shared.model.Booking
import app.chargeshare.shared.model.BookingStatus
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BookingFilter { Upcoming, Past }

data class BookingsUiState(
    val bookings: List<Booking> = emptyList(),
    val filter: BookingFilter = BookingFilter.Upcoming,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    /** Buchung, für die gerade die Storno-Rückfrage angezeigt wird. */
    val cancelCandidate: Booking? = null,
    val cancellingId: String? = null,
    val cancelError: AppError? = null,
    /** Geöffneter Bewertungsdialog, sonst `null`. */
    val reviewDraft: ReviewDraft? = null,
    /** Stand von „Jetzt bezahlen“. */
    val payNow: PayNowState = PayNowState.Idle,
    /** Auf diesem Gerät gibt es ein Zahlungsfenster (Google Pay/Apple Pay); sonst wird „Jetzt bezahlen“ nicht angeboten. */
    val canPayOnline: Boolean = false,
    /** Zeitpunkt, an dem die Liste aufgeteilt wurde (bestimmt „kommend“ vs. „vergangen“). */
    val now: Instant,
) {
    val visibleBookings: List<Booking>
        get() = when (filter) {
            BookingFilter.Upcoming -> bookings.filter { it.isUpcoming(now) }.sortedBy { it.startTime }
            BookingFilter.Past -> bookings.filterNot { it.isUpcoming(now) }.sortedByDescending { it.startTime }
        }
}

/** Kommend: noch nicht beendet und weder storniert noch fehlgeschlagen noch abgeschlossen. */
fun Booking.isUpcoming(now: Instant): Boolean =
    endTime > now && status in UPCOMING_STATUSES

/** Offene Buchungen lassen sich nachträglich bezahlen, solange sie nicht beendet sind. */
fun Booking.isPayable(now: Instant): Boolean = status == BookingStatus.PENDING && endTime > now

/** Nur offene oder bestätigte Buchungen dürfen vom Fahrer storniert werden. */
fun Booking.isCancellable(): Boolean = status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED

private val UPCOMING_STATUSES = setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE)

/**
 * Buchungsliste mit Stornieren und „Jetzt bezahlen“ für offene Buchungen.
 *
 * Das Zahlungsfenster gehört der Plattform: Das ViewModel meldet es über [PayNowState.PaymentRequired] und erhält
 * das Ergebnis über [onPaymentResult] (wie im [BookingFlowViewModel]).
 *
 * @param supportedPaymentMethods Zahlungsarten der Plattform; leer = „Jetzt bezahlen“ wird nicht angeboten
 */
class BookingsViewModel(
    private val repository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val supportedPaymentMethods: List<PaymentMethodChoice> = emptyList(),
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(BookingsUiState(now = clock.now(), canPayOnline = supportedPaymentMethods.isNotEmpty()))
    val state: StateFlow<BookingsUiState> = _state.asStateFlow()

    /** Lädt die Liste neu; die Oberfläche ruft das beim Einblenden des Screens auf. */
    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null, now = clock.now()) }
        viewModelScope.launch {
            val result = repository.list()
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(isLoading = false, bookings = result.value)
                    is Outcome.Failure -> it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    fun onFilterChange(filter: BookingFilter) = _state.update { it.copy(filter = filter) }

    fun onCancelRequested(booking: Booking) {
        // Während einer laufenden Zahlung nicht stornieren (sonst würde der PaymentIntent unter dem Zahlungsfenster geschlossen).
        _state.update { if (it.payNow.isBusy) it else it.copy(cancelCandidate = booking, cancelError = null) }
    }

    fun onCancelDismissed() = _state.update { it.copy(cancelCandidate = null) }

    fun onCancelErrorShown() = _state.update { it.copy(cancelError = null) }

    fun onCancelConfirmed() {
        val booking = _state.value.cancelCandidate ?: return
        _state.update { it.copy(cancelCandidate = null, cancellingId = booking.id) }
        viewModelScope.launch {
            val result = repository.cancel(booking.id)
            _state.update { current ->
                when (result) {
                    is Outcome.Success -> current.copy(
                        cancellingId = null,
                        bookings = current.bookings.map { if (it.id == booking.id) result.value.withWallboxOf(it) else it },
                    )
                    is Outcome.Failure -> current.copy(cancellingId = null, cancelError = result.error)
                }
            }
        }
    }

    // ---- Bewerten ---------------------------------------------------------------------------

    fun onReviewRequested(booking: Booking) {
        if (!booking.isReviewable()) return
        _state.update { it.copy(reviewDraft = ReviewDraft(booking)) }
    }

    fun onReviewRatingChanged(rating: Int) = updateDraft { it.copy(rating = rating, error = null) }

    fun onReviewCommentChanged(comment: String) = updateDraft { it.copy(comment = comment, error = null) }

    fun onReviewDismissed() {
        // Während des Sendens bleibt der Dialog offen, damit das Ergebnis nicht verloren geht.
        _state.update { if (it.reviewDraft?.isSubmitting == true) it else it.copy(reviewDraft = null) }
    }

    fun onReviewSubmitted() {
        val draft = _state.value.reviewDraft?.takeIf { it.canSubmit } ?: return
        updateDraft { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = repository.review(draft.booking.id, draft.rating, draft.comment.trim().ifEmpty { null })
            when {
                result is Outcome.Success -> markReviewed(draft.booking.id)
                // Schon bewertet (z. B. auf einem anderen Gerät): wie ein Erfolg behandeln, die Bewertung existiert.
                result is Outcome.Failure && result.error == AppError.Conflict -> markReviewed(draft.booking.id)
                result is Outcome.Failure -> updateDraft { it.copy(isSubmitting = false, error = result.error) }
            }
        }
    }

    private fun updateDraft(transform: (ReviewDraft) -> ReviewDraft) {
        _state.update { current -> current.copy(reviewDraft = current.reviewDraft?.let(transform)) }
    }

    private fun markReviewed(bookingId: String) {
        _state.update { current ->
            current.copy(
                reviewDraft = null,
                bookings = current.bookings.map { if (it.id == bookingId) it.copy(reviewed = true) else it },
            )
        }
    }

    // ---- Jetzt bezahlen ---------------------------------------------------------------------

    /** „Jetzt bezahlen“: holt Zahlungskonfiguration und `client_secret` und fordert dann das Zahlungsfenster an. */
    fun onPayNow(booking: Booking) {
        val current = _state.value
        if (!current.canPayOnline || current.payNow.isBusy || !booking.isPayable(current.now)) return
        _state.update { it.copy(payNow = PayNowState.Preparing(booking.id)) }
        viewModelScope.launch {
            val config = when (val result = paymentRepository.config()) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> return@launch setPayNow(PayNowState.PrepareFailed(booking, result.error))
            }
            val setup = paymentSetupFor(config, supportedPaymentMethods)
            if (setup is PaymentSetup.Unavailable) return@launch setPayNow(PayNowState.Unavailable(booking, setup.reason))
            when (val result = repository.startPayment(booking.id)) {
                is Outcome.Failure -> {
                    setPayNow(PayNowState.PrepareFailed(booking, result.error))
                    // Die Buchung ist serverseitig vermutlich nicht mehr offen (z. B. abgelaufen): Liste neu laden.
                    if (result.error !is AppError.Network && result.error !is AppError.Server) refresh()
                }
                is Outcome.Success -> payNowStarted(booking, setup, result.value)
            }
        }
    }

    private fun payNowStarted(booking: Booking, setup: PaymentSetup, creation: BookingCreation) {
        // Schon bezahlt (oder nicht mehr offen): Liste aktualisieren, nichts weiter zu tun.
        if (creation.booking.status != BookingStatus.PENDING) return replaceBooking(creation.booking)
        val clientSecret = creation.clientSecret
        val method = (setup as? PaymentSetup.Ready)?.methods?.firstOrNull()
        if (clientSecret == null || setup !is PaymentSetup.Ready || method == null) {
            return setPayNow(PayNowState.PrepareFailed(booking, AppError.Unknown))
        }
        setPayNow(PayNowState.PaymentRequired(creation.booking.withWallboxOf(booking), PaymentRequest(clientSecret, setup.config, method)))
    }

    /** Die UI hat das Zahlungsfenster geöffnet (verhindert, dass es bei Neuaufbau der UI ein zweites Mal erscheint). */
    fun onPaymentSheetOpened() {
        _state.update {
            val payNow = it.payNow
            if (payNow is PayNowState.PaymentRequired) {
                it.copy(payNow = PayNowState.InProgress(payNow.booking, payNow.request, PaymentStep.SHEET_OPEN))
            } else {
                it
            }
        }
    }

    /** Ergebnis des Zahlungsfensters. Nur wirksam, solange das Fenster als geöffnet gilt. */
    fun onPaymentResult(result: PaymentResult) {
        val payNow = _state.value.payNow as? PayNowState.InProgress ?: return
        if (payNow.step != PaymentStep.SHEET_OPEN) return
        when (result) {
            PaymentResult.Completed -> confirmPayment(payNow.booking, payNow.request)
            PaymentResult.Canceled -> setPayNow(PayNowState.Incomplete(payNow.booking, payNow.request, PaymentIssue.Canceled))
            is PaymentResult.Failed -> setPayNow(PayNowState.Incomplete(payNow.booking, payNow.request, PaymentIssue.Failed))
        }
    }

    /** „Erneut bezahlen“ bzw. „Zahlung prüfen“: nach einer bezahlten, unbestätigten Buchung nur die Bestätigung, sonst von vorn. */
    fun onRetryPayNow() {
        val payNow = _state.value.payNow
        when (payNow) {
            is PayNowState.Incomplete ->
                if (payNow.issue is PaymentIssue.ConfirmationFailed) confirmPayment(payNow.booking, payNow.request) else restartPayNow(payNow.booking)
            is PayNowState.PrepareFailed -> restartPayNow(payNow.booking)
            is PayNowState.Unavailable -> restartPayNow(payNow.booking)
            PayNowState.Idle, is PayNowState.Preparing, is PayNowState.PaymentRequired, is PayNowState.InProgress -> Unit
        }
    }

    /** Blendet Hinweis oder Fehler zum Bezahlen aus. */
    fun onPayNowDismissed() {
        _state.update { if (it.payNow.isBusy) it else it.copy(payNow = PayNowState.Idle) }
    }

    private fun restartPayNow(booking: Booking) {
        _state.update { it.copy(payNow = PayNowState.Idle) }
        onPayNow(booking)
    }

    private fun confirmPayment(booking: Booking, request: PaymentRequest) {
        setPayNow(PayNowState.InProgress(booking, request, PaymentStep.CONFIRMING))
        viewModelScope.launch {
            when (val result = repository.confirmPayment(booking.id)) {
                is Outcome.Success -> replaceBooking(result.value.withWallboxOf(booking))
                is Outcome.Failure ->
                    setPayNow(PayNowState.Incomplete(booking, request, PaymentIssue.ConfirmationFailed(result.error)))
            }
        }
    }

    private fun setPayNow(payNow: PayNowState) = _state.update { it.copy(payNow = payNow) }

    /** Ersetzt die Buchung in der Liste durch den neuen Stand und beendet den Bezahlvorgang. */
    private fun replaceBooking(updated: Booking) {
        _state.update { current ->
            current.copy(
                payNow = PayNowState.Idle,
                bookings = current.bookings.map { if (it.id == updated.id) updated.withWallboxOf(it) else it },
            )
        }
    }

    /** Die nachgeladene Buchung enthält ggf. keine Wallbox-Relation; die bekannte bleibt erhalten. */
    private fun Booking.withWallboxOf(previous: Booking): Booking = if (wallbox == null) copy(wallbox = previous.wallbox) else this
}
