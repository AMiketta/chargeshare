package app.chargeshare.presentation.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chargeshare.data.AppError
import app.chargeshare.data.HostRepository
import app.chargeshare.data.Outcome
import app.chargeshare.data.WALLBOX_ZONE
import app.chargeshare.data.localToUtc
import app.chargeshare.presentation.wallbox.DEFAULT_CENTER
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.CreateWallboxRequest
import app.chargeshare.shared.model.TimeSlot
import app.chargeshare.shared.model.WallboxLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.math.roundToLong
import kotlin.time.Clock

/** Formularfelder des Anlegens und Bearbeitens; Beschriftungen und Fehlertexte kommen aus der UI. */
enum class WallboxField { Name, Address, City, PostalCode, Lat, Lng, PowerKw, Price, Description, AvailabilityStart, AvailabilityEnd, Days }

/** Wochentage in der Schreibweise des Backends (`weekly availability`), Montag zuerst. */
val WEEKDAY_KEYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

/** Formularzustand für Anlegen und Bearbeiten einer Wallbox. */
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
    val description: String = "",
    /** Verfügbarkeit in **lokaler Zeit** ([WALLBOX_ZONE]), `HH:MM`; vor dem Senden wird nach UTC umgerechnet. */
    val availabilityStart: String = "07:00",
    val availabilityEnd: String = "19:00",
    val days: Set<String> = WEEKDAY_KEYS.take(WEEKDAYS_DEFAULT_COUNT).toSet(),
    /**
     * `false`, wenn der bestehende Wochenplan nicht als „ein Zeitfenster an mehreren Tagen“ darstellbar ist
     * (nur beim Bearbeiten). Die Verfügbarkeit wird dann weder angezeigt noch gesendet.
     */
    val availabilityEditable: Boolean = true,
    val errors: Set<WallboxField> = emptySet(),
    val isSubmitting: Boolean = false,
    val submitError: AppError? = null,
    /** Erfolgreich gespeichert (angelegt bzw. geändert). */
    val saved: Boolean = false,
    /** Stand der Adresssuche („Koordinaten aus Adresse ermitteln“). */
    val geocode: GeocodeStatus = GeocodeStatus.Idle,
    /** Fotos der Wallbox (Pfade wie vom Server); nur beim Bearbeiten gefüllt. */
    val photos: List<String> = emptyList(),
    /** Ein Foto wird gerade hochgeladen oder entfernt. */
    val photoBusy: Boolean = false,
    val photoError: PhotoIssue? = null,
)

/** Ergebnis von „Koordinaten aus Adresse ermitteln“. */
sealed interface GeocodeStatus {
    data object Idle : GeocodeStatus
    data object Loading : GeocodeStatus

    /** Gefunden; [displayName] ist die Adresse laut Kartendaten, damit der Host den Treffer prüfen kann. */
    data class Found(val displayName: String?) : GeocodeStatus

    /** Straße, PLZ oder Ort fehlen noch. */
    data object AddressIncomplete : GeocodeStatus

    /** Die Adresse ist in den Kartendaten nicht bekannt. */
    data object NotFound : GeocodeStatus

    /** Der Adressdienst antwortet gerade nicht: Koordinaten von Hand eintragen. */
    data object Unavailable : GeocodeStatus

    data class Failed(val error: AppError) : GeocodeStatus
}

/** Fehler beim Hochladen oder Entfernen eines Fotos. */
sealed interface PhotoIssue {
    /** Die Datei ließ sich nicht lesen (oder ist zu groß für den Upload). */
    data object Unreadable : PhotoIssue

    data class Failed(val error: AppError) : PhotoIssue
}

private const val WEEKDAYS_DEFAULT_COUNT = 5

/** Wallbox anlegen (Host). Validiert clientseitig; der Server prüft maßgeblich nach. */
class CreateWallboxViewModel(
    private val repository: HostRepository,
    private val zone: TimeZone = WALLBOX_ZONE,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateWallboxUiState())
    val state: StateFlow<CreateWallboxUiState> = _state.asStateFlow()

    fun onChange(change: (CreateWallboxUiState) -> CreateWallboxUiState) =
        _state.update { change(it).copy(errors = emptySet(), submitError = null, geocode = it.geocode.afterEdit()) }

    fun onGeocode() = geocodeAddress(viewModelScope, _state, repository)

    fun onDayToggled(dayKey: String) = onChange {
        it.copy(days = if (dayKey in it.days) it.days - dayKey else it.days + dayKey)
    }

    fun onSubmit() {
        val current = _state.value
        if (current.isSubmitting) return
        val parsed = parseWallboxForm(current)
        val form = parsed.form
        if (form == null) {
            _state.update { it.copy(errors = parsed.errors) }
            return
        }
        val request = CreateWallboxRequest(
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
            description = form.description.ifEmpty { null },
            availability = form.availability.orEmpty().localToUtc(zone, clock.now()),
        )
        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val result = repository.createWallbox(request)
            _state.update {
                when (result) {
                    is Outcome.Success -> it.copy(isSubmitting = false, saved = true)
                    is Outcome.Failure -> it.copy(isSubmitting = false, submitError = result.error)
                }
            }
        }
    }
}

/** Geprüfte Formularwerte; nur gültig, wenn `errors` leer ist. */
internal class ParsedWallboxForm(
    val name: String,
    val address: String,
    val city: String,
    val postalCode: String,
    val lat: Double,
    val lng: Double,
    val powerKw: Double,
    val pricePerKwh: Double,
    /** Getrimmte Beschreibung, leer = keine. */
    val description: String,
    /** `null`, wenn die Verfügbarkeit nicht geprüft wurde (unverändert bzw. nicht editierbar). */
    val availability: Map<String, List<TimeSlot>>?,
)

internal class WallboxFormResult(val form: ParsedWallboxForm?, val errors: Set<WallboxField>)

/**
 * Prüft das Formular. Mit [checkAvailability] = `false` bleiben Zeiten und Tage unbeachtet
 * (beim Bearbeiten, solange der Host die Verfügbarkeit nicht anfasst).
 */
internal fun parseWallboxForm(s: CreateWallboxUiState, checkAvailability: Boolean = true): WallboxFormResult {
    val errors = mutableSetOf<WallboxField>()
    if (s.name.isBlank() || s.name.trim().length > WallboxLimits.MAX_NAME_LENGTH) errors += WallboxField.Name
    if (s.address.isBlank()) errors += WallboxField.Address
    if (s.city.isBlank()) errors += WallboxField.City
    if (s.postalCode.isBlank()) errors += WallboxField.PostalCode
    val lat = parseDecimal(s.lat)?.takeIf { it in -90.0..90.0 }
    if (lat == null) errors += WallboxField.Lat
    val lng = parseDecimal(s.lng)?.takeIf { it in -180.0..180.0 }
    if (lng == null) errors += WallboxField.Lng
    val power = parseDecimal(s.powerKw)?.takeIf { it > 0 && it <= WallboxLimits.MAX_POWER_KW }
    if (power == null) errors += WallboxField.PowerKw
    val price = parseDecimal(s.price)?.takeIf { it > 0 && it <= WallboxLimits.MAX_PRICE_PER_KWH }
    if (price == null) errors += WallboxField.Price
    if (s.description.trim().length > WallboxLimits.MAX_DESCRIPTION_LENGTH) errors += WallboxField.Description
    var availability: Map<String, List<TimeSlot>>? = null
    if (checkAvailability) {
        val start = parseTime(s.availabilityStart)
        if (start == null) errors += WallboxField.AvailabilityStart
        val end = parseTime(s.availabilityEnd)
        if (end == null) errors += WallboxField.AvailabilityEnd
        if (start != null && end != null && end <= start) errors += WallboxField.AvailabilityEnd
        if (s.days.isEmpty()) errors += WallboxField.Days
        if (errors.isEmpty()) {
            val slot = TimeSlot(start = s.availabilityStart.trim().padStart(TIME_LENGTH, '0'), end = s.availabilityEnd.trim().padStart(TIME_LENGTH, '0'))
            availability = WEEKDAY_KEYS.filter { it in s.days }.associateWith { listOf(slot) }
        }
    }
    if (errors.isNotEmpty() || lat == null || lng == null || power == null || price == null) {
        return WallboxFormResult(null, errors)
    }
    val form = ParsedWallboxForm(
        name = s.name.trim(),
        address = s.address.trim(),
        city = s.city.trim(),
        postalCode = s.postalCode.trim(),
        lat = lat,
        lng = lng,
        powerKw = power,
        pricePerKwh = price,
        description = s.description.trim(),
        availability = availability,
    )
    return WallboxFormResult(form, emptySet())
}

private const val TIME_LENGTH = 5

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

/** Eine Bearbeitung macht „gefunden/nicht gefunden“ ungültig; eine laufende Suche läuft weiter. */
internal fun GeocodeStatus.afterEdit(): GeocodeStatus = if (this is GeocodeStatus.Loading) this else GeocodeStatus.Idle

/**
 * „Koordinaten aus Adresse ermitteln“: fragt den Server und trägt bei einem Treffer Breiten- und Längengrad ein.
 * Schlägt die Suche fehl, bleiben die Felder unverändert und der Host kann die Koordinaten von Hand eintragen.
 */
internal fun geocodeAddress(scope: CoroutineScope, state: MutableStateFlow<CreateWallboxUiState>, repository: HostRepository) {
    val current = state.value
    if (current.geocode is GeocodeStatus.Loading) return
    if (current.address.isBlank() || current.postalCode.isBlank() || current.city.isBlank()) {
        state.update { it.copy(geocode = GeocodeStatus.AddressIncomplete) }
        return
    }
    state.update { it.copy(geocode = GeocodeStatus.Loading) }
    scope.launch {
        val result = repository.geocode(current.address.trim(), current.postalCode.trim(), current.city.trim())
        state.update {
            when (result) {
                is Outcome.Success -> it.copy(
                    lat = coordinateInput(result.value.lat),
                    lng = coordinateInput(result.value.lng),
                    errors = it.errors - WallboxField.Lat - WallboxField.Lng,
                    geocode = GeocodeStatus.Found(result.value.displayName),
                )
                is Outcome.Failure -> it.copy(geocode = result.error.toGeocodeStatus())
            }
        }
    }
}

private fun AppError.toGeocodeStatus(): GeocodeStatus = when (this) {
    AppError.NotFound -> GeocodeStatus.NotFound
    AppError.Server, AppError.Network -> GeocodeStatus.Unavailable
    else -> GeocodeStatus.Failed(this)
}

/** Koordinate mit höchstens sechs Nachkommastellen (ca. 10 cm), mit Punkt wie im Formular vorbelegt. */
internal fun coordinateInput(value: Double): String {
    val rounded = (value * COORDINATE_FACTOR).roundToLong() / COORDINATE_FACTOR
    return rounded.toString()
}

private const val COORDINATE_FACTOR = 1_000_000.0
