package app.chargeshare.presentation.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.BookingRepository
import app.chargeshare.data.BookingCreation
import app.chargeshare.data.Outcome
import app.chargeshare.data.PaymentRepository
import app.chargeshare.data.WallboxRepository
import app.chargeshare.payment.PaymentMethodChoice
import app.chargeshare.payment.PaymentRequest
import app.chargeshare.payment.PaymentResult
import app.chargeshare.shared.model.Booking
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.Wallbox
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Fortschritt des Absendens. Bei echter Zahlung (Stripe) läuft der Ablauf
 * `Submitting → PaymentRequired → PaymentInProgress → Success`; bei Abbruch oder Fehler landet er in [PaymentIncomplete].
 */
sealed interface BookingSubmitState {
    data object Idle : BookingSubmitState
    data object Submitting : BookingSubmitState
    data class Success(val booking: Booking) : BookingSubmitState
    data class Failed(val error: AppError) : BookingSubmitState

    /** Buchung ist angelegt (`pending`); die UI soll jetzt das Zahlungsfenster für [request] zeigen. */
    data class PaymentRequired(val booking: Booking, val request: PaymentRequest) : BookingSubmitState

    data class PaymentInProgress(
        val booking: Booking,
        val request: PaymentRequest,
        val step: PaymentStep,
    ) : BookingSubmitState

    /**
     * Die Zahlung ist offen: erneut versuchen oder die Buchung verwerfen.
     *
     * @property discardError Fehler des letzten Verwerfen-Versuchs (Storno), sonst `null`
     */
    data class PaymentIncomplete(
        val booking: Booking,
        val request: PaymentRequest,
        val issue: PaymentIssue,
        val discardError: AppError? = null,
    ) : BookingSubmitState
}

data class BookingFlowUiState(
    val wallbox: Wallbox,
    /** Wählbare Tage (heute + [BOOKING_HORIZON_DAYS] − 1), lokal. */
    val days: List<LocalDate>,
    val selectedDay: LocalDate,
    val freeStarts: List<Instant> = emptyList(),
    val isLoadingSlots: Boolean = true,
    val slotsError: AppError? = null,
    val selectedStart: Instant? = null,
    val durationOptions: List<Int> = emptyList(),
    val selectedDurationMinutes: Int? = null,
    val estimate: BookingEstimate? = null,
    val submit: BookingSubmitState = BookingSubmitState.Idle,
    val paymentSetup: PaymentSetup = PaymentSetup.Loading,
    val selectedPaymentMethod: PaymentMethodChoice? = null,
) {
    /** Buchen ist möglich, wenn ein Slot gewählt ist, nichts läuft und die Zahlung klar ist (Test oder gewählte Methode). */
    val canSubmit: Boolean
        get() = selectedStart != null && selectedDurationMinutes != null && isIdleForSubmit && isPaymentChosen

    private val isIdleForSubmit: Boolean
        get() = submit is BookingSubmitState.Idle || submit is BookingSubmitState.Failed

    private val isPaymentChosen: Boolean
        get() = when (paymentSetup) {
            PaymentSetup.TestPayment -> true
            is PaymentSetup.Ready -> selectedPaymentMethod != null
            PaymentSetup.Loading, is PaymentSetup.Unavailable, is PaymentSetup.LoadFailed -> false
        }
}

/** Anzahl der Tage, für die gebucht werden kann (Design: horizontaler Datumsscroller). */
const val BOOKING_HORIZON_DAYS = 7

/**
 * Slot wählen, Kosten schätzen, Zahlungsart wählen, buchen und bezahlen.
 *
 * Das Zahlungsfenster selbst gehört der Plattform (Activity/ViewController) und wird von der UI geöffnet:
 * Das ViewModel meldet es über [BookingSubmitState.PaymentRequired] und erhält das Ergebnis über [onPaymentResult].
 *
 * @param supportedPaymentMethods Zahlungsarten der Plattform ([app.chargeshare.payment.PaymentLauncher.supportedMethods])
 */
class BookingFlowViewModel(
    wallbox: Wallbox,
    private val wallboxRepository: WallboxRepository,
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val supportedPaymentMethods: List<PaymentMethodChoice> = emptyList(),
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state: MutableStateFlow<BookingFlowUiState>
    val state: StateFlow<BookingFlowUiState>
    private var slotsJob: Job? = null

    init {
        val today = clock.todayIn(zone)
        _state = MutableStateFlow(
            BookingFlowUiState(
                wallbox = wallbox,
                days = List(BOOKING_HORIZON_DAYS) { today.plus(it, DateTimeUnit.DAY) },
                selectedDay = today,
            ),
        )
        state = _state.asStateFlow()
        loadSlots()
        loadPaymentConfig()
    }

    fun onPaymentMethodSelected(method: PaymentMethodChoice) {
        _state.update { if (it.paymentSetup is PaymentSetup.Ready) it.copy(selectedPaymentMethod = method) else it }
    }

    fun onRetryPaymentConfig() = loadPaymentConfig()

    fun onDaySelected(day: LocalDate) {
        _state.update {
            it.copy(
                selectedDay = day,
                selectedStart = null,
                durationOptions = emptyList(),
                selectedDurationMinutes = null,
                estimate = null,
            )
        }
        loadSlots()
    }

    fun onStartSelected(start: Instant) {
        val current = _state.value
        val options = SlotPlanner.durationOptions(start, current.freeStarts.toSet(), current.wallbox)
        // Bisherige Dauer behalten, wenn sie noch möglich ist; sonst die kürzeste.
        val duration = current.selectedDurationMinutes?.takeIf { it in options } ?: options.firstOrNull()
        _state.update {
            it.copy(
                selectedStart = start,
                durationOptions = options,
                selectedDurationMinutes = duration,
                estimate = duration?.let { minutes -> SlotPlanner.estimate(it.wallbox, minutes) },
            )
        }
    }

    fun onDurationSelected(minutes: Int) {
        _state.update { it.copy(selectedDurationMinutes = minutes, estimate = SlotPlanner.estimate(it.wallbox, minutes)) }
    }

    fun onRetrySlots() = loadSlots()

    fun onSubmit() {
        val current = _state.value
        val start = current.selectedStart ?: return
        val duration = current.selectedDurationMinutes ?: return
        if (!current.canSubmit) return
        _state.update { it.copy(submit = BookingSubmitState.Submitting) }
        viewModelScope.launch {
            val result = bookingRepository.create(current.wallbox.id, start, start + duration.minutes)
            when (result) {
                is Outcome.Success -> _state.update { it.copy(submit = submitStateAfterCreate(it, result.value)) }
                is Outcome.Failure -> {
                    _state.update { it.copy(submit = BookingSubmitState.Failed(result.error)) }
                    // Slot vergeben: Liste neu laden, damit der Nutzer einen freien Slot sieht.
                    if (result.error == AppError.Conflict) loadSlots(resetSelection = true)
                }
            }
        }
    }

    /** Die UI hat das Zahlungsfenster geöffnet (verhindert, dass es bei Neuaufbau der UI ein zweites Mal erscheint). */
    fun onPaymentSheetOpened() {
        _state.update {
            val submit = it.submit
            if (submit is BookingSubmitState.PaymentRequired) {
                it.copy(submit = BookingSubmitState.PaymentInProgress(submit.booking, submit.request, PaymentStep.SHEET_OPEN))
            } else {
                it
            }
        }
    }

    /** Ergebnis des Zahlungsfensters. Nur wirksam, solange das Fenster als geöffnet gilt. */
    fun onPaymentResult(result: PaymentResult) {
        val submit = _state.value.submit as? BookingSubmitState.PaymentInProgress ?: return
        if (submit.step != PaymentStep.SHEET_OPEN) return
        when (result) {
            PaymentResult.Completed -> confirmPayment(submit.booking, submit.request)
            PaymentResult.Canceled -> markIncomplete(submit.booking, submit.request, PaymentIssue.Canceled)
            is PaymentResult.Failed -> markIncomplete(submit.booking, submit.request, PaymentIssue.Failed)
        }
    }

    /** „Erneut versuchen“: nach einer bezahlten, aber unbestätigten Buchung nur die Bestätigung, sonst das Zahlungsfenster noch einmal. */
    fun onRetryPayment() {
        val submit = _state.value.submit as? BookingSubmitState.PaymentIncomplete ?: return
        if (submit.issue is PaymentIssue.ConfirmationFailed) {
            confirmPayment(submit.booking, submit.request)
        } else {
            _state.update { it.copy(submit = BookingSubmitState.PaymentRequired(submit.booking, submit.request)) }
        }
    }

    /** „Buchung verwerfen“: storniert die unbezahlte Buchung, damit der Slot wieder frei wird. */
    fun onDiscardBooking() {
        val submit = _state.value.submit as? BookingSubmitState.PaymentIncomplete ?: return
        _state.update { it.copy(submit = BookingSubmitState.PaymentInProgress(submit.booking, submit.request, PaymentStep.DISCARDING)) }
        viewModelScope.launch {
            when (val result = bookingRepository.cancel(submit.booking.id)) {
                is Outcome.Success -> {
                    _state.update { it.copy(submit = BookingSubmitState.Idle) }
                    loadSlots(resetSelection = true)
                }
                is Outcome.Failure -> _state.update { it.copy(submit = submit.copy(discardError = result.error)) }
            }
        }
    }

    fun onFailureDismissed() {
        _state.update { if (it.submit is BookingSubmitState.Failed) it.copy(submit = BookingSubmitState.Idle) else it }
    }

    /** Bei bereits bestätigter Buchung (Fake-Zahlung) ist man fertig; sonst wird bezahlt. */
    private fun submitStateAfterCreate(state: BookingFlowUiState, creation: BookingCreation): BookingSubmitState {
        val setup = state.paymentSetup
        val method = state.selectedPaymentMethod
        val needsPayment = creation.booking.status == BookingStatus.PENDING
        if (!needsPayment || setup !is PaymentSetup.Ready || method == null) {
            return BookingSubmitState.Success(creation.booking)
        }
        // Ohne client_secret lässt sich nicht bezahlen; die Buchung bleibt offen und erscheint unter „Buchungen“.
        val clientSecret = creation.clientSecret ?: return BookingSubmitState.Failed(AppError.Unknown)
        return BookingSubmitState.PaymentRequired(creation.booking, PaymentRequest(clientSecret, setup.config, method))
    }

    private fun confirmPayment(booking: Booking, request: PaymentRequest) {
        _state.update { it.copy(submit = BookingSubmitState.PaymentInProgress(booking, request, PaymentStep.CONFIRMING)) }
        viewModelScope.launch {
            when (val result = bookingRepository.confirmPayment(booking.id)) {
                is Outcome.Success -> _state.update { it.copy(submit = BookingSubmitState.Success(result.value)) }
                is Outcome.Failure -> markIncomplete(booking, request, PaymentIssue.ConfirmationFailed(result.error))
            }
        }
    }

    private fun markIncomplete(booking: Booking, request: PaymentRequest, issue: PaymentIssue) {
        _state.update { it.copy(submit = BookingSubmitState.PaymentIncomplete(booking, request, issue)) }
    }

    private fun loadPaymentConfig() {
        _state.update { it.copy(paymentSetup = PaymentSetup.Loading) }
        viewModelScope.launch {
            val setup = when (val result = paymentRepository.config()) {
                is Outcome.Success -> paymentSetupFor(result.value, supportedPaymentMethods)
                is Outcome.Failure -> PaymentSetup.LoadFailed(result.error)
            }
            _state.update { it.copy(paymentSetup = setup, selectedPaymentMethod = (setup as? PaymentSetup.Ready)?.methods?.firstOrNull()) }
        }
    }

    private fun loadSlots(resetSelection: Boolean = false) {
        slotsJob?.cancel()
        val snapshot = _state.value
        _state.update {
            it.copy(
                isLoadingSlots = true,
                slotsError = null,
                selectedStart = if (resetSelection) null else it.selectedStart,
                durationOptions = if (resetSelection) emptyList() else it.durationOptions,
                selectedDurationMinutes = if (resetSelection) null else it.selectedDurationMinutes,
                estimate = if (resetSelection) null else it.estimate,
            )
        }
        slotsJob = viewModelScope.launch {
            val result = wallboxRepository.freeSlotStarts(snapshot.wallbox.id, snapshot.selectedDay, zone)
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(isLoadingSlots = false, freeStarts = result.value)
                    is Outcome.Failure -> it.copy(isLoadingSlots = false, slotsError = result.error, freeStarts = emptyList())
                }
            }
        }
    }
}
