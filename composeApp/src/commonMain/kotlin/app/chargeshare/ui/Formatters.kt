package app.chargeshare.ui

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Deutsche Zahlen-/Datumsformatierung (Dezimalkomma, 24-Stunden-Zeit). Gemeinsam für alle Plattformen. */

/** Dezimalzahl mit [digits] Nachkommastellen und Komma, z. B. `11,22`. */
fun formatDecimal(value: Double, digits: Int): String {
    val factor = 10.0.pow(digits)
    val scaled = (abs(value) * factor).roundToLong()
    val integerPart = scaled / factor.toLong()
    val fraction = (scaled % factor.toLong()).toString().padStart(digits, '0')
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return if (digits == 0) "$sign$integerPart" else "$sign$integerPart,$fraction"
}

/** Eurobetrag, z. B. `11,22 €`. */
fun formatEuro(amount: Double): String = "${formatDecimal(amount, CENT_DIGITS)} €"

/** Strompreis, z. B. `0,34 €/kWh`. */
fun formatPricePerKwh(price: Double): String = "${formatDecimal(price, CENT_DIGITS)} €/kWh"

/** Leistung ohne überflüssige Nachkommastelle: `11 kW`, `7,4 kW`. */
fun formatPowerKw(kw: Double): String {
    val digits = if (kw == kw.roundToLong().toDouble()) 0 else 1
    return "${formatDecimal(kw, digits)} kW"
}

fun formatKwh(kwh: Double): String = "${formatDecimal(kwh, 1)} kWh"

/** Entfernung: unter 1 km in Metern, sonst in Kilometern mit einer Nachkommastelle. */
fun formatDistance(meters: Double): String =
    if (meters < METERS_PER_KM) "${meters.roundToLong()} m" else "${formatDecimal(meters / METERS_PER_KM, 1)} km"

/** Umkreis als Chip-Text: `500 m`, `5 km`. */
fun formatRadius(meters: Double): String =
    if (meters < METERS_PER_KM) "${meters.roundToLong()} m" else "${formatDecimal(meters / METERS_PER_KM, 0)} km"

/** Uhrzeit in der Zeitzone [zone], z. B. `14:30`. */
fun formatTime(instant: Instant, zone: TimeZone): String {
    val time = instant.toLocalDateTime(zone).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

/** Kurzdatum, z. B. `Mo, 5. Okt.`. */
fun formatDate(date: LocalDate): String =
    "${weekdayShort(date.dayOfWeek)}, ${date.day}. ${MONTHS_SHORT[date.month.ordinal]}"

/** Datum und Uhrzeit eines Zeitpunkts, z. B. `Mo, 5. Okt., 14:30`. */
fun formatDateTime(instant: Instant, zone: TimeZone): String =
    "${formatDate(instant.toLocalDateTime(zone).date)}, ${formatTime(instant, zone)}"

fun weekdayShort(day: DayOfWeek): String = WEEKDAYS_SHORT[day.ordinal]

/** Wochentag des Backends (`"monday"`) als Kurzname (`Mo`); unbekannte Schlüssel bleiben unverändert. */
fun weekdayShortFromKey(key: String): String {
    val index = WEEKDAY_KEYS_ORDERED.indexOf(key.lowercase())
    return if (index >= 0) WEEKDAYS_SHORT[index] else key
}

/** Position des Wochentags (Montag = 0) für die Sortierung; unbekannte Schlüssel kommen ans Ende. */
fun weekdayIndexFromKey(key: String): Int =
    WEEKDAY_KEYS_ORDERED.indexOf(key.lowercase()).takeIf { it >= 0 } ?: WEEKDAY_KEYS_ORDERED.size

/** Initialen für Avatare, z. B. `Andreas Miketta` → `AM`. */
fun initialsOf(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private const val CENT_DIGITS = 2
private const val METERS_PER_KM = 1000.0
private val WEEKDAYS_SHORT = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val WEEKDAY_KEYS_ORDERED = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
private val MONTHS_SHORT = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.")
