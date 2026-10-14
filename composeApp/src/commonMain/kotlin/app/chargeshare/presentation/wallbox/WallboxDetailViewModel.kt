package app.chargeshare.presentation.wallbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.Outcome
import app.chargeshare.data.WallboxRepository
import app.chargeshare.shared.model.HostProfile
import app.chargeshare.shared.model.Review
import app.chargeshare.shared.model.Wallbox
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class WallboxDetailUiState(
    val isLoading: Boolean = true,
    val wallbox: Wallbox? = null,
    val reviews: List<Review> = emptyList(),
    /** Host-Karte; `null`, solange unbekannt oder nicht ladbar (dann fehlt nur die Karte). */
    val host: HostProfile? = null,
    /** Anzahl freier 30-Minuten-Slots am heutigen (lokalen) Tag; `null`, solange unbekannt. */
    val freeSlotsToday: Int? = null,
    val error: AppError? = null,
)

/** Lädt Wallbox, Bewertungen, Host-Profil und die Verfügbarkeit von heute. */
class WallboxDetailViewModel(
    val wallboxId: String,
    private val repository: WallboxRepository,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(WallboxDetailUiState())
    val state: StateFlow<WallboxDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onRetry() {
        _state.value = WallboxDetailUiState()
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val wallboxRequest = async { repository.wallbox(wallboxId) }
            // Zusatzinfos dürfen die Detailseite nie blockieren: eine unerwartete Ausnahme (z. B. eine JS-Ausnahme auf
            // Wasm, die runCatchingOutcome nicht als Exception sieht) würde sonst den ganzen Ladevorgang abbrechen
            // und die Seite bliebe im Ladezustand hängen.
            val reviewsRequest = async { optional { repository.reviews(wallboxId) } }
            val hostRequest = async { optional { repository.hostProfile(wallboxId) } }
            val slotsRequest = async { optional { repository.freeSlotStarts(wallboxId, clock.todayIn(zone), zone) } }

            when (val wallbox = wallboxRequest.await()) {
                is Outcome.Failure -> _state.update { it.copy(isLoading = false, error = wallbox.error) }
                is Outcome.Success -> {
                    // Bewertungen, Host und Slots sind Zusatzinfos: fallen sie aus, bleibt die Detailseite nutzbar.
                    val reviews = (reviewsRequest.await() as? Outcome.Success)?.value.orEmpty()
                    val slots = (slotsRequest.await() as? Outcome.Success)?.value?.size
                    _state.value = WallboxDetailUiState(
                        isLoading = false,
                        wallbox = wallbox.value,
                        reviews = reviews,
                        host = (hostRequest.await() as? Outcome.Success)?.value,
                        freeSlotsToday = slots,
                    )
                }
            }
        }
    }
}

/** Ergebnis von [block] oder `null` bei jeder Ausnahme außer Abbruch (Structured Concurrency bleibt erhalten). */
private suspend fun <T> optional(block: suspend () -> Outcome<T>): Outcome<T>? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        null
    }
