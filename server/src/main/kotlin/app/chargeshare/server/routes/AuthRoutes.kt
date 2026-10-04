package app.chargeshare.server.routes

import app.chargeshare.server.Services
import app.chargeshare.server.plugins.JWT_AUTH
import app.chargeshare.server.plugins.RateLimiter
import app.chargeshare.server.plugins.limitedPost
import app.chargeshare.shared.model.AppleAuthRequest
import app.chargeshare.shared.model.LoginRequest
import app.chargeshare.shared.model.LogoutRequest
import app.chargeshare.shared.model.RefreshRequest
import app.chargeshare.shared.model.RegisterRequest
import app.chargeshare.shared.model.UpdateRoleRequest
import app.chargeshare.shared.model.UpdateUserRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * @param rateLimiter begrenzt Registrierung, Login und Apple-Login je Client-Adresse (gemeinsamer Zähler); `null` = aus
 * @param trustProxyHeaders Client-Adresse aus `X-Forwarded-For` lesen (nur hinter genau einem vertrauenswürdigen Proxy)
 */
fun Route.authRoutes(s: Services, rateLimiter: RateLimiter? = null, trustProxyHeaders: Boolean = false) {
    route("/auth") {
        limitedPost("/register", rateLimiter, trustProxyHeaders) {
            call.respond(HttpStatusCode.Created, s.auth.register(call.receive<RegisterRequest>()))
        }
        limitedPost("/login", rateLimiter, trustProxyHeaders) {
            call.respond(s.auth.login(call.receive<LoginRequest>()))
        }
        limitedPost("/apple", rateLimiter, trustProxyHeaders) {
            call.respond(s.auth.loginWithApple(call.receive<AppleAuthRequest>()))
        }
        post("/refresh") {
            call.respond(s.auth.refresh(call.receive<RefreshRequest>().refreshToken))
        }
        authenticate(JWT_AUTH) {
            post("/logout") {
                val req = call.receive<LogoutRequest>()
                s.auth.logout(call.userId(), req.refreshToken)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

fun Route.userRoutes(s: Services) {
    authenticate(JWT_AUTH) {
        route("/users/me") {
            get { call.respond(s.auth.loadUser(call.userId())) }
            put { call.respond(s.auth.updateProfile(call.userId(), call.receive<UpdateUserRequest>())) }
            delete {
                s.auth.deleteAccount(call.userId())
                call.respond(HttpStatusCode.NoContent)
            }
            get("/stats") { call.respond(s.auth.stats(call.userId())) }
            put("/role") { call.respond(s.auth.updateRole(call.userId(), call.receive<UpdateRoleRequest>())) }
        }
    }
}
