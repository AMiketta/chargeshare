package app.chargeshare.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.AuthRepository
import app.chargeshare.data.Outcome
import app.chargeshare.shared.model.ProfileLimits
import app.chargeshare.shared.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Felder des Profilformulars. */
enum class ProfileField { Name, Phone }

/**
 * Bearbeitungsformular für Name und Telefon.
 *
 * @property errors Felder, die clientseitig abgelehnt wurden
 * @property saveError Fehler des letzten Speichern-Versuchs (z. B. Netz, Server)
 */
data class ProfileEditState(
    val name: String,
    val phone: String,
    val errors: Set<ProfileField> = emptySet(),
    val isSaving: Boolean = false,
    val saveError: AppError? = null,
)

data class ProfileUiState(
    val isBusy: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val error: AppError? = null,
    /** Geöffnetes Bearbeitungsformular, sonst `null`. */
    val edit: ProfileEditState? = null,
)

/** Profil: Name/Telefon bearbeiten, Abmelden, Host werden, Konto löschen (DSGVO). Der Nutzer selbst kommt aus dem `SessionState`. */
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

    // ---- Bearbeiten -------------------------------------------------------------------------

    fun onEditRequested(user: User) {
        _state.update { it.copy(edit = ProfileEditState(name = user.name, phone = user.phone.orEmpty())) }
    }

    fun onEditNameChanged(name: String) = updateEdit { it.copy(name = name, errors = it.errors - ProfileField.Name, saveError = null) }

    fun onEditPhoneChanged(phone: String) = updateEdit { it.copy(phone = phone, errors = it.errors - ProfileField.Phone, saveError = null) }

    fun onEditDismissed() {
        _state.update { if (it.edit?.isSaving == true) it else it.copy(edit = null) }
    }

    fun onEditSaved() {
        val edit = _state.value.edit?.takeIf { !it.isSaving } ?: return
        val errors = validate(edit)
        if (errors.isNotEmpty()) return updateEdit { it.copy(errors = errors) }
        updateEdit { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = authRepository.updateProfile(edit.name.trim(), edit.phone.trim())) {
                is Outcome.Success -> _state.update { it.copy(edit = null) }
                is Outcome.Failure -> updateEdit { it.copy(isSaving = false, saveError = result.error) }
            }
        }
    }

    private fun updateEdit(transform: (ProfileEditState) -> ProfileEditState) {
        _state.update { current -> current.copy(edit = current.edit?.let(transform)) }
    }

    private fun validate(edit: ProfileEditState): Set<ProfileField> = buildSet {
        val name = edit.name.trim()
        if (name.isEmpty() || name.length > ProfileLimits.MAX_NAME_LENGTH) add(ProfileField.Name)
        val phone = edit.phone.trim()
        if (phone.isNotEmpty() && !ProfileLimits.isValidPhone(phone)) add(ProfileField.Phone)
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
