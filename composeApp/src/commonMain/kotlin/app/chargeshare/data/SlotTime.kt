package app.chargeshare.data

import app.chargeshare.shared.model.TimeSlot
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Länge eines vom Server angebotenen Slots in Minuten (Vertrag: `GET /wallboxes/{id}/slots`). */
const val SLOT_MINUTES = 30

/**
 * UTC-Kalendertage, die ein lokaler Tag [day] berührt.
 *
 * Der Server rechnet Slots pro **UTC-Tag**; ein lokaler Tag (z. B. in Berlin) kann daher zwei UTC-Tage umfassen.
 */
fun utcDatesTouchedBy(day: LocalDate, zone: TimeZone): List<LocalDate> {
    val first = day.atStartOfDayIn(zone).toLocalDateTime(TimeZone.UTC).date
    val lastInstant = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone) - 1.nanoseconds
    val last = lastInstant.toLocalDateTime(TimeZone.UTC).date
    return if (first == last) listOf(first) else listOf(first, last)
}

/** Startzeitpunkte der Slots eines UTC-Tages; unlesbare Zeitangaben werden übersprungen. */
fun List<TimeSlot>.toStartInstants(utcDate: LocalDate): List<Instant> =
    mapNotNull { slot ->
        runCatching { LocalTime.parse(slot.start) }.getOrNull()?.let { utcDate.atTime(it).toInstant(TimeZone.UTC) }
    }

/** Beginn und Ende (exklusiv) eines lokalen Tages als Zeitpunkte. */
fun localDayRange(day: LocalDate, zone: TimeZone): ClosedRange<Instant> {
    val start = day.atStartOfDayIn(zone)
    val end = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone) - 1.nanoseconds
    return start..end
}
