package app.chargeshare.presentation.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.HostRepository
import app.chargeshare.data.Outcome
import app.chargeshare.data.WALLBOX_ZONE
import app.chargeshare.data.localToUtc
import app.chargeshare.data.utcToLocal
import app.chargeshare.shared.model.UpdateWallboxRequest
import app.chargeshare.shared.model.Wallbox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Wallbox bearbeiten (Host). Nutzt dasselbe Formular wie [CreateWallboxViewModel].
 *
 * Nur tatsächlich geänderte Felder werden gesendet. Die Verfügbarkeit geht nur dann an den Server,
 * wenn der Host Zeiten oder Tage geändert hat; ein Wochenplan, der sich nicht als „ein Zeitfenster an
 * mehreren Tagen“ darstellen lässt, bleibt unangetastet. Die Berechtigung (nur der Eigentümer) prüft der Server.
 */
class EditWallboxViewModel(
    private val wallbox: Wallbox,
    private val repository: HostRepository,
    private val zone: TimeZone = WALLBOX_ZONE,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val initial = formStateOf(wallbox, zone, clock.now())
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<CreateWallboxUiState> = _state.asStateFlow()

    fun onChange(change: (CreateWallboxUiState) -> CreateWallboxUiState) =
        _state.update { change(it).copy(errors = emptySet(), submitError = null) }

    fun onDayToggled(dayKey: String) = onChange {
        it.copy(days = if (dayKey in it.days) it.days - dayKey else it.days + dayKey)
    }

    fun onSubmit() {
        val current = _state.value
        if (current.isSubmitting) return
        val availabilityChanged = current.availabilityEditable && availabilityOf(current) != availabilityOf(initial)
        val parsed = parseWallboxForm(current, checkAvailability = availabilityChanged)
        val form = parsed.form
        if (form == null) {
            _state.update { it.copy(errors = parsed.errors) }
            return
        }
        val request = UpdateWallboxRequest(
            name = form.name,
            address = form.address,
            city = form.city,
            postalCode = form.postalCode,
            lat = form.lat,
            lng = form.lng,
            powerKw = form.powerKw,
            connectorType = current.connector,
            pricePerKwh = form.pricePerKwh,
            isMetered = current.isMetered,
            // Leerer Text löscht die Beschreibung auf dem Server.
            description = form.description,
            availability = if (availabilityChanged) form.availability?.localToUtc(zone, clock.now()) else null,
        )
        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val result = repository.updateWallbox(wallbox.id, request)
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(isSubmitting = false, saved = true)
                    is Outcome.Failure -> it.copy(isSubmitting = false, submitError = result.error)
                }
            }
        }
    }

    private fun availabilityOf(s: CreateWallboxUiState) = Triple(s.availabilityStart.trim(), s.availabilityEnd.trim(), s.days)
}

/**
 * Formularzustand aus einer bestehenden Wallbox; Zahlen deutsch formatiert, ohne überflüssiges „.0“.
 * Die Verfügbarkeit (UTC vom Server) wird für [zone] in lokale Zeit umgerechnet.
 */
internal fun formStateOf(wallbox: Wallbox, zone: TimeZone, reference: Instant): CreateWallboxUiState {
    val slots = wallbox.availability.utcToLocal(zone, reference).filterValues { it.isNotEmpty() }
    val uniform = slots.values.map { it.singleOrNull() }.distinct().singleOrNull()
    val representable = slots.isEmpty() || (uniform != null && slots.keys.all { it in WEEKDAY_KEYS })
    val base = CreateWallboxUiState(
        name = wallbox.name,
        address = wallbox.address,
        city = wallbox.city,
        postalCode = wallbox.postalCode,
        lat = wallbox.lat.toString(),
        lng = wallbox.lng.toString(),
        powerKw = decimalInput(wallbox.powerKw),
        connector = wallbox.connectorType,
        price = decimalInput(wallbox.pricePerKwh),
        isMetered = wallbox.isMetered,
        description = wallbox.description.orEmpty(),
        availabilityEditable = representable,
    )
    return if (uniform != null && representable) {
        base.copy(availabilityStart = uniform.start, availabilityEnd = uniform.end, days = slots.keys)
    } else {
        base.copy(days = emptySet())
    }
}

/** `11.0` → `"11"`, `0.34` → `"0,34"`. */
internal fun decimalInput(value: Double): String {
    val text = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    return text.replace('.', ',')
}
