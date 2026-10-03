package app.chargeshare.data

import app.chargeshare.presentation.host.WEEKDAY_KEYS
import app.chargeshare.shared.model.TimeSlot
import app.chargeshare.shared.model.WeeklyAvailability
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt

/**
 * Zeitzone, in der Hosts ihre Verfügbarkeit eingeben und lesen. Das Backend speichert den Wochenplan in UTC;
 * die Wallboxen stehen in Deutschland.
 */
val WALLBOX_ZONE: TimeZone = TimeZone.of("Europe/Berlin")

private const val MINUTES_PER_DAY = 24 * 60
private const val DAYS_PER_WEEK = 7
private const val SECONDS_PER_MINUTE = 60
private const val END_OF_DAY = "24:00"

/**
 * Wochenplan vom Server (UTC) in lokale Zeit von [zone] umrechnen.
 *
 * Der Server kennt keine Sommerzeit: es gilt der UTC-Versatz von [reference] (Standard: jetzt). Nach dem Wechsel
 * zwischen Sommer- und Winterzeit verschieben sich die tatsächlichen Ortszeiten daher um eine Stunde, bis der Host
 * den Plan neu speichert.
 */
fun WeeklyAvailability.utcToLocal(zone: TimeZone, reference: Instant): WeeklyAvailability =
    shifted(zone.offsetMinutes(reference))

/** Lokalen Wochenplan von [zone] für den Server nach UTC umrechnen (Gegenstück zu [utcToLocal], gleiche Einschränkung). */
fun WeeklyAvailability.localToUtc(zone: TimeZone, reference: Instant): WeeklyAvailability =
    shifted(-zone.offsetMinutes(reference))

private fun TimeZone.offsetMinutes(reference: Instant): Int = offsetAt(reference).totalSeconds / SECONDS_PER_MINUTE

/**
 * Verschiebt alle Fenster um [deltaMinutes]. Reicht ein Fenster dadurch über Mitternacht, wird es am Tageswechsel
 * geteilt (Ende `24:00`) und der Rest landet am Folgetag; über die Wochengrenze läuft es von Sonntag auf Montag.
 * Unlesbare Fenster werden übersprungen. Berührende Fenster eines Tages werden zusammengefasst.
 */
private fun WeeklyAvailability.shifted(deltaMinutes: Int): WeeklyAvailability {
    val perDay = Array(DAYS_PER_WEEK) { mutableListOf<IntRange>() }
    for ((key, slots) in this) {
        val dayIndex = WEEKDAY_KEYS.indexOf(key.lowercase()).takeIf { it >= 0 } ?: continue
        for (slot in slots) {
            val start = parseMinutes(slot.start) ?: continue
            val end = parseMinutes(slot.end) ?: continue
            if (end <= start) continue
            var from = dayIndex * MINUTES_PER_DAY + start + deltaMinutes
            val to = from + (end - start)
            while (from < to) {
                val dayStart = from.floorDiv(MINUTES_PER_DAY) * MINUTES_PER_DAY
                val segmentEnd = minOf(to, dayStart + MINUTES_PER_DAY)
                val day = (dayStart / MINUTES_PER_DAY).mod(DAYS_PER_WEEK)
                perDay[day] += (from - dayStart)..(segmentEnd - dayStart)
                from = segmentEnd
            }
        }
    }
    return buildMap {
        perDay.forEachIndexed { index, ranges ->
            if (ranges.isEmpty()) return@forEachIndexed
            val merged = mutableListOf<IntRange>()
            for (range in ranges.sortedBy { it.first }) {
                val last = merged.lastOrNull()
                if (last != null && range.first <= last.last) {
                    merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
                } else {
                    merged += range
                }
            }
            put(WEEKDAY_KEYS[index], merged.map { TimeSlot(formatMinutes(it.first), formatMinutes(it.last)) })
        }
    }
}

/** `HH:MM` oder Tagesende `24:00` als Minuten seit Mitternacht. */
private fun parseMinutes(text: String): Int? {
    if (text == END_OF_DAY) return MINUTES_PER_DAY
    val parts = text.split(':')
    if (parts.size != 2) return null
    val hours = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minutes = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return hours * 60 + minutes
}

private fun formatMinutes(total: Int): String =
    if (total >= MINUTES_PER_DAY) END_OF_DAY else "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
