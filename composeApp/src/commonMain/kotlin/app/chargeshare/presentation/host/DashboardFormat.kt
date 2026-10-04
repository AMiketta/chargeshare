package app.chargeshare.presentation.host

import app.chargeshare.shared.model.DailyEarnings
import kotlinx.datetime.LocalDate

/** Tageszeit für die Begrüßung im Host-Dashboard. */
enum class DayPart { MORNING, DAY, EVENING }

/** Morgen bis 10:59 Uhr, Tag bis 17:59 Uhr, danach (auch nachts) Abend. */
fun dayPartFor(hour: Int): DayPart = when (hour) {
    in MORNING_START until DAY_START -> DayPart.MORNING
    in DAY_START until EVENING_START -> DayPart.DAY
    else -> DayPart.EVENING
}

/**
 * Höhenanteile (0..1) der Balken, normiert auf den größten Tageswert.
 *
 * Tage ohne Einnahmen bekommen nur einen kleinen Stummel ([EMPTY_BAR]), kleine Werte mindestens [MIN_BAR],
 * damit jeder Balken sichtbar bleibt. Ohne Einnahmen in der ganzen Woche bleiben alle Balken Stummel.
 */
fun barFractions(amounts: List<Double>): List<Float> {
    val max = amounts.maxOrNull() ?: return emptyList()
    return amounts.map { amount ->
        when {
            amount <= 0.0 || max <= 0.0 -> EMPTY_BAR
            else -> (amount / max).toFloat().coerceAtLeast(MIN_BAR)
        }
    }
}

/** Index des heutigen Tages in [week] (`YYYY-MM-DD`), `-1` wenn „heute“ nicht in der Woche liegt oder unbekannt ist. */
fun todayIndex(week: List<DailyEarnings>, today: LocalDate?): Int =
    if (today == null) -1 else week.indexOfFirst { it.date == today.toString() }

/** `Andreas Miketta` → `Andreas M.`; Klammer-Zusätze werden vor dem Kürzen ignoriert. */
fun shortName(fullName: String): String {
    val nameWithoutParentheticalParts = fullName.replace(Regex("\\([^)]*\\)"), " ")
    val parts = nameWithoutParentheticalParts.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return if (parts.size < 2) parts.firstOrNull().orEmpty() else "${parts.first()} ${parts.last().first().uppercase()}."
}

private const val MORNING_START = 5
private const val DAY_START = 11
private const val EVENING_START = 18
const val EMPTY_BAR = 0.04f
const val MIN_BAR = 0.1f
