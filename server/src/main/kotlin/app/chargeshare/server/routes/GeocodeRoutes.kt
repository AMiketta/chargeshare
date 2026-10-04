package app.chargeshare.server.routes

import app.chargeshare.server.Services
import app.chargeshare.server.plugins.JWT_AUTH
import app.chargeshare.shared.model.GeocodeRequest
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/** `POST /geocode`: Koordinaten zu einer Adresse (nur mit Login, je Nutzer ratenbegrenzt). */
fun Route.geocodeRoutes(s: Services) {
    authenticate(JWT_AUTH) {
        post("/geocode") { call.respond(s.geocoding.lookup(call.userId(), call.receive<GeocodeRequest>())) }
    }
}
