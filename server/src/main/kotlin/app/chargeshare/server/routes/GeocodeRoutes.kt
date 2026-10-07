package app.chargeshare.server.routes

import app.chargeshare.server.Services
import app.chargeshare.server.plugins.JWT_AUTH
import app.chargeshare.shared.model.GeocodeRequest
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * `POST /geocode`: Koordinaten zu einer Adresse; `GET /geocode/place?q=`: Ortssuche (Ort, Stadtteil, PLZ) für das Suchzentrum.
 * Beides nur mit Login und je Nutzer ratenbegrenzt.
 */
fun Route.geocodeRoutes(s: Services) {
    authenticate(JWT_AUTH) {
        post("/geocode") { call.respond(s.geocoding.lookup(call.userId(), call.receive<GeocodeRequest>())) }
        get("/geocode/place") { call.respond(s.geocoding.searchPlace(call.userId(), call.request.queryParameters["q"])) }
    }
}
