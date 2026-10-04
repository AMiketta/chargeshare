package app.chargeshare.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.Outcome
import app.chargeshare.data.PaymentRepository
import app.chargeshare.payment.PaymentResult
import app.chargeshare.payment.SaveCardRequest
import app.chargeshare.shared.model.SavedCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Schritte von „Karte hinzufügen“. */
sealed interface AddCardState {
    data object Idle : AddCardState

    /** SetupIntent und Konfiguration werden geholt. */
    data object Preparing : AddCardState

    /** Bereit: die UI öffnet jetzt das Kartenformular der Plattform und meldet das über [SavedCardsViewModel.onSheetOpened]. */
    data class Ready(val request: SaveCardRequest) : AddCardState

    /** Das Kartenformular ist offen. */
    data object SheetOpen : AddCardState
}

data class SavedCardsUiState(
    val cards: List<SavedCard> = emptyList(),
    /** `false`, wenn der Server ohne Stripe läuft (Dev-Modus): dann gibt es nichts zu speichern. */
    val supported: Boolean = true,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    /** Diese Plattform kann Karten ohne Zahlung speichern (Android). */
    val canAddCard: Boolean = false,
    val removeCandidate: SavedCard? = null,
    val removingId: String? = null,
    val addCard: AddCardState = AddCardState.Idle,
    /** Fehler einer Aktion (Entfernen, Hinzufügen); `null` mit [addCardFailed] = Stripe hat das Speichern abgelehnt. */
    val actionError: AppError? = null,
    val addCardFailed: Boolean = false,
) {
    val isBusy: Boolean get() = removingId != null || addCard != AddCardState.Idle
}

/**
 * Gespeicherte Karten ansehen, entfernen und hinzufügen. Das Kartenformular gehört der Plattform:
 * Das ViewModel meldet es über [AddCardState.Ready] und erhält das Ergebnis über [onSaveCardResult].
 *
 * @param canAddCard die Plattform kann Karten speichern ([app.chargeshare.payment.PaymentLauncher.supportsCardSetup])
 */
class SavedCardsViewModel(
    private val repository: PaymentRepository,
    canAddCard: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(SavedCardsUiState(canAddCard = canAddCard))
    val state: StateFlow<SavedCardsUiState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.savedCards()
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(isLoading = false, cards = result.value.cards, supported = result.value.supported)
                    is Outcome.Failure -> it.copy(isLoading = false, error = result.error)
                }
            }
        }
    }

    fun onRemoveRequested(card: SavedCard) {
        _state.update { if (it.isBusy) it else it.copy(removeCandidate = card, actionError = null, addCardFailed = false) }
    }

    fun onRemoveDismissed() = _state.update { it.copy(removeCandidate = null) }

    fun onRemoveConfirmed() {
        val card = _state.value.removeCandidate ?: return
        _state.update { it.copy(removeCandidate = null, removingId = card.id) }
        viewModelScope.launch {
            val result = repository.removeCard(card.id)
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(removingId = null, cards = it.cards.filterNot { c -> c.id == card.id })
                    is Outcome.Failure -> it.copy(removingId = null, actionError = result.error)
                }
            }
        }
    }

    fun onActionErrorShown() = _state.update { it.copy(actionError = null, addCardFailed = false) }

    // ---- Karte hinzufügen -------------------------------------------------------------------

    fun onAddCard() {
        val current = _state.value
        if (!current.canAddCard || !current.supported || current.isBusy) return
        _state.update { it.copy(addCard = AddCardState.Preparing, actionError = null, addCardFailed = false) }
        viewModelScope.launch {
            val config = when (val result = repository.config()) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> return@launch addCardFailed(result.error)
            }
            when (val result = repository.createSetupIntent()) {
                is Outcome.Success ->
                    _state.update { it.copy(addCard = AddCardState.Ready(SaveCardRequest(result.value.clientSecret, config))) }
                is Outcome.Failure -> addCardFailed(result.error)
            }
        }
    }

    /** Die UI hat das Kartenformular geöffnet (verhindert ein zweites Öffnen bei Neuaufbau der UI). */
    fun onSheetOpened() {
        _state.update { if (it.addCard is AddCardState.Ready) it.copy(addCard = AddCardState.SheetOpen) else it }
    }

    /** Ergebnis des Kartenformulars; nur wirksam, solange es als offen gilt. */
    fun onSaveCardResult(result: PaymentResult) {
        if (_state.value.addCard != AddCardState.SheetOpen) return
        when (result) {
            PaymentResult.Completed -> {
                _state.update { it.copy(addCard = AddCardState.Idle) }
                refresh()
            }
            PaymentResult.Canceled -> _state.update { it.copy(addCard = AddCardState.Idle) }
            is PaymentResult.Failed -> _state.update { it.copy(addCard = AddCardState.Idle, addCardFailed = true) }
        }
    }

    private fun addCardFailed(error: AppError) = _state.update { it.copy(addCard = AddCardState.Idle, actionError = error) }
}
