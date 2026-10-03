package app.chargeshare.server.routes

import app.chargeshare.server.Services
import app.chargeshare.server.plugins.JWT_AUTH
import app.chargeshare.server.util.badRequest
import app.chargeshare.server.util.validationError
import app.chargeshare.shared.model.ConnectorType
import app.chargeshare.shared.model.CreateWallboxRequest
import app.chargeshare.shared.model.UpdateWallboxRequest
import app.chargeshare.shared.model.WallboxSearch
import app.chargeshare.shared.model.WallboxStatusRequest
import app.chargeshare.shared.model.WallboxStatusResponse
import app.chargeshare.shared.model.WeeklyAvailability
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

private fun ApplicationCall.searchParams(): WallboxSearch {
    val q = request.queryParameters
    val connector = q["connector"]?.takeIf { it.isNotEmpty() }?.let { v ->
        ConnectorType.entries.firstOrNull { it.name.equals(v, ignoreCase = true) }
            ?: throw validationError("connector: must be one of type2 ccs chademo type1")
    }
    return WallboxSearch(
        lat = q["lat"]?.toDoubleOrNull(),
        lng = q["lng"]?.toDoubleOrNull(),
        radiusM = q["radius"]?.toDoubleOrNull(),
        connector = connector,
        minPowerKw = q["min_power"]?.toDoubleOrNull(),
        maxPricePerKwh = q["max_price"]?.toDoubleOrNull(),
        limit = q["limit"]?.toIntOrNull(),
        offset = q["offset"]?.toIntOrNull(),
    )
}

fun Route.wallboxRoutes(s: Services) {
    route("/wallboxes") {
        // ---- oeffentlich
        get { call.respond(s.wallboxes.search(call.searchParams())) }
        get("/{id}") { call.respond(s.wallboxes.get(call.pathUuid("id", "wallbox id"))) }
        get("/{id}/availability") {
            call.respond<WeeklyAvailability>(s.wallboxes.availability(call.pathUuid("id", "wallbox id")))
        }
        get("/{id}/slots") {
            val id = call.pathUuid("id", "wallbox id")
            val dateStr = call.request.queryParameters["date"]?.takeIf { it.isNotEmpty() }
                ?: LocalDate.now(ZoneOffset.UTC).toString()
            val date = try {
                LocalDate.parse(dateStr)
            } catch (e: DateTimeParseException) {
                throw badRequest("invalid date format, use YYYY-MM-DD")
            }
            call.respond(s.wallboxes.slots(id, date))
        }
        get("/{id}/reviews") {
            val id = call.pathUuid("id", "wallbox id")
            val p = call.page()
            call.respond(s.reviews.listForWallbox(id, p.limit, p.offset))
        }

        // ---- authentifiziert
        authenticate(JWT_AUTH) {
            post { call.respond(HttpStatusCode.Created, s.wallboxes.create(call.userId(), call.receive<CreateWallboxRequest>())) }
            put("/{id}") {
                val id = call.pathUuid("id", "wallbox id")
                call.respond(s.wallboxes.update(id, call.userId(), call.receive<UpdateWallboxRequest>()))
            }
            delete("/{id}") {
                s.wallboxes.delete(call.pathUuid("id", "wallbox id"), call.userId())
                call.respond(HttpStatusCode.NoContent)
            }
            put("/{id}/status") {
                val id = call.pathUuid("id", "wallbox id")
                val req = call.receive<WallboxStatusRequest>()
                call.respond(WallboxStatusResponse(s.wallboxes.setOnline(id, call.userId(), req.isOnline)))
            }
            put("/{id}/availability") {
                val id = call.pathUuid("id", "wallbox id")
                call.respond<WeeklyAvailability>(s.wallboxes.updateAvailability(id, call.userId(), call.receive<WeeklyAvailability>()))
            }
        }
    }
}
