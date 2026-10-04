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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.utils.io.readRemaining
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import kotlinx.io.readByteArray
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

/**
 * Liest die Datei `file` aus einer `multipart/form-data`-Anfrage und bricht ab, sobald [limit] Bytes überschritten sind,
 * damit ein großer Upload nicht den Speicher füllt.
 */
private suspend fun ApplicationCall.receivePhotoBytes(limit: Long): ByteArray {
    val declared = request.contentLength()
    if (declared != null && declared > limit + MULTIPART_OVERHEAD_BYTES) throw validationError("photo: file is too large")
    var bytes: ByteArray? = null
    receiveMultipart().forEachPart { part ->
        try {
            if (part is PartData.FileItem && part.name == "file" && bytes == null) {
                bytes = part.provider().readRemaining(limit + 1).readByteArray()
            }
        } finally {
            part.dispose()
        }
    }
    val received = bytes ?: throw badRequest("multipart field 'file' is required")
    if (received.size > limit) throw validationError("photo: file is too large")
    return received
}

private const val MULTIPART_OVERHEAD_BYTES = 64 * 1024L

fun Route.wallboxRoutes(s: Services) {
    route("/wallboxes") {
        // ---- oeffentlich
        get { call.respond(s.wallboxes.search(call.searchParams())) }
        get("/{id}") { call.respond(s.wallboxes.get(call.pathUuid("id", "wallbox id"))) }
        get("/{id}/availability") {
            call.respond<WeeklyAvailability>(s.wallboxes.availability(call.pathUuid("id", "wallbox id")))
        }
        get("/{id}/host") { call.respond(s.host.publicProfile(call.pathUuid("id", "wallbox id"))) }
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
        // Fotos sind wie die Wallbox öffentlich lesbar; lange cachebar, weil jeder Upload einen neuen Namen bekommt.
        get("/{id}/photos/{name}") {
            val bytes = s.photos.read(call.pathUuid("id", "wallbox id"), call.parameters["name"] ?: "")
            call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
            call.respondBytes(bytes, ContentType.Image.JPEG)
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
                val id = call.pathUuid("id", "wallbox id")
                s.wallboxes.delete(id, call.userId())
                s.photos.purge(id)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/{id}/photos") {
                val id = call.pathUuid("id", "wallbox id")
                val userId = call.userId()
                s.wallboxes.assertCanEdit(id, userId)
                call.respond(HttpStatusCode.Created, s.photos.upload(id, userId, call.receivePhotoBytes(s.photoUploadLimit)))
            }
            delete("/{id}/photos/{name}") {
                val id = call.pathUuid("id", "wallbox id")
                call.respond(s.photos.remove(id, call.userId(), call.parameters["name"] ?: ""))
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
