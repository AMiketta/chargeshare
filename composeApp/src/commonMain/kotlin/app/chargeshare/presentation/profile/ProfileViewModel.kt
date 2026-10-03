package app.chargeshare.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.AuthRepository
import app.chargeshare.data.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isBusy: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val error: AppError? = null,
)

/** Profil: Abmelden, Host werden, Konto löschen (DSGVO). Der Nutzer selbst kommt aus dem `SessionState`. */
class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun onLogout() = runBusy { authRepository.logout(); Outcome.Success(Unit) }

    fun onBecomeHost() = runBusy { authRepository.becomeHost() }

    fun onDeleteRequested() = _state.update { it.copy(showDeleteConfirmation = true) }

    fun onDeleteDismissed() = _state.update { it.copy(showDeleteConfirmation = false) }

    fun onDeleteConfirmed() {
        _state.update { it.copy(showDeleteConfirmation = false) }
        runBusy { authRepository.deleteAccount() }
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    private fun runBusy(action: suspend () -> Outcome<*>) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val result = action()
            _state.update { it.copy(isBusy = false, error = (result as? Outcome.Failure)?.error) }
        }
    }
}
