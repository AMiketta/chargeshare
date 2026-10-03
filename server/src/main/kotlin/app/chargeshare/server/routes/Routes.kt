package app.chargeshare.server.routes

import app.chargeshare.server.Services
import app.chargeshare.server.plugins.JWT_AUTH
import app.chargeshare.server.util.serviceUnavailable
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val database: String)

fun Route.healthRoutes(databaseStatus: () -> String) {
    get("/health") {
        call.respond(HealthResponse(status = "ok", database = databaseStatus()))
    }
}

/**
 * Alle `/api/v1`-Routen (Pfade identisch zu legacy/go-backend/internal/api/router.go).
 * Ohne Datenbank (`services == null`) antworten alle Pfade mit 503.
 */
fun Route.apiV1Routes(services: Services?) {
    route("/api/v1") {
        if (services == null) {
            route("{...}") {
                handle { throw serviceUnavailable("database is not configured (set DATABASE_URL)") }
            }
            return@route
        }
        authRoutes(services)
        userRoutes(services)
        wallboxRoutes(services)
        bookingRoutes(services)
    }
}
