package app.chargeshare.server

import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.CreateReviewRequest
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class OcppTest {

    private suspend fun DefaultClientWebSocketSession.call(id: String, action: String, payload: String = "{}"): JsonArray {
        send(Frame.Text("""[2,"$id","$action",$payload]"""))
        val text = (incoming.receive() as Frame.Text).readText()
        return Json.parseToJsonElement(text).jsonArray
    }

    @Test
    fun fullChargingSessionUpdatesBookingAndHostDashboard() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val driver = registerUser("driver@example.com")
        val wb = host.api.createWallbox(newWallbox(price = 0.40).copy(ocppId = "CP-1"))
        host.api.setWallboxOnline(wb.id, false)
        host.api.setWallboxOnline(wb.id, true)
        val bk = driver.api.createBooking(CreateBookingRequest(wb.id, inFuture(1, 10), inFuture(1, 12))).booking!!
        // Buchung "jetzt laufend" machen
        transaction(TestDb.database) {
            exec("UPDATE bookings SET start_time = NOW() - interval '10 minutes', end_time = NOW() + interval '1 hour' WHERE id = '${bk.id}'")
        }

        wsClient().webSocket("/ocpp/CP-1", request = { header("Sec-WebSocket-Protocol", "ocpp1.6") }) {
            val boot = call("1", "BootNotification", """{"chargePointModel":"Wallbox X","chargePointVendor":"ACME"}""")
            assertEquals(3, boot[0].jsonPrimitive.int)
            assertEquals("1", boot[1].jsonPrimitive.content)
            assertEquals("Accepted", boot[2].jsonObject["status"]!!.jsonPrimitive.content)
            assertEquals(300, boot[2].jsonObject["interval"]!!.jsonPrimitive.int)
            assertNotNull(boot[2].jsonObject["currentTime"])

            assertNotNull(call("2", "Heartbeat")[2].jsonObject["currentTime"])
            assertEquals("Accepted", call("3", "Authorize", """{"idTag":"TAG1"}""")[2].jsonObject["idTagInfo"]!!.jsonObject["status"]!!.jsonPrimitive.content)

            val start = call("4", "StartTransaction", """{"connectorId":1,"idTag":"TAG1","meterStart":1000,"timestamp":"2026-01-01T10:00:00Z"}""")
            val txId = start[2].jsonObject["transactionId"]!!.jsonPrimitive.int
            assertTrue(txId in 10_000_000..99_999_999)
            assertEquals(BookingStatus.ACTIVE, driver.api.booking(bk.id).status)
            assertEquals(txId, driver.api.booking(bk.id).ocppTransactionId)

            assertEquals(3, call("5", "MeterValues", """{"connectorId":1,"transactionId":$txId,"meterValue":[]}""")[0].jsonPrimitive.int)
            assertEquals(3, call("6", "StatusNotification", """{"connectorId":1,"errorCode":"NoError","status":"Charging"}""")[0].jsonPrimitive.int)

            // Unbekannte Aktion + kaputter Frame
            assertEquals("NotImplemented", call("7", "DataTransfer")[2].jsonPrimitive.content)
            send(Frame.Text("kein json"))
            assertEquals(3, call("8", "Heartbeat")[0].jsonPrimitive.int) // naechste Antwort gehoert zum Heartbeat
            val bad = call("9", "StartTransaction", """{"connectorId":"x"}""")
            assertEquals(4, bad[0].jsonPrimitive.int)
            assertEquals("FormationViolation", bad[2].jsonPrimitive.content)

            call("10", "StopTransaction", """{"meterStop":6000,"timestamp":"2026-01-01T11:00:00Z","transactionId":$txId,"reason":"EVDisconnected"}""")
        }

        // 5 kWh * 0,40 = 2,00 EUR, abgeschlossen -> bewertbar, Einnahmen sichtbar
        val done = driver.api.booking(bk.id)
        assertEquals(BookingStatus.COMPLETED, done.status)
        assertEquals(5.0, done.actualKwh)
        assertEquals(2.0, done.totalAmount)
        driver.api.createReview(CreateReviewRequest(bk.id, 5, "Top"))
        assertEquals(2.0, host.api.hostEarnings().total)
        val sessions = host.api.hostSessions().sessions
        assertEquals(1, sessions.size)
        assertEquals(1000, sessions[0].meterStart)
        assertEquals(6000, sessions[0].meterStop)
        assertEquals("EVDisconnected", sessions[0].stopReason)
        assertEquals("driver", sessions[0].driver!!.name)
        // Status Charging -> online
        assertTrue(newApi().api.wallbox(wb.id).isOnline)
        assertEquals("Wallbox X", newApi().api.wallbox(wb.id).model)
    }

    @Test
    fun unknownChargepointsAreRejectedAndStatusTogglesOnline() = serverTest {
        val host = registerUser("host@example.com", host = true)
        val wb = host.api.createWallbox(newWallbox().copy(ocppId = "CP-2"))

        // ohne Subprotokoll verbinden ist erlaubt
        wsClient().webSocket("/ocpp/UNBEKANNT") {
            assertEquals("Rejected", call("1", "BootNotification", """{"chargePointModel":"M","chargePointVendor":"V"}""")[2].jsonObject["status"]!!.jsonPrimitive.content)
            val sec = call("2", "Heartbeat")
            assertEquals(4, sec[0].jsonPrimitive.int)
            assertEquals("SecurityError", sec[2].jsonPrimitive.content)
        }

        wsClient().webSocket("/ocpp/CP-2") {
            assertEquals("Accepted", call("1", "BootNotification", """{"chargePointModel":"M","chargePointVendor":"V"}""")[2].jsonObject["status"]!!.jsonPrimitive.content)
            assertTrue(newApi().api.wallbox(wb.id).isOnline)
            call("2", "StatusNotification", """{"connectorId":1,"errorCode":"GroundFailure","status":"Faulted"}""")
            assertEquals(false, newApi().api.wallbox(wb.id).isOnline)
            call("3", "StatusNotification", """{"connectorId":1,"errorCode":"NoError","status":"Available"}""")
            assertTrue(newApi().api.wallbox(wb.id).isOnline)
            // StartTransaction ohne passende Buchung: Session ohne booking_id, kein Fehler
            val tx = call("4", "StartTransaction", """{"connectorId":1,"idTag":"T","meterStart":0,"timestamp":"2026-01-01T10:00:00Z"}""")
            assertEquals(3, tx[0].jsonPrimitive.int)
            call("5", "StopTransaction", """{"meterStop":10,"timestamp":"2026-01-01T10:10:00Z","transactionId":${tx[2].jsonObject["transactionId"]!!.jsonPrimitive.int}}""")
        }
    }
}
