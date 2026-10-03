package app.chargeshare.presentation.wallbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.GeoPoint
import app.chargeshare.data.Outcome
import app.chargeshare.data.WallboxFilter
import app.chargeshare.data.WallboxRepository
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.Wallbox
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/** Darstellung der Suchergebnisse. */
enum class SearchViewMode { Map, List }

data class WallboxListUiState(
    val center: GeoPoint = DEFAULT_CENTER,
    val filter: WallboxFilter = WallboxFilter(),
    /** Freitext; wirkt clientseitig auf Name, Straße und Ort (das Backend hat keine Textsuche). */
    val query: String = "",
    val wallboxes: List<Wallbox> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
    /** Ob die Plattform eine Karte anzeigen kann; sonst gibt es nur die Liste. */
    val mapSupported: Boolean = false,
    val viewMode: SearchViewMode = SearchViewMode.List,
) {
    /** Karte nur anzeigen, wenn die Plattform sie unterstützt und sie gewählt ist. */
    val showMap: Boolean get() = mapSupported && viewMode == SearchViewMode.Map

    /** Ergebnisliste nach Freitext und Online-Filter. */
    val visibleWallboxes: List<Wallbox>
        get() = wallboxes.filter { box ->
            (!filter.onlyOnline || box.isOnline) &&
                (query.isBlank() || listOf(box.name, box.address, box.city).any { it.contains(query.trim(), ignoreCase = true) })
        }
}

/** Standard-Suchzentrum Bremen, solange der Gerätestandort nicht (mit Einwilligung) angebunden ist. */
val DEFAULT_CENTER = GeoPoint(lat = 53.0793, lng = 8.8017)

/**
 * Umkreis-/Filtersuche nach Wallboxen.
 *
 * @param mapSupported `true`, wenn die Plattform eine Karte zeigt (`isMapSupported`); dann ist die Karte die Startansicht,
 * sonst gibt es nur die Liste.
 */
class WallboxListViewModel(
    private val repository: WallboxRepository,
    mapSupported: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(
        WallboxListUiState(
            mapSupported = mapSupported,
            viewMode = if (mapSupported) SearchViewMode.Map else SearchViewMode.List,
        ),
    )
    val state: StateFlow<WallboxListUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        search()
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun onRadiusChange(radiusM: Double) = changeFilter { it.copy(radiusM = radiusM) }

    fun onConnectorChange(connector: ConnectorType?) = changeFilter { it.copy(connector = connector) }

    fun onMinPowerChange(minPowerKw: Double?) = changeFilter { it.copy(minPowerKw = minPowerKw) }

    fun onMaxPriceChange(maxPricePerKwh: Double?) = changeFilter { it.copy(maxPricePerKwh = maxPricePerKwh) }

    fun onOnlyOnlineChange(onlyOnline: Boolean) = _state.update { it.copy(filter = it.filter.copy(onlyOnline = onlyOnline)) }

    fun onViewModeChange(viewMode: SearchViewMode) = _state.update { it.copy(viewMode = viewMode) }

    fun onRefresh() = search()

    private fun changeFilter(change: (WallboxFilter) -> WallboxFilter) {
        _state.update { it.copy(filter = change(it.filter)) }
        search()
    }

    private fun search() {
        // Eine neue Suche ersetzt eine laufende, damit späte Antworten alte Filter nicht überschreiben.
        searchJob?.cancel()
        val snapshot = _state.updateAndGet { it.copy(isLoading = true, error = null) }
        searchJob = viewModelScope.launch {
            val result = repository.search(snapshot.center, snapshot.filter)
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(isLoading = false, wallboxes = result.value)
                    is Outcome.Failure -> it.copy(isLoading = false, error = result.error, wallboxes = emptyList())
                }
            }
        }
    }
}
