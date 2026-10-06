package app.chargeshare.presentation.auth

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

enum class AuthMode { Login, Register }

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val nameError: FieldError? = null,
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val isSubmitting: Boolean = false,
    /** Fehler der Server-Anfrage (z. B. falsche Zugangsdaten); Feldfehler stehen in `*Error`. */
    val submitError: AppError? = null,
)

/**
 * Login und Registrierung mit Validierung.
 * Bei Erfolg wechselt die App über den `SessionState` des Repositories automatisch in die Hauptnavigation.
 */
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onModeChange(mode: AuthMode) {
        _state.update { AuthUiState(mode = mode, email = it.email) }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, submitError = null) }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, submitError = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, passwordError = null, submitError = null) }

    /** Nur Debug-Builds: meldet direkt mit einem Demo-Konto an (Felder füllen und absenden). */
    fun onQuickLogin(email: String, password: String) {
        if (_state.value.isSubmitting) return
        _state.value = AuthUiState(mode = AuthMode.Login, email = email, password = password)
        onSubmit()
    }

    fun onSubmit() {
        val current = _state.value
        if (current.isSubmitting) return
        val isRegister = current.mode == AuthMode.Register
        val validated = current.copy(
            nameError = if (isRegister) validateName(current.name) else null,
            emailError = validateEmail(current.email),
            passwordError = validatePassword(current.password, enforceMinLength = isRegister),
        )
        if (validated.nameError != null || validated.emailError != null || validated.passwordError != null) {
            _state.value = validated
            return
        }
        _state.value = validated.copy(isSubmitting = true, submitError = null)
        viewModelScope.launch {
            val result = if (isRegister) {
                authRepository.register(current.name, current.email, current.password)
            } else {
                authRepository.login(current.email, current.password)
            }
            _state.update {
                when (result) {
                    // Passwort nicht länger als nötig im Zustand halten.
                    is Outcome.Success -> it.copy(isSubmitting = false, password = "")
                    is Outcome.Failure -> it.copy(isSubmitting = false, submitError = result.error)
                }
            }
        }
    }
}
