package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.CreateBookingRequest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Dashboard-Werte von `GET /host/earnings`: kWh, Vormonat, Prozent, Wochenverlauf, Zeitzone. */
class HostEarningsApiTest {
    private val today: LocalDate = LocalDate.now(ZoneOffset.UTC)

    private fun at(day: LocalDate, hour: Int) = day.atTime(LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC)

    /** Legt eine abgeschlossene Buchung an und verschiebt sie auf [day]; [index] hält die Slots überschneidungsfrei. */
    private suspend fun ServerScope.completedBooking(
        driver: TestClient,
        wallboxId: String,
        day: LocalDate,
        amount: Double,
        kwh: Double,
        index: Int,
    ) {
        val booking = driver.api.createBooking(CreateBookingRequest(wallboxId, inFuture(index + 1L, 10), inFuture(index + 1L, 12))).booking!!
        forceBookingStatus(booking.id, "completed")
        forceBookingTotals(booking.id, amount, kwh)
        moveBooking(booking.id, at(day, 8), at(day, 10))
    }

    @Test
    fun hostWithoutSessionsGetsEmptyDashboard() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val earnings = host.api.hostEarnings()
        assertEquals(0.0, earnings.month)
        assertEquals(0.0, earnings.kwh)
        assertNull(earnings.monthChangePct)
        assertEquals(7, earnings.week.size)
        assertEquals(today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString(), earnings.week.first().date)
    }

    @Test
    fun aggregatesKwhMonthPreviousMonthAndWeek() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox()).also { host.api.setWallboxOnline(it.id, true) }
        val driver = registerUser("fahrer@example.com")
        val previousMonthDay = today.withDayOfMonth(1).minusMonths(1).withDayOfMonth(15)
        completedBooking(driver, wallbox.id, today, amount = 10.0, kwh = 25.0, index = 0)
        completedBooking(driver, wallbox.id, previousMonthDay, amount = 8.0, kwh = 20.0, index = 1)
        completedBooking(driver, wallbox.id, previousMonthDay.minusMonths(3), amount = 100.0, kwh = 250.0, index = 2)

        val earnings = host.api.hostEarnings()
        assertEquals(118.0, earnings.total)
        assertEquals(3, earnings.sessions)
        assertEquals(295.0, earnings.kwh)
        assertEquals(8.0, earnings.previousMonth)
        // Liegt „heute“ im Monat des ersten Datums, zählt die erste Buchung zum Monat – auch am Monatsersten.
        assertEquals(10.0, earnings.month)
        assertEquals(1, earnings.monthSessions)
        assertEquals(25.0, earnings.monthKwh)
        assertEquals(25.0, earnings.monthChangePct, "(10 − 8) / 8 = +25 %")
        assertEquals(10.0, earnings.week.single { it.date == today.toString() }.amount)
        assertEquals(10.0, earnings.week.sumOf { it.amount })
    }

    @Test
    fun otherHostsBookingsAreNotCounted() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val other = registerUser("anderer@example.com", host = true)
        val foreign = other.api.createWallbox(newWallbox()).also { other.api.setWallboxOnline(it.id, true) }
        val driver = registerUser("fahrer@example.com")
        completedBooking(driver, foreign.id, today, amount = 10.0, kwh = 25.0, index = 0)

        assertEquals(0.0, host.api.hostEarnings().total)
        assertEquals(10.0, other.api.hostEarnings().total)
    }

    @Test
    fun timeZoneMovesTheDayBucket() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox()).also { host.api.setWallboxOnline(it.id, true) }
        val driver = registerUser("fahrer@example.com")
        val booking = driver.api.createBooking(CreateBookingRequest(wallbox.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        forceBookingStatus(booking.id, "completed")
        forceBookingTotals(booking.id, 6.0, 15.0)
        // 23:30 UTC am heutigen Tag: in Tokio (UTC+9) schon der nächste Tag
        moveBooking(booking.id, today.atTime(23, 30).toInstant(ZoneOffset.UTC), today.plusDays(1).atTime(1, 30).toInstant(ZoneOffset.UTC))

        assertEquals(1, host.api.hostEarnings(from = today.toString(), to = today.toString()).sessions)
        assertEquals(0, host.api.hostEarnings(from = today.toString(), to = today.toString(), timeZone = "Asia/Tokyo").sessions)
        assertEquals(1, host.api.hostEarnings(from = today.plusDays(1).toString(), to = today.plusDays(1).toString(), timeZone = "Asia/Tokyo").sessions)
    }

    @Test
    fun filterByDateUsesBookingStart() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox()).also { host.api.setWallboxOnline(it.id, true) }
        val driver = registerUser("fahrer@example.com")
        completedBooking(driver, wallbox.id, LocalDate.of(2025, 3, 10), amount = 4.0, kwh = 10.0, index = 0)

        assertEquals(1, host.api.hostEarnings(from = "2025-03-10", to = "2025-03-10").sessions)
        assertEquals(0, host.api.hostEarnings(from = "2025-03-11").sessions)
        assertEquals(4.0, host.api.hostEarnings(to = "2025-03-31").total)
    }

    @Test
    fun invalidTimeZoneIsRejected() = serverTest {
        val host = registerUser("host@example.com", host = true)
        assertEquals(400, assertFailsWith<ApiException> { host.api.hostEarnings(timeZone = "Mars/Olympus") }.status)
    }
}
