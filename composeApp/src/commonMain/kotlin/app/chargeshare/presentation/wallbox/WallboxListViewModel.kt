package app.chargeshare.presentation.wallbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.GeoPoint
import app.chargeshare.data.InMemoryPreferenceStorage
import app.chargeshare.data.Outcome
import app.chargeshare.data.PreferenceStorage
import app.chargeshare.data.WallboxFilter
import app.chargeshare.data.WallboxRepository
import app.chargeshare.platform.LocationPermissionStatus
import app.chargeshare.platform.LocationProvider
import app.chargeshare.platform.UnsupportedLocationProvider
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
    /** Vom Nutzer angetippter Pin; `null` oder nicht mehr sichtbar = die nächste Wallbox gilt als aktiv. */
    val selectedWallboxId: String? = null,
    /** Suche nutzt den freigegebenen Gerätestandort statt Bremen. */
    val usesDeviceLocation: Boolean = false,
    /** Sanfter In-App-Dialog vor dem System-Permission-Sheet. */
    val showLocationPrompt: Boolean = false,
    /** Hinweis, dass ohne Freigabe Bremen als Zentrum gilt. */
    val showLocationFallbackHint: Boolean = false,
) {
    /** Karte nur anzeigen, wenn die Plattform sie unterstützt und sie gewählt ist. */
    val showMap: Boolean get() = mapSupported && viewMode == SearchViewMode.Map

    /** Ergebnisliste nach Freitext und Online-Filter (gilt für Karten-Pins und Liste). */
    val visibleWallboxes: List<Wallbox>
        get() = wallboxes.filter { box -> (!filter.onlyOnline || box.isOnline) && box.matchesQuery(query) }

    /** Es ist ein Freitext gesetzt, der alle geladenen Wallboxen ausblendet (eigener Leerzustand). */
    val noQueryMatches: Boolean
        get() = query.isNotBlank() && wallboxes.isNotEmpty() && visibleWallboxes.isEmpty()

    /** Die auf Karte und in der Liste hervorgehobene Wallbox: die gewählte, sonst die erste (nächste) sichtbare. */
    val activeWallbox: Wallbox?
        get() = visibleWallboxes.let { visible -> visible.firstOrNull { it.id == selectedWallboxId } ?: visible.firstOrNull() }
}

/**
 * Freitext-Treffer: jedes Wort der Eingabe (Leerzeichen-getrennt, ohne Groß/Klein) muss in Name, Straße, PLZ oder Ort
 * vorkommen. So finden „Nr. 2“, „markt bremen“ oder „28195“ ihre Wallbox, unabhängig von der Reihenfolge.
 */
fun Wallbox.matchesQuery(query: String): Boolean {
    val tokens = query.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val haystack = listOf(name, address, postalCode, city).joinToString(" ")
    return tokens.all { haystack.contains(it, ignoreCase = true) }
}

private val WHITESPACE = Regex("\\s+")

/** Standard-Suchzentrum Bremen, wenn der Gerätestandort fehlt oder verweigert wurde. */
val DEFAULT_CENTER = GeoPoint(lat = 53.0793, lng = 8.8017)

/**
 * Umkreis-/Filtersuche nach Wallboxen.
 *
 * @param mapSupported `true`, wenn die Plattform eine Karte zeigt (`isMapSupported`); dann ist die Karte die Startansicht,
 * sonst gibt es nur die Liste.
 * @param locationProvider Gerätestandort (Android/iOS); Desktop/Web = [UnsupportedLocationProvider]
 * @param preferences speichert, ob der Standort-Hinweis schon einmal beantwortet wurde (kein erneutes Nachfragen)
 */
class WallboxListViewModel(
    private val repository: WallboxRepository,
    mapSupported: Boolean = false,
    private val locationProvider: LocationProvider = UnsupportedLocationProvider,
    private val preferences: PreferenceStorage = InMemoryPreferenceStorage(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        WallboxListUiState(
            mapSupported = mapSupported,
            viewMode = if (mapSupported) SearchViewMode.Map else SearchViewMode.List,
        ),
    )
    val state: StateFlow<WallboxListUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var locationJob: Job? = null

    init {
        // Sofort mit Bremen suchen, Standort danach nachziehen – die UI bleibt responsiv.
        search()
        resolveDeviceLocation()
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    /** Leert das Suchfeld (X-Button); alle Wallboxen im Umkreis sind wieder sichtbar. */
    fun onClearQuery() = onQueryChange("")

    fun onRadiusChange(radiusM: Double) = changeFilter { it.copy(radiusM = radiusM) }

    fun onConnectorChange(connector: ConnectorType?) = changeFilter { it.copy(connector = connector) }

    fun onMinPowerChange(minPowerKw: Double?) = changeFilter { it.copy(minPowerKw = minPowerKw) }

    fun onMaxPriceChange(maxPricePerKwh: Double?) = changeFilter { it.copy(maxPricePerKwh = maxPricePerKwh) }

    fun onOnlyOnlineChange(onlyOnline: Boolean) = _state.update { it.copy(filter = it.filter.copy(onlyOnline = onlyOnline)) }

    /** Setzt alle Schnellfilter (Stecker, Mindestleistung, „jetzt verfügbar“) zurück, Umkreis und Preis bleiben. */
    fun onClearQuickFilters() = changeFilter { it.copy(connector = null, minPowerKw = null, onlyOnline = false) }

    /** Markiert eine Wallbox als aktiv (Tipp auf den Pin). */
    fun onWallboxSelected(id: String) = _state.update { it.copy(selectedWallboxId = id) }

    fun onViewModeChange(viewMode: SearchViewMode) = _state.update { it.copy(viewMode = viewMode) }

    fun onRefresh() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            if (locationProvider.permissionStatus() == LocationPermissionStatus.Granted) {
                applyDeviceLocationOrFallback()
            }
            search()
        }
    }

    /** Nutzer bestätigt den sanften Standort-Hinweis → Systemdialog. */
    fun onAllowLocation() {
        markLocationPromptDone()
        _state.update { it.copy(showLocationPrompt = false) }
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            when (locationProvider.requestPermission()) {
                LocationPermissionStatus.Granted -> {
                    applyDeviceLocationOrFallback()
                    search()
                }
                else -> _state.update { it.copy(showLocationFallbackHint = true, usesDeviceLocation = false) }
            }
        }
    }

    /** Nutzer lehnt den sanften Hinweis ab → Bremen bleibt, kurzer Hinweis. */
    fun onSkipLocation() {
        markLocationPromptDone()
        _state.update {
            it.copy(
                showLocationPrompt = false,
                showLocationFallbackHint = true,
                usesDeviceLocation = false,
                center = DEFAULT_CENTER,
            )
        }
    }

    /** Schließt den Dialog ohne Entscheidung (gleiche Wirkung wie „Nicht jetzt“). */
    fun onDismissLocationPrompt() = onSkipLocation()

    private fun resolveDeviceLocation() {
        if (!locationProvider.isSupported) return
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            when (locationProvider.permissionStatus()) {
                LocationPermissionStatus.Granted -> {
                    if (applyDeviceLocationOrFallback()) search()
                }
                LocationPermissionStatus.NotDetermined -> {
                    if (preferences.read(LOCATION_PROMPT_DONE_KEY) == "true") {
                        _state.update { it.copy(showLocationFallbackHint = true) }
                    } else {
                        _state.update { it.copy(showLocationPrompt = true) }
                    }
                }
                LocationPermissionStatus.Denied,
                LocationPermissionStatus.Unavailable,
                -> _state.update { it.copy(showLocationFallbackHint = true) }
            }
        }
    }

    /**
     * Liest den Gerätestandort und aktualisiert [WallboxListUiState.center].
     * @return `true`, wenn sich das Zentrum geändert hat und eine neue Suche nötig ist
     */
    private suspend fun applyDeviceLocationOrFallback(): Boolean {
        val point = locationProvider.currentLocation()
        if (point == null) {
            val changed = _state.value.center != DEFAULT_CENTER || _state.value.usesDeviceLocation
            _state.update {
                it.copy(
                    center = DEFAULT_CENTER,
                    usesDeviceLocation = false,
                    showLocationFallbackHint = true,
                )
            }
            return changed
        }
        val changed = _state.value.center != point
        _state.update {
            it.copy(
                center = point,
                usesDeviceLocation = true,
                showLocationFallbackHint = false,
            )
        }
        return changed
    }

    private fun markLocationPromptDone() {
        preferences.write(LOCATION_PROMPT_DONE_KEY, "true")
    }

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

    private companion object {
        const val LOCATION_PROMPT_DONE_KEY = "location_prompt_done"
    }
}
