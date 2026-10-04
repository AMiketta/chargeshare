package app.chargeshare.presentation.wallbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.FavoritesRepository
import app.chargeshare.data.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property favoriteIds IDs der favorisierten Wallboxen (schon beim Tippen aktualisiert, siehe [FavoritesViewModel.onToggle])
 * @property pendingIds Wallboxen, deren Änderung gerade zum Server unterwegs ist
 * @property error letzter Fehler beim Speichern; die UI blendet ihn ein, bis [FavoritesViewModel.onErrorShown] aufgerufen wird
 */
data class FavoritesUiState(
    val favoriteIds: Set<String> = emptySet(),
    val pendingIds: Set<String> = emptySet(),
    val error: AppError? = null,
) {
    fun isFavorite(wallboxId: String): Boolean = wallboxId in favoriteIds
}

/**
 * Favoriten des angemeldeten Nutzers, geteilt von Liste und Detailseite (ein Exemplar pro Nutzer, siehe `App.kt`).
 *
 * Ein Tipp aufs Herz wirkt sofort („optimistisch“) und wird bei einem Serverfehler zurückgenommen.
 * Schlägt schon das erste Laden fehl, bleiben die Herzen leer – die Favoriten sind Zusatzinfo, die Seiten zeigen dafür keinen Fehler.
 */
class FavoritesViewModel(private val repository: FavoritesRepository) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = (repository.ids() as? Outcome.Success)?.value ?: return@launch
            // Hat der Nutzer während des Ladens schon getippt, gewinnt seine Änderung.
            _state.update { current ->
                current.copy(favoriteIds = (loaded - current.pendingIds) + (current.favoriteIds intersect current.pendingIds))
            }
        }
    }

    /** Schaltet das Herz der Wallbox um; solange deren letzte Änderung noch unterwegs ist, wird ein weiterer Tipp ignoriert. */
    fun onToggle(wallboxId: String) {
        val before = _state.value
        if (wallboxId in before.pendingIds) return
        val wasFavorite = before.isFavorite(wallboxId)
        _state.update {
            it.copy(
                favoriteIds = if (wasFavorite) it.favoriteIds - wallboxId else it.favoriteIds + wallboxId,
                pendingIds = it.pendingIds + wallboxId,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = if (wasFavorite) repository.remove(wallboxId) else repository.add(wallboxId)
            _state.update { current ->
                val settled = current.copy(pendingIds = current.pendingIds - wallboxId)
                when (result) {
                    is Outcome.Success -> settled
                    is Outcome.Failure -> settled.copy(
                        favoriteIds = if (wasFavorite) settled.favoriteIds + wallboxId else settled.favoriteIds - wallboxId,
                        error = result.error,
                    )
                }
            }
        }
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }
}
