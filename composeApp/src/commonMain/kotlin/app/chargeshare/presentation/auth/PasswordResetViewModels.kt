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

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: FieldError? = null,
    val isSubmitting: Boolean = false,
    /** Die Anfrage wurde angenommen. Der Text bleibt neutral, weil der Server nicht verrät, ob die Adresse existiert. */
    val sent: Boolean = false,
    val submitError: AppError? = null,
)

/** „Passwort vergessen“: nimmt die E-Mail-Adresse entgegen und bittet den Server um eine Reset-Mail. */
class ForgotPasswordViewModel(
    private val authRepository: AuthRepository,
    initialEmail: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState(email = initialEmail))
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, submitError = null) }

    fun onSubmit() {
        val current = _state.value
        if (current.isSubmitting) return
        val emailError = validateEmail(current.email)
        if (emailError != null) {
            _state.value = current.copy(emailError = emailError)
            return
        }
        _state.value = current.copy(isSubmitting = true, submitError = null)
        viewModelScope.launch {
            val result = authRepository.forgotPassword(current.email)
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(isSubmitting = false, sent = true)
                    is Outcome.Failure -> it.copy(isSubmitting = false, submitError = result.error)
                }
            }
        }
    }
}

data class ResetPasswordUiState(
    val token: String = "",
    val password: String = "",
    val tokenError: FieldError? = null,
    val passwordError: FieldError? = null,
    val isSubmitting: Boolean = false,
    /** Das Passwort wurde geändert; die UI führt zur Anmeldung. */
    val done: Boolean = false,
    val submitError: AppError? = null,
)

/**
 * Neues Passwort setzen. Das Token kommt aus dem Deep Link der Mail ([initialToken]) und lässt sich von Hand
 * eintragen, falls der Link auf diesem Gerät nicht in der App landet (z. B. Mail am Desktop gelesen).
 */
class ResetPasswordViewModel(
    private val authRepository: AuthRepository,
    initialToken: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(ResetPasswordUiState(token = initialToken))
    val state: StateFlow<ResetPasswordUiState> = _state.asStateFlow()

    fun onTokenChange(value: String) = _state.update { it.copy(token = value, tokenError = null, submitError = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, passwordError = null, submitError = null) }

    fun onSubmit() {
        val current = _state.value
        if (current.isSubmitting) return
        val validated = current.copy(
            tokenError = if (current.token.isBlank()) FieldError.Required else null,
            passwordError = validatePassword(current.password, enforceMinLength = true),
        )
        if (validated.tokenError != null || validated.passwordError != null) {
            _state.value = validated
            return
        }
        _state.value = validated.copy(isSubmitting = true, submitError = null)
        viewModelScope.launch {
            val result = authRepository.resetPassword(current.token, current.password)
            _state.update {
                when (result) {
                    // Passwort nicht länger als nötig im Zustand halten.
                    is Outcome.Success -> it.copy(isSubmitting = false, password = "", done = true)
                    is Outcome.Failure -> it.copy(isSubmitting = false, submitError = result.error)
                }
            }
        }
    }
}
