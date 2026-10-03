package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.TimeSlot
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Ein Zeitfenster darf um `24:00` enden (nötig, wenn lokale Zeiten in UTC umgerechnet über Mitternacht reichen). */
class AvailabilityEndOfDayTest {

    @Test
    fun endOfDayIsAcceptedAndYieldsTheLastSlot() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox())
        var monday = LocalDate.now(ZoneOffset.UTC).plusDays(1)
        while (monday.dayOfWeek.name != "MONDAY") monday = monday.plusDays(1)
        host.api.updateWallboxAvailability(wallbox.id, mapOf("monday" to listOf(TimeSlot("22:30", "24:00"))))
        val slots = host.api.wallboxSlots(wallbox.id, monday.toString()).slots
        assertEquals(listOf("22:30", "23:00", "23:30"), slots.map { it.start })
    }

    @Test
    fun invalidEndsAreStillRejected() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wallbox = host.api.createWallbox(newWallbox())
        listOf(TimeSlot("24:00", "24:00"), TimeSlot("10:00", "24:30"), TimeSlot("10:00", "10:00"), TimeSlot("10:00", "9")).forEach { slot ->
            val error = assertFailsWith<ApiException> { host.api.updateWallboxAvailability(wallbox.id, mapOf("monday" to listOf(slot))) }
            assertEquals(422, error.status, slot.toString())
        }
    }
}
