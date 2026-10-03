package app.chargeshare.server.ocpp

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ocpp")

/**
 * `GET /ocpp/{chargepoint_id}` – WebSocket fuer OCPP 1.6J (Subprotokoll `ocpp1.6`, wie im Go-Backend
 * ist es optional). Keine HTTP-Authentifizierung: Ladepunkte werden ueber ihre `ocpp_id` zugeordnet.
 */
fun Route.ocppRoutes(handler: OcppHandler) {
    route("/ocpp/{chargepoint_id}") {
        webSocket(protocol = "ocpp1.6") { serve(handler) }
        webSocket { serve(handler) }
    }
}

private suspend fun DefaultWebSocketServerSession.serve(handler: OcppHandler) {
    val cpId = call.parameters["chargepoint_id"] ?: return
    log.info("chargepoint connected: {}", cpId)
    try {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val reply = handler.handle(cpId, frame.readText())
            if (reply != null) send(Frame.Text(reply))
        }
    } finally {
        log.info("chargepoint disconnected: {}", cpId)
    }
}
