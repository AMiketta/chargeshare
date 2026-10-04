package app.chargeshare.server.routes

import app.chargeshare.server.util.unauthorized
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import kotlin.uuid.Uuid

fun ApplicationCall.userId(): Uuid {
    val sub = principal<JWTPrincipal>()?.payload?.subject ?: throw unauthorized("missing or invalid token")
    return runCatching { Uuid.parse(sub) }.getOrNull() ?: throw unauthorized("invalid user id")
}

/** `limit` 1..100 (Default 20), `offset` >= 0 – wie im Go-Backend; ungueltige Werte fallen auf den Default. */
data class Page(val limit: Int, val offset: Int)

fun ApplicationCall.page(): Page {
    val limit = request.queryParameters["limit"]?.toIntOrNull()?.takeIf { it in 1..100 } ?: 20
    val offset = request.queryParameters["offset"]?.toIntOrNull()?.takeIf { it >= 0 } ?: 0
    return Page(limit, offset)
}

fun ApplicationCall.pathUuid(name: String, what: String): Uuid =
    app.chargeshare.server.util.parseUuid(parameters[name] ?: "", what)

/** Adresse (`http://10.0.2.2:8080`), unter der der Client den Server erreicht; hinter einem Proxy mit `X-Forwarded-*` die öffentliche. */
fun ApplicationCall.requestBaseUrl(): String {
    val origin = request.origin
    val defaultPort = if (origin.scheme == "https") 443 else 80
    val portSuffix = if (origin.serverPort == defaultPort) "" else ":${origin.serverPort}"
    return "${origin.scheme}://${origin.serverHost}$portSuffix"
}
