package app.chargeshare.presentation.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.HostRepository
import app.chargeshare.data.Outcome
import app.chargeshare.presentation.wallbox.DEFAULT_CENTER
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.CreateWallboxRequest
import app.chargeshare.shared.model.TimeSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Formularfelder des Anlegens; Beschriftungen und Fehlertexte kommen aus der UI. */
enum class WallboxField { Name, Address, City, PostalCode, Lat, Lng, PowerKw, Price, AvailabilityStart, AvailabilityEnd, Days }

/** Wochentage in der Schreibweise des Backends (`weekly availability`), Montag zuerst. */
val WEEKDAY_KEYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

data class CreateWallboxUiState(
    val name: String = "",
    val address: String = "",
    val city: String = "",
    val postalCode: String = "",
    val lat: String = DEFAULT_CENTER.lat.toString(),
    val lng: String = DEFAULT_CENTER.lng.toString(),
    val powerKw: String = "11",
    val connector: ConnectorType = ConnectorType.TYPE2,
    val price: String = "0,34",
    val isMetered: Boolean = true,
    /** Verfügbarkeit in **UTC** (Vertrag des Backends), `HH:MM`. */
    val availabilityStart: String = "07:00",
    val availabilityEnd: String = "19:00",
    val days: Set<String> = WEEKDAY_KEYS.take(WEEKDAYS_DEFAULT_COUNT).toSet(),
    val errors: Set<WallboxField> = emptySet(),
    val isSubmitting: Boolean = false,
    val submitError: AppError? = null,
    val created: Boolean = false,
)

private const val WEEKDAYS_DEFAULT_COUNT = 5

/** Wallbox anlegen (Host). Validiert clientseitig; der Server prüft maßgeblich nach. */
class CreateWallboxViewModel(private val repository: HostRepository) : ViewModel() {

    private val _state = MutableStateFlow(CreateWallboxUiState())
    val state: StateFlow<CreateWallboxUiState> = _state.asStateFlow()

    fun onChange(change: (CreateWallboxUiState) -> CreateWallboxUiState) =
        _state.update { change(it).copy(errors = emptySet(), submitError = null) }

    fun onDayToggled(dayKey: String) = onChange {
        it.copy(days = if (dayKey in it.days) it.days - dayKey else it.days + dayKey)
    }

    fun onSubmit() {
        val current = _state.value
        if (current.isSubmitting) return
        val parsed = parse(current)
        if (parsed.errors.isNotEmpty()) {
            _state.update { it.copy(errors = parsed.errors) }
            return
        }
        val request = checkNotNull(parsed.request) { "Anfrage muss bei fehlerfreier Validierung vorliegen" }
        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val result = repository.createWallbox(request)
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(isSubmitting = false, created = true)
                    is Outcome.Failure -> it.copy(isSubmitting = false, submitError = result.error)
                }
            }
        }
    }

    /** Ergebnis der Validierung: entweder Fehler oder eine gültige Anfrage. */
    internal class Parsed(val request: CreateWallboxRequest?, val errors: Set<WallboxField>)

    internal fun parse(s: CreateWallboxUiState): Parsed {
        val errors = mutableSetOf<WallboxField>()
        if (s.name.isBlank() || s.name.length > MAX_NAME_LENGTH) errors += WallboxField.Name
        if (s.address.isBlank()) errors += WallboxField.Address
        if (s.city.isBlank()) errors += WallboxField.City
        if (s.postalCode.isBlank()) errors += WallboxField.PostalCode
        val lat = parseDecimal(s.lat)?.takeIf { it in -90.0..90.0 }
        if (lat == null) errors += WallboxField.Lat
        val lng = parseDecimal(s.lng)?.takeIf { it in -180.0..180.0 }
        if (lng == null) errors += WallboxField.Lng
        val power = parseDecimal(s.powerKw)?.takeIf { it > 0 }
        if (power == null) errors += WallboxField.PowerKw
        val price = parseDecimal(s.price)?.takeIf { it > 0 }
        if (price == null) errors += WallboxField.Price
        val start = parseTime(s.availabilityStart)
        if (start == null) errors += WallboxField.AvailabilityStart
        val end = parseTime(s.availabilityEnd)
        if (end == null) errors += WallboxField.AvailabilityEnd
        if (start != null && end != null && end <= start) errors += WallboxField.AvailabilityEnd
        if (s.days.isEmpty()) errors += WallboxField.Days
        if (errors.isNotEmpty() || lat == null || lng == null || power == null || price == null) {
            return Parsed(null, errors)
        }
        val slot = TimeSlot(start = s.availabilityStart.trim().padStart(TIME_LENGTH, '0'), end = s.availabilityEnd.trim().padStart(TIME_LENGTH, '0'))
        val request = CreateWallboxRequest(
            name = s.name.trim(),
            address = s.address.trim(),
            city = s.city.trim(),
            postalCode = s.postalCode.trim(),
            lat = lat,
            lng = lng,
            powerKw = power,
            connectorType = s.connector,
            pricePerKwh = price,
            isMetered = s.isMetered,
            availability = WEEKDAY_KEYS.filter { it in s.days }.associateWith { listOf(slot) },
        )
        return Parsed(request, emptySet())
    }

    private companion object {
        const val MAX_NAME_LENGTH = 200
        const val TIME_LENGTH = 5
    }
}

/** Dezimalzahl mit Komma oder Punkt (`"0,34"` / `"0.34"`). */
internal fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** `HH:MM` oder `H:MM` als Minuten seit Mitternacht; sonst `null`. */
internal fun parseTime(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.size != 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (parts[1].length != 2 || hours !in 0..23 || minutes !in 0..59) return null
    return hours * 60 + minutes
}
