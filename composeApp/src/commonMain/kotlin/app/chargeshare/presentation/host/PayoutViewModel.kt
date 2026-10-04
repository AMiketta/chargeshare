package app.chargeshare.presentation.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.Outcome
import app.chargeshare.data.PayoutRepository
import app.chargeshare.shared.model.PayoutState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PayoutUiState(
    /** `null`, solange der Status noch nicht geladen ist. */
    val state: PayoutState? = null,
    val isLoading: Boolean = false,
    /** Der Onboarding-Link wird gerade geholt. */
    val isStarting: Boolean = false,
    val error: AppError? = null,
    /** Zu öffnender Onboarding-Link; die UI öffnet ihn und meldet [PayoutViewModel.onOnboardingUrlHandled]. */
    val onboardingUrl: String? = null,
    /** Die Plattform konnte den Browser nicht öffnen. */
    val openFailed: Boolean = false,
) {
    /** Die Karte ist sichtbar, wenn der Server Auszahlungskonten verlangt – oder der Status nicht geladen werden konnte. */
    val isVisible: Boolean get() = if (state == null) error != null else state != PayoutState.NOT_REQUIRED
}

/**
 * Auszahlungsstatus des Hosts (Stripe Connect): nicht eingerichtet / in Prüfung / bereit, Start des Onboardings im Browser
 * und sofortiger Abgleich mit Stripe nach der Rückkehr per Deep Link.
 */
class PayoutViewModel(private val repository: PayoutRepository) : ViewModel() {
    private val _state = MutableStateFlow(PayoutUiState())
    val state: StateFlow<PayoutUiState> = _state.asStateFlow()

    /** Lädt den Status beim Einblenden. Ist das Konto „in Prüfung“, fragt der Server gleich bei Stripe nach. */
    fun refresh() = load(syncWithStripe = _state.value.state == PayoutState.PENDING)

    /** Der Host kommt aus dem Browser zurück: Stripe sofort fragen, statt auf den Hintergrund-Job zu warten. */
    fun onReturnedFromOnboarding() = load(syncWithStripe = true)

    /** „Status prüfen“ nach manueller Auswahl. */
    fun onCheckStatus() = load(syncWithStripe = true)

    /** Der alte Onboarding-Link ist abgelaufen: Stripe verlangt einen neuen. */
    fun onOnboardingExpired() = onSetUpClicked()

    fun onSetUpClicked() {
        if (_state.value.isStarting) return
        _state.update { it.copy(isStarting = true, error = null, openFailed = false) }
        viewModelScope.launch {
            when (val result = repository.startOnboarding()) {
                is Outcome.Success -> _state.update { it.copy(isStarting = false, onboardingUrl = result.value) }
                is Outcome.Failure -> _state.update { it.copy(isStarting = false, error = result.error) }
            }
        }
    }

    /** Die UI hat den Link geöffnet ([opened] = `true`) oder konnte es nicht. */
    fun onOnboardingUrlHandled(opened: Boolean) = _state.update { it.copy(onboardingUrl = null, openFailed = !opened) }

    private fun load(syncWithStripe: Boolean) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = if (syncWithStripe) repository.refresh() else repository.status()
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(isLoading = false, state = result.value.state)
                    is Outcome.Failure -> it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }
}
