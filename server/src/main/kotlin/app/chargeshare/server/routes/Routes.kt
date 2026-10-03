package app.chargeshare.server.routes

import app.chargeshare.server.plugins.JWT_AUTH
import app.chargeshare.shared.model.ApiError
import app.chargeshare.shared.model.ErrorCodes
import app.chargeshare.shared.model.LoginRequest
import app.chargeshare.shared.model.RegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val database: String)

private suspend fun io.ktor.server.routing.RoutingCall.notImplemented(what: String) =
    respond(HttpStatusCode.NotImplemented, ApiError("$what is not implemented yet", ErrorCodes.NOT_IMPLEMENTED))

private suspend fun io.ktor.server.routing.RoutingCall.validation(message: String) =
    respond(HttpStatusCode.UnprocessableEntity, ApiError(message, ErrorCodes.VALIDATION_ERROR))

fun Route.healthRoutes(databaseStatus: () -> String) {
    get("/health") {
        call.respond(HealthResponse(status = "ok", database = databaseStatus()))
    }
}

/**
 * Auth-Routen (Stubs). Pfade, Request-Validierung und Statuscodes folgen dem Go-Backend
 * (legacy/go-backend/internal/api/router.go); die Logik (Passwort-Hashing, Refresh-Rotation,
 * Apple-Token-Pruefung) folgt in einem naechsten Schritt.
 */
fun Route.apiV1Routes() {
    route("/api/v1") {
        route("/auth") {
            post("/register") {
                val req = call.receive<RegisterRequest>()
                when {
                    !EMAIL.matches(req.email) -> call.validation("email: must be a valid email address")
                    req.password.length < 8 -> call.validation("password: must be at least 8 characters")
                    req.name.isBlank() || req.name.length > 100 -> call.validation("name: must be 1-100 characters")
                    else -> call.notImplemented("register")
                }
            }
            post("/login") {
                val req = call.receive<LoginRequest>()
                if (!EMAIL.matches(req.email) || req.password.isEmpty()) {
                    call.validation("email and password are required")
                } else {
                    call.notImplemented("login")
                }
            }
            post("/apple") { call.notImplemented("apple sign-in") }
            post("/refresh") { call.notImplemented("refresh") }
            authenticate(JWT_AUTH) {
                post("/logout") { call.notImplemented("logout") }
            }
        }
        authenticate(JWT_AUTH) {
            get("/users/me") { call.notImplemented("users/me") }
        }
    }
}

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
