package app.chargeshare.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AuthRepository
import app.chargeshare.data.SessionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Hält den Anmeldezustand für die ganze App (Login-Screen oder Hauptnavigation). */
class SessionViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val session: StateFlow<SessionState> = authRepository.session

    init {
        viewModelScope.launch { authRepository.restoreSession() }
    }
}
