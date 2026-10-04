package app.chargeshare.server

import app.chargeshare.server.services.CompletedBooking
import app.chargeshare.server.services.EarningsCalculator
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Reine Rechenlogik des Host-Dashboards, ohne Datenbank. */
class EarningsCalculatorTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    // Mittwoch, 14. Oktober 2026, 12:00 Uhr deutscher Zeit (Woche: Mo 12.10. bis So 18.10.)
    private val now = Instant.parse("2026-10-14T10:00:00Z")

    private fun booking(start: String, amount: Double, kwh: Double = 0.0) = CompletedBooking(Instant.parse(start), amount, kwh)

    @Test
    fun emptyHostHasZeroValuesAndSevenWeekDays() {
        val result = EarningsCalculator.calculate(emptyList(), 0.0, now, berlin)
        assertEquals(0.0, result.month)
        assertEquals(0.0, result.previousMonth)
        assertNull(result.monthChangePct)
        assertEquals(listOf("2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-16", "2026-10-17", "2026-10-18"), result.week.map { it.date })
        assertEquals(List(7) { 0.0 }, result.week.map { it.amount })
    }

    @Test
    fun splitsMonthPreviousMonthAndTotal() {
        val bookings = listOf(
            booking("2026-10-14T08:00:00Z", 10.00, 25.0),
            booking("2026-10-02T08:00:00Z", 5.50, 14.5),
            booking("2026-09-20T08:00:00Z", 12.00, 30.0),
            booking("2026-08-01T08:00:00Z", 100.00, 250.0),
        )
        val result = EarningsCalculator.calculate(bookings, 4.5, now, berlin)
        assertEquals(127.5, result.total)
        assertEquals(4, result.sessions)
        assertEquals(15.5, result.month)
        assertEquals(2, result.monthSessions)
        assertEquals(39.5, result.monthKwh)
        assertEquals(319.5, result.kwh)
        assertEquals(12.0, result.previousMonth)
        assertEquals(29.2, result.monthChangePct, "(15,50 − 12,00) / 12,00 = +29,2 %")
        assertEquals(4.5, result.avgRating)
    }

    @Test
    fun decreaseIsNegativeAndMissingBaselineIsNull() {
        assertEquals(-50.0, EarningsCalculator.changePercent(5.0, 10.0))
        assertEquals(0.0, EarningsCalculator.changePercent(10.0, 10.0))
        assertNull(EarningsCalculator.changePercent(5.0, 0.0))
    }

    @Test
    fun weekCoversMondayToSundayOfCurrentWeekOnly() {
        val bookings = listOf(
            booking("2026-10-11T10:00:00Z", 1.0), // Sonntag davor
            booking("2026-10-12T10:00:00Z", 2.0), // Montag
            booking("2026-10-12T15:00:00Z", 3.0), // Montag
            booking("2026-10-14T10:00:00Z", 4.0), // Mittwoch
            booking("2026-10-18T10:00:00Z", 5.0), // Sonntag
            booking("2026-10-19T10:00:00Z", 6.0), // Montag danach
        )
        val week = EarningsCalculator.calculate(bookings, 0.0, now, berlin).week
        assertEquals(listOf(5.0, 0.0, 4.0, 0.0, 0.0, 0.0, 5.0), week.map { it.amount })
    }

    @Test
    fun daysFollowTheRequestedTimeZone() {
        // Sonntag 23:30 UTC ist in Berlin (UTC+2) schon Montag 01:30 – und in Los Angeles (UTC−7) noch Sonntag 16:30.
        val sundayNight = listOf(booking("2026-10-11T23:30:00Z", 7.0))
        val inBerlin = EarningsCalculator.calculate(sundayNight, 0.0, now, berlin).week
        assertEquals(7.0, inBerlin.first().amount, "Montag in Berlin")
        val inLosAngeles = EarningsCalculator.calculate(sundayNight, 0.0, now, ZoneId.of("America/Los_Angeles")).week
        assertEquals(0.0, inLosAngeles.sumOf { it.amount }, "Sonntag der Vorwoche")
    }

    @Test
    fun monthBoundaryUsesTheZone() {
        // 30. September 22:30 UTC ist in Berlin bereits der 1. Oktober.
        val boundary = listOf(booking("2026-09-30T22:30:00Z", 9.0))
        assertEquals(9.0, EarningsCalculator.calculate(boundary, 0.0, now, berlin).month)
        assertEquals(9.0, EarningsCalculator.calculate(boundary, 0.0, now, ZoneId.of("UTC")).previousMonth)
    }

    @Test
    fun januaryComparesWithDecemberOfPreviousYear() {
        val january = Instant.parse("2027-01-10T10:00:00Z")
        val result = EarningsCalculator.calculate(listOf(booking("2026-12-20T10:00:00Z", 8.0), booking("2027-01-05T10:00:00Z", 12.0)), 0.0, january, berlin)
        assertEquals(8.0, result.previousMonth)
        assertEquals(12.0, result.month)
        assertEquals(50.0, result.monthChangePct)
    }
}
