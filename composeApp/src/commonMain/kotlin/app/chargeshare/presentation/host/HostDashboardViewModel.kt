package app.chargeshare.presentation.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.HostRepository
import app.chargeshare.data.Outcome
import app.chargeshare.shared.model.ChargingSession
import app.chargeshare.shared.model.HostEarnings
import app.chargeshare.shared.model.Wallbox
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HostDashboardUiState(
    val isLoading: Boolean = true,
    val earnings: HostEarnings? = null,
    val sessions: List<ChargingSession> = emptyList(),
    val wallboxes: List<Wallbox> = emptyList(),
    val error: AppError? = null,
    /** Wallbox, deren Online-Status gerade umgeschaltet wird. */
    val togglingId: String? = null,
    val toggleError: AppError? = null,
)

/** Host-Dashboard: Einnahmen, letzte Sessions, eigene Wallboxen mit Freigabe-Schalter. */
class HostDashboardViewModel(
    private val ownerId: String,
    private val repository: HostRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HostDashboardUiState())
    val state: StateFlow<HostDashboardUiState> = _state.asStateFlow()

    /** Lädt alles neu; die Oberfläche ruft das beim Einblenden des Screens auf. */
    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val earnings = async { repository.earnings() }
            val sessions = async { repository.sessions() }
            val wallboxes = async { repository.myWallboxes(ownerId) }
            val results = listOf(earnings.await(), sessions.await(), wallboxes.await())
            val firstError = results.filterIsInstance<Outcome.Failure>().firstOrNull()?.error
            _state.update {
                it.copy(
                    isLoading = false,
                    error = firstError,
                    earnings = (earnings.await() as? Outcome.Success)?.value,
                    sessions = (sessions.await() as? Outcome.Success)?.value.orEmpty(),
                    wallboxes = (wallboxes.await() as? Outcome.Success)?.value.orEmpty(),
                )
            }
        }
    }

    fun onOnlineToggled(wallbox: Wallbox, isOnline: Boolean) {
        if (_state.value.togglingId != null) return
        _state.update { it.copy(togglingId = wallbox.id, toggleError = null) }
        viewModelScope.launch {
            val result = repository.setOnline(wallbox.id, isOnline)
            _state.update { current ->
                when (result) {
                    is Outcome.Success -> current.copy(
                        togglingId = null,
                        wallboxes = current.wallboxes.map { if (it.id == wallbox.id) it.copy(isOnline = result.value) else it },
                    )
                    is Outcome.Failure -> current.copy(togglingId = null, toggleError = result.error)
                }
            }
        }
    }

    fun onToggleErrorShown() = _state.update { it.copy(toggleError = null) }
}
