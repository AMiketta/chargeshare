package app.chargeshare.presentation.booking

import app.chargeshare.data.SLOT_MINUTES
import app.chargeshare.shared.model.Wallbox
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Geschätzte Kosten einer Buchung (Obergrenze bei Dauerladen mit voller Leistung). */
data class BookingEstimate(val kwh: Double, val totalEur: Double)

/** Reine Rechenlogik für den Buchungsflow (ohne Zustand, daher leicht testbar). */
object SlotPlanner {

    /** Anzahl direkt aufeinanderfolgender freier Slots ab [start] (0, wenn [start] nicht frei ist). */
    fun contiguousFreeSlots(start: Instant, freeStarts: Set<Instant>): Int {
        var count = 0
        var cursor = start
        while (cursor in freeStarts) {
            count++
            cursor += SLOT_MINUTES.minutes
        }
        return count
    }

    /**
     * Wählbare Buchungsdauern in Minuten (Vielfache von [SLOT_MINUTES]) für einen Start:
     * mindestens `min_duration_min`, höchstens `max_duration_min` und nur so lang, wie die Slots am Stück frei sind.
     */
    fun durationOptions(start: Instant, freeStarts: Set<Instant>, wallbox: Wallbox): List<Int> {
        val freeMinutes = contiguousFreeSlots(start, freeStarts) * SLOT_MINUTES
        val upper = minOf(freeMinutes, wallbox.maxDurationMin)
        val lower = roundUpToSlot(wallbox.minDurationMin)
        return (lower..upper step SLOT_MINUTES).toList()
    }

    /** Wie der Server: kWh = Leistung × Stunden, Betrag = kWh × Preis (auf Cent gerundet). */
    fun estimate(wallbox: Wallbox, durationMinutes: Int): BookingEstimate {
        val kwh = wallbox.powerKw * durationMinutes / MINUTES_PER_HOUR
        return BookingEstimate(kwh = kwh, totalEur = roundToCents(kwh * wallbox.pricePerKwh))
    }

    private fun roundUpToSlot(minutes: Int): Int = ((minutes + SLOT_MINUTES - 1) / SLOT_MINUTES) * SLOT_MINUTES

    private fun roundToCents(value: Double): Double = (value * CENTS_PER_EURO).roundToLong() / CENTS_PER_EURO

    private const val MINUTES_PER_HOUR = 60.0
    private const val CENTS_PER_EURO = 100.0
}
