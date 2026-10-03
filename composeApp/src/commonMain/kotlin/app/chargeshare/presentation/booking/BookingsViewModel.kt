package app.chargeshare.presentation.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.BookingRepository
import app.chargeshare.data.Outcome
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

/** Nur offene oder bestätigte Buchungen dürfen vom Fahrer storniert werden. */
fun Booking.isCancellable(): Boolean = status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED

private val UPCOMING_STATUSES = setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE)

/** Buchungsliste mit Stornieren. */
class BookingsViewModel(
    private val repository: BookingRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(BookingsUiState(now = clock.now()))
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

    fun onCancelRequested(booking: Booking) = _state.update { it.copy(cancelCandidate = booking, cancelError = null) }

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

    /** Die nachgeladene Buchung enthält ggf. keine Wallbox-Relation; die bekannte bleibt erhalten. */
    private fun Booking.withWallboxOf(previous: Booking): Booking = if (wallbox == null) copy(wallbox = previous.wallbox) else this
}
