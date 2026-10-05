package app.chargeshare.server.routes

import app.chargeshare.server.Services
import app.chargeshare.server.plugins.JWT_AUTH
import app.chargeshare.shared.model.RegisterDeviceRequest
import app.chargeshare.shared.model.UnregisterDeviceRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Push-Gerätetokens: `POST|DELETE /devices`. */
fun Route.deviceRoutes(s: Services) {
    authenticate(JWT_AUTH) {
        route("/devices") {
            post {
                s.pushDevices.register(call.userId(), call.receive<RegisterDeviceRequest>())
                call.respond(HttpStatusCode.NoContent)
            }
            delete {
                s.pushDevices.unregister(call.userId(), call.receive<UnregisterDeviceRequest>())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
