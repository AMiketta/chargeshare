package app.chargeshare.presentation.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.BookingRepository
import app.chargeshare.data.Outcome
import app.chargeshare.data.WallboxRepository
import app.chargeshare.shared.model.Booking
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

/** Fortschritt des Absendens. */
sealed interface BookingSubmitState {
    data object Idle : BookingSubmitState
    data object Submitting : BookingSubmitState
    data class Success(val booking: Booking) : BookingSubmitState
    data class Failed(val error: AppError) : BookingSubmitState
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
) {
    val canSubmit: Boolean
        get() = selectedStart != null && selectedDurationMinutes != null && submit !is BookingSubmitState.Submitting
}

/** Anzahl der Tage, für die gebucht werden kann (Design: horizontaler Datumsscroller). */
const val BOOKING_HORIZON_DAYS = 7

/** Slot wählen, Kosten schätzen, buchen. */
class BookingFlowViewModel(
    wallbox: Wallbox,
    private val wallboxRepository: WallboxRepository,
    private val bookingRepository: BookingRepository,
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
    }

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
                is Outcome.Success -> _state.update { it.copy(submit = BookingSubmitState.Success(result.value)) }
                is Outcome.Failure -> {
                    _state.update { it.copy(submit = BookingSubmitState.Failed(result.error)) }
                    // Slot vergeben: Liste neu laden, damit der Nutzer einen freien Slot sieht.
                    if (result.error == AppError.Conflict) loadSlots(resetSelection = true)
                }
            }
        }
    }

    fun onFailureDismissed() {
        _state.update { if (it.submit is BookingSubmitState.Failed) it.copy(submit = BookingSubmitState.Idle) else it }
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
