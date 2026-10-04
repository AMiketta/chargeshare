package app.chargeshare.server.services

import app.chargeshare.shared.model.DailyEarnings
import app.chargeshare.shared.model.HostEarnings
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Eine abgeschlossene Buchung, wie sie in die Einnahmen eingeht. */
internal data class CompletedBooking(val startTime: Instant, val amount: Double, val kwh: Double)

/**
 * Rechnet abgeschlossene Buchungen zu den Kennzahlen des Host-Dashboards zusammen (reine Funktion, ohne Datenbank).
 * Tage, Monate und Wochen richten sich nach dem Beginn der Buchung in [zone].
 */
internal object EarningsCalculator {
    private const val DAYS_PER_WEEK = 7
    private const val CENT_FACTOR = 100.0
    private const val PERCENT = 100.0
    private const val PERCENT_FACTOR = 10.0
    private const val KWH_FACTOR = 10.0

    /**
     * @param avgRating bereits berechneter Bewertungsdurchschnitt des Hosts (gehört nicht zu den Buchungen)
     * @param now Zeitpunkt „jetzt“; bestimmt laufenden Monat, Vormonat und laufende Woche
     */
    fun calculate(bookings: List<CompletedBooking>, avgRating: Double, now: Instant, zone: ZoneId): HostEarnings {
        val today = now.atZone(zone).toLocalDate()
        val thisMonth = YearMonth.from(today)
        val previousMonth = thisMonth.minusMonths(1)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dated = bookings.map { it to it.startTime.atZone(zone).toLocalDate() }

        val inThisMonth = dated.filter { YearMonth.from(it.second) == thisMonth }.map { it.first }
        val monthTotal = inThisMonth.sumOf { it.amount }
        val previousTotal = dated.filter { YearMonth.from(it.second) == previousMonth }.sumOf { it.first.amount }
        val week = (0 until DAYS_PER_WEEK).map { offset ->
            val day = monday.plusDays(offset.toLong())
            DailyEarnings(date = day.toString(), amount = roundCents(dated.filter { it.second == day }.sumOf { it.first.amount }))
        }
        return HostEarnings(
            total = roundCents(bookings.sumOf { it.amount }),
            month = roundCents(monthTotal),
            sessions = bookings.size,
            avgRating = avgRating,
            kwh = roundKwh(bookings.sumOf { it.kwh }),
            monthSessions = inThisMonth.size,
            monthKwh = roundKwh(inThisMonth.sumOf { it.kwh }),
            previousMonth = roundCents(previousTotal),
            monthChangePct = changePercent(monthTotal, previousTotal),
            week = week,
        )
    }

    /** Prozentuale Veränderung auf eine Nachkommastelle; `null`, wenn der Vergleichswert 0 ist. */
    internal fun changePercent(current: Double, previous: Double): Double? {
        if (previous <= 0.0) return null
        return Math.round((current - previous) / previous * PERCENT * PERCENT_FACTOR) / PERCENT_FACTOR
    }

    private fun roundCents(value: Double): Double = Math.round(value * CENT_FACTOR) / CENT_FACTOR

    private fun roundKwh(value: Double): Double = Math.round(value * KWH_FACTOR) / KWH_FACTOR
}
