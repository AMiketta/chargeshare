package app.chargeshare.server.plugins

import app.chargeshare.server.auth.TokenService
import app.chargeshare.shared.api.ChargeShareJson
import app.chargeshare.shared.model.ApiError
import app.chargeshare.shared.model.ErrorCodes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import app.chargeshare.server.util.AppException
import org.slf4j.event.Level

const val JWT_AUTH = "auth-jwt"

fun Application.configureSerialization() {
    install(ContentNegotiation) { json(ChargeShareJson) }
}

fun Application.configureHttp() {
    install(CallLogging) { level = Level.INFO }
    // Wie im Go-Backend: offene CORS-Policy fuer Web-Client (Entwicklung); in Produktion einschraenken.
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Request-ID")
        maxAgeInSeconds = 300
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("invalid JSON body", ErrorCodes.BAD_REQUEST))
        }
        exception<AppException> { call, e ->
            call.respond(e.status, ApiError(e.message, e.code))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("an unexpected error occurred", ErrorCodes.INTERNAL_ERROR))
        }
        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, ApiError("missing or invalid token", ErrorCodes.UNAUTHORIZED))
        }
    }
}

fun Application.configureSecurity(tokens: TokenService) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            verifier(tokens.verifier)
            validate { cred -> if (cred.payload.subject != null) JWTPrincipal(cred.payload) else null }
        }
    }
}
