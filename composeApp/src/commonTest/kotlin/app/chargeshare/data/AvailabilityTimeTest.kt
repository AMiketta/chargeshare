package app.chargeshare.data

import app.chargeshare.shared.model.TimeSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class AvailabilityTimeTest {
    private val berlin = TimeZone.of("Europe/Berlin")
    private val summer = Instant.parse("2026-07-15T10:00:00Z")
    private val winter = Instant.parse("2026-12-15T10:00:00Z")

    private fun plan(day: String, start: String, end: String) = mapOf(day to listOf(TimeSlot(start, end)))

    @Test
    fun utcToLocalUsesTheOffsetOfTheReferenceInstant() {
        assertEquals(plan("monday", "08:00", "16:30"), plan("monday", "06:00", "14:30").utcToLocal(berlin, summer))
        assertEquals(plan("monday", "07:00", "15:30"), plan("monday", "06:00", "14:30").utcToLocal(berlin, winter))
    }

    @Test
    fun localToUtcIsTheInverse() {
        val local = mapOf("monday" to listOf(TimeSlot("09:00", "12:00"), TimeSlot("14:00", "18:00")), "friday" to listOf(TimeSlot("10:00", "11:00")))
        for (reference in listOf(summer, winter)) {
            assertEquals(local, local.localToUtc(berlin, reference).utcToLocal(berlin, reference))
        }
        val utc = local.localToUtc(berlin, summer)
        assertEquals(listOf(TimeSlot("07:00", "10:00"), TimeSlot("12:00", "16:00")), utc["monday"])
        assertEquals(listOf(TimeSlot("08:00", "09:00")), utc["friday"])
    }

    @Test
    fun windowCrossingMidnightIsSplitAcrossDays() {
        // Montag 00:30–02:00 Uhr (MEZ) = Sonntag 23:30–24:00 UTC + Montag 00:00–01:00 UTC
        assertEquals(
            mapOf("sunday" to listOf(TimeSlot("23:30", "24:00")), "monday" to listOf(TimeSlot("00:00", "01:00"))),
            plan("monday", "00:30", "02:00").localToUtc(berlin, winter),
        )
        // UTC Montag 23:00–24:00 = Dienstag 01:00–02:00 Uhr (MESZ)
        assertEquals(plan("tuesday", "01:00", "02:00"), plan("monday", "23:00", "24:00").utcToLocal(berlin, summer))
        // UTC Montag 20:00–24:00 = Montag 22:00–24:00 + Dienstag 00:00–02:00 Uhr (MESZ)
        assertEquals(
            mapOf("monday" to listOf(TimeSlot("22:00", "24:00")), "tuesday" to listOf(TimeSlot("00:00", "02:00"))),
            plan("monday", "20:00", "24:00").utcToLocal(berlin, summer),
        )
    }

    @Test
    fun sundayRollsOverToMondayAcrossTheWeekBoundary() {
        // UTC Sonntag 21:30–23:30 = Sonntag 23:30–24:00 + Montag 00:00–01:30 Uhr (MESZ)
        assertEquals(
            mapOf("sunday" to listOf(TimeSlot("23:30", "24:00")), "monday" to listOf(TimeSlot("00:00", "01:30"))),
            plan("sunday", "21:30", "23:30").utcToLocal(berlin, summer),
        )
    }

    @Test
    fun mondayEarlyMorningMovesBackToSundayInUtc() {
        assertEquals(plan("sunday", "23:00", "24:00"), plan("monday", "00:00", "01:00").localToUtc(berlin, winter))
    }

    @Test
    fun touchingWindowsAreMergedAndInvalidOnesSkipped() {
        val result = mapOf("monday" to listOf(TimeSlot("08:00", "10:00"), TimeSlot("10:00", "12:00"), TimeSlot("kaputt", "12:00"), TimeSlot("13:00", "13:00")))
            .utcToLocal(TimeZone.UTC, summer)
        assertEquals(plan("monday", "08:00", "12:00"), result)
        assertEquals(emptyMap(), mapOf("noday" to listOf(TimeSlot("08:00", "10:00"))).utcToLocal(TimeZone.UTC, summer))
    }
}
