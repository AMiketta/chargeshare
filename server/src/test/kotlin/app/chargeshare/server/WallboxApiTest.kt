package app.chargeshare.server

import app.chargeshare.shared.api.ApiException
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.TimeSlot
import app.chargeshare.shared.model.UpdateWallboxRequest
import app.chargeshare.shared.model.WallboxSearch
import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WallboxApiTest {

    @Test
    fun crudStatusAndOwnership() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val other = registerUser("other@example.com")

        val wb = host.api.createWallbox(newWallbox("Marktplatz-Box"))
        assertEquals("Marktplatz-Box", wb.name)
        assertEquals(false, wb.isOnline)
        assertEquals(30, wb.minDurationMin)
        assertEquals(480, wb.maxDurationMin)
        assertEquals(11.0, wb.powerKw)
        assertEquals(0.40, wb.pricePerKwh)
        assertEquals(14, wb.availability.values.sumOf { it.size })

        // oeffentlich lesbar (ohne Token)
        assertEquals(wb.id, raw.getAuth("/api/v1/wallboxes/${wb.id}", null).let { errOrId(it.text()) })

        val upd = host.api.updateWallbox(wb.id, UpdateWallboxRequest(pricePerKwh = 0.55, name = "Neu"))
        assertEquals(0.55, upd.pricePerKwh)
        assertEquals("Neu", upd.name)
        assertEquals(11.0, upd.powerKw) // unveraendert

        assertEquals(403, assertFailsWith<ApiException> { other.api.updateWallbox(wb.id, UpdateWallboxRequest(name = "x")) }.status)
        assertEquals(403, assertFailsWith<ApiException> { other.api.setWallboxOnline(wb.id, true) }.status)
        assertEquals(403, assertFailsWith<ApiException> { other.api.deleteWallbox(wb.id) }.status)
        assertEquals(401, assertFailsWith<ApiException> { newApi().api.createWallbox(newWallbox()) }.status)

        assertTrue(host.api.setWallboxOnline(wb.id, true).isOnline)
        assertTrue(host.api.wallbox(wb.id).isOnline)

        host.api.deleteWallbox(wb.id)
        assertEquals(404, assertFailsWith<ApiException> { host.api.wallbox(wb.id) }.status)
        assertEquals(400, assertFailsWith<ApiException> { host.api.wallbox("keine-uuid") }.status)
    }

    private fun errOrId(body: String): String = Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]

    @Test
    fun createValidatesInput() = serverTest {
        val host = registerUser("host@example.com", host = true)
        for (bad in listOf(
            newWallbox().copy(name = ""),
            newWallbox().copy(lat = 91.0),
            newWallbox().copy(powerKw = 0.0),
            newWallbox().copy(pricePerKwh = -1.0),
            newWallbox().copy(availability = mapOf("monday" to listOf(TimeSlot("25:00", "26:00")))),
        )) {
            assertEquals(422, assertFailsWith<ApiException> { host.api.createWallbox(bad) }.status)
        }
    }

    @Test
    fun geoSearchWithFilters() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val markt = host.api.createWallbox(newWallbox("Markt", 53.0758, 8.8072, 11.0, 0.40, ConnectorType.TYPE2))
        val hbf = host.api.createWallbox(newWallbox("Hbf", 53.0833, 8.8137, 22.0, 0.55, ConnectorType.CCS))
        val uni = host.api.createWallbox(newWallbox("Uni", 53.1067, 8.8517, 50.0, 0.65, ConnectorType.CCS))
        val hamburg = host.api.createWallbox(newWallbox("Hamburg", 53.5511, 9.9937))

        // ohne Geo: alle, neueste zuerst, keine distance_m
        val all = newApi().api.searchWallboxes()
        assertEquals(listOf("Hamburg", "Uni", "Hbf", "Markt"), all.wallboxes.map { it.name })
        assertEquals(20, all.limit)
        assertTrue(all.wallboxes.all { it.distanceM == null })

        // Umkreis 2 km um den Marktplatz: Bremen-Boxen nach Entfernung, Hamburg nicht
        val near = newApi().api.searchWallboxes(WallboxSearch(lat = 53.0758, lng = 8.8072, radiusM = 2000.0))
        assertEquals(listOf("Markt", "Hbf"), near.wallboxes.map { it.name })
        assertTrue(near.wallboxes[0].distanceM!! < 5.0)
        assertTrue(near.wallboxes[1].distanceM!! in 800.0..1100.0, "Hbf ~0,95 km: ${near.wallboxes[1].distanceM}")

        // Default-Radius 10 km: Uni (~4,7 km) dabei
        val def = newApi().api.searchWallboxes(WallboxSearch(lat = 53.0758, lng = 8.8072))
        assertEquals(listOf("Markt", "Hbf", "Uni"), def.wallboxes.map { it.name })

        // Filter
        assertEquals(listOf("Uni", "Hbf"), newApi().api.searchWallboxes(WallboxSearch(connector = ConnectorType.CCS)).wallboxes.map { it.name })
        assertEquals(listOf("Uni"), newApi().api.searchWallboxes(WallboxSearch(connector = ConnectorType.CCS, minPowerKw = 30.0)).wallboxes.map { it.name })
        assertEquals(listOf("Markt"), newApi().api.searchWallboxes(WallboxSearch(lat = 53.0758, lng = 8.8072, maxPricePerKwh = 0.45)).wallboxes.map { it.name })
        // Paging
        val page = newApi().api.searchWallboxes(WallboxSearch(limit = 2, offset = 1))
        assertEquals(listOf("Uni", "Hbf"), page.wallboxes.map { it.name })
        assertEquals(1, page.offset)
        assertEquals(422, assertFailsWith<ApiException> { newApi().api.searchWallboxes(WallboxSearch(lat = 100.0, lng = 8.0)) }.status)
        assertNotNull(markt); assertNotNull(hbf); assertNotNull(uni); assertNotNull(hamburg)
    }

    @Test
    fun availabilityAndSlots() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val other = registerUser("other@example.com")
        val wb = host.api.createWallbox(newWallbox())
        assertEquals(weekPlan(), newApi().api.wallboxAvailability(wb.id))

        // Montag 09:00-10:30 nur, mit Kurzschluessel "mon" (Go: Praefix-Match)
        val updated = host.api.updateWallboxAvailability(wb.id, mapOf("mon" to listOf(TimeSlot("09:00", "10:30"))))
        assertEquals(1, updated.size)
        assertEquals(403, assertFailsWith<ApiException> { other.api.updateWallboxAvailability(wb.id, emptyMap()) }.status)

        var monday = LocalDate.now(ZoneOffset.UTC).plusDays(1)
        while (monday.dayOfWeek.name != "MONDAY") monday = monday.plusDays(1)
        val slots = newApi().api.wallboxSlots(wb.id, monday.toString())
        assertEquals(monday.toString(), slots.date)
        assertEquals(listOf(TimeSlot("09:00", "09:30"), TimeSlot("09:30", "10:00"), TimeSlot("10:00", "10:30")), slots.slots)
        assertEquals(emptyList(), slots.locked)
        // Dienstag: nichts konfiguriert
        assertEquals(emptyList(), newApi().api.wallboxSlots(wb.id, monday.plusDays(1).toString()).slots)
        assertEquals(400, assertFailsWith<ApiException> { newApi().api.wallboxSlots(wb.id, "morgen") }.status)
        assertEquals(404, assertFailsWith<ApiException> { newApi().api.wallboxSlots("00000000-0000-0000-0000-000000000000", monday.toString()) }.status)
        // ohne Datum: heute (Antwort-Datum gesetzt)
        assertEquals(LocalDate.now(ZoneOffset.UTC).toString(), newApi().api.wallboxSlots(wb.id).date)

        assertEquals(0, newApi().api.wallboxReviews(wb.id).total)
    }

    @Suppress("unused")
    private val unused = HttpStatusCode.OK
}
