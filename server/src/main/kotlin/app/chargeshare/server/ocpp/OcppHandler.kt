package app.chargeshare.server.ocpp

import app.chargeshare.server.db.Bookings
import app.chargeshare.server.db.ChargingSessions
import app.chargeshare.server.db.Db
import app.chargeshare.server.db.Wallboxes
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

private const val CALL = 2
private const val CALL_RESULT = 3
private const val CALL_ERROR = 4

@Serializable private data class BootReq(val chargePointModel: String = "", val chargePointVendor: String = "")
@Serializable private data class StartTxReq(val connectorId: Int = 0, val idTag: String = "", val meterStart: Int = 0)
@Serializable private data class StopTxReq(val meterStop: Int = 0, val transactionId: Int = 0, val reason: String? = null)
@Serializable private data class StatusReq(val connectorId: Int = 0, val status: String = "")

/**
 * OCPP 1.6J-Nachrichtenverarbeitung (Go: `internal/ocpp/handler.go`).
 * Unterstuetzt: BootNotification, Heartbeat, Authorize, StartTransaction, StopTransaction, MeterValues,
 * StatusNotification. Antworten sind OCPP-JSON-Frames (`[3, id, {...}]` bzw. `[4, id, code, desc, {}]`).
 */
class OcppHandler(private val db: Db) {
    private val log = LoggerFactory.getLogger(OcppHandler::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val random = SecureRandom()

    /** Verarbeitet einen eingehenden Frame von Ladepunkt [cpId]; `null` = keine Antwort (z. B. CallResult). */
    suspend fun handle(cpId: String, frame: String): String? {
        val arr = runCatching { json.parseToJsonElement(frame).jsonArray }.getOrNull()
        if (arr == null || arr.size < 3) {
            log.warn("ocpp[{}]: invalid frame ignored", cpId)
            return null
        }
        val type = arr[0].jsonPrimitive.intOrNull
        if (type != CALL) return null // Antworten auf eigene Kommandos werden ignoriert
        if (arr.size < 4) return null
        val uniqueId = arr[1].jsonPrimitive.contentOrNull ?: return null
        val action = arr[2].jsonPrimitive.contentOrNull ?: return callError(uniqueId, "ProtocolError", "missing action")
        val payload: JsonElement = arr[3]

        return try {
            when (action) {
                "BootNotification" -> boot(cpId, uniqueId, payload)
                else -> if (!known(cpId)) {
                    callError(uniqueId, "SecurityError", "chargepoint $cpId is not registered (set ocpp_id on a wallbox)")
                } else when (action) {
                    "Heartbeat" -> result(uniqueId, buildJsonObject { put("currentTime", now()) })
                    "Authorize" -> {
                        // MVP wie im Go-Backend: jeder idTag wird akzeptiert
                        result(uniqueId, buildJsonObject { put("idTagInfo", buildJsonObject { put("status", "Accepted") }) })
                    }
                    "StartTransaction" -> startTransaction(cpId, uniqueId, payload)
                    "StopTransaction" -> stopTransaction(cpId, uniqueId, payload)
                    "MeterValues" -> {
                        log.debug("ocpp[{}]: MeterValues {}", cpId, payload)
                        result(uniqueId, JsonObject(emptyMap()))
                    }
                    "StatusNotification" -> statusNotification(cpId, uniqueId, payload)
                    else -> callError(uniqueId, "NotImplemented", "action $action not implemented")
                }
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            callError(uniqueId, "FormationViolation", "invalid $action payload")
        } catch (e: IllegalArgumentException) {
            callError(uniqueId, "FormationViolation", "invalid $action payload")
        } catch (e: Exception) {
            log.error("ocpp[$cpId]: $action failed", e)
            callError(uniqueId, "InternalError", "could not process $action")
        }
    }

    private suspend fun known(cpId: String): Boolean =
        db.tx { !Wallboxes.select(Wallboxes.id).where { Wallboxes.ocppId eq cpId }.empty() }

    private suspend fun boot(cpId: String, uniqueId: String, payload: JsonElement): String {
        val req = json.decodeFromJsonElement(BootReq.serializer(), payload)
        val updated = db.tx {
            Wallboxes.update({ Wallboxes.ocppId eq cpId }) {
                it[isOnline] = true
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                if (req.chargePointModel.isNotBlank()) it[model] = req.chargePointModel
                if (req.chargePointVendor.isNotBlank()) it[manufacturer] = req.chargePointVendor
            }
        }
        // Unbekannte Ladepunkte werden abgelehnt (das Go-Backend akzeptierte jeden; hier bewusst strenger)
        val status = if (updated > 0) "Accepted" else "Rejected"
        return result(uniqueId, buildJsonObject {
            put("currentTime", now())
            put("interval", 300)
            put("status", status)
        })
    }

    private suspend fun startTransaction(cpId: String, uniqueId: String, payload: JsonElement): String {
        val req = json.decodeFromJsonElement(StartTxReq.serializer(), payload)
        val txId = 10_000_000 + random.nextInt(90_000_000)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        db.tx {
            // Buchung der Wallbox mit diesem ocpp_id, die jetzt laeuft (confirmed/active)
            val bookingId = (Bookings innerJoin Wallboxes).select(Bookings.id)
                .where {
                    (Wallboxes.ocppId eq cpId) and (Bookings.status inList listOf("confirmed", "active")) and
                        (Bookings.startTime lessEq now) and (Bookings.endTime greaterEq now)
                }.orderBy(Bookings.startTime to SortOrder.ASC).limit(1).singleOrNull()?.get(Bookings.id)
            ChargingSessions.insert {
                it[id] = Uuid.random()
                it[ChargingSessions.bookingId] = bookingId
                it[ocppId] = cpId
                it[transactionId] = txId
                it[meterStart] = req.meterStart
                it[startedAt] = now
                it[createdAt] = now
            }
            if (bookingId != null) {
                Bookings.update({ Bookings.id eq bookingId }) {
                    it[status] = "active"
                    it[ocppTransactionId] = txId
                    it[updatedAt] = now
                }
            }
        }
        log.info("ocpp[{}]: transaction {} started (meterStart={})", cpId, txId, req.meterStart)
        return result(uniqueId, buildJsonObject {
            put("transactionId", txId)
            put("idTagInfo", buildJsonObject { put("status", "Accepted") })
        })
    }

    private suspend fun stopTransaction(cpId: String, uniqueId: String, payload: JsonElement): String {
        val req = json.decodeFromJsonElement(StopTxReq.serializer(), payload)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        db.tx {
            val session = ChargingSessions.selectAll()
                .where { (ChargingSessions.transactionId eq req.transactionId) and (ChargingSessions.ocppId eq cpId) }
                .singleOrNull()
            ChargingSessions.update({ (ChargingSessions.transactionId eq req.transactionId) and (ChargingSessions.ocppId eq cpId) }) {
                it[meterStop] = req.meterStop
                it[stoppedAt] = now
                it[stopReason] = req.reason
            }
            val bookingId = session?.get(ChargingSessions.bookingId)
            val meterStart = session?.get(ChargingSessions.meterStart)
            if (bookingId != null && meterStart != null) {
                val kwh = BigDecimal(req.meterStop - meterStart).divide(BigDecimal(1000), 3, RoundingMode.HALF_UP)
                val price = Bookings.select(Bookings.pricePerKwh).where { Bookings.id eq bookingId }.single()[Bookings.pricePerKwh]
                Bookings.update({ Bookings.id eq bookingId }) {
                    it[status] = "completed"
                    it[actualKwh] = kwh
                    it[totalAmount] = kwh.multiply(price).setScale(2, RoundingMode.HALF_UP)
                    it[updatedAt] = now
                }
            }
        }
        log.info("ocpp[{}]: transaction {} stopped (meterStop={})", cpId, req.transactionId, req.meterStop)
        return result(uniqueId, JsonObject(emptyMap()))
    }

    private suspend fun statusNotification(cpId: String, uniqueId: String, payload: JsonElement): String {
        val req = json.decodeFromJsonElement(StatusReq.serializer(), payload)
        val online = req.status in setOf("Available", "Preparing", "Charging")
        db.tx {
            Wallboxes.update({ Wallboxes.ocppId eq cpId }) {
                it[isOnline] = online
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
        log.info("ocpp[{}]: status {} (connector {})", cpId, req.status, req.connectorId)
        return result(uniqueId, JsonObject(emptyMap()))
    }

    private fun now(): String = DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now())

    private fun result(uniqueId: String, payload: JsonObject): String =
        JsonArray(listOf(JsonPrimitive(CALL_RESULT), JsonPrimitive(uniqueId), payload)).toString()

    private fun callError(uniqueId: String, code: String, desc: String): String =
        JsonArray(listOf(JsonPrimitive(CALL_ERROR), JsonPrimitive(uniqueId), JsonPrimitive(code), JsonPrimitive(desc), JsonObject(emptyMap()))).toString()
}
