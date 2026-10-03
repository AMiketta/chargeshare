package app.chargeshare.server.auth

import app.chargeshare.server.config.AppConfig
import app.chargeshare.shared.model.UserRole
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

/** Erzeugt/prueft HS256-Access-Tokens (Claims: `sub` = User-ID, `role`). */
class TokenService(private val config: AppConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(config.jwtIssuer).build()

    fun createAccessToken(userId: String, role: UserRole, now: Date = Date()): String =
        JWT.create()
            .withIssuer(config.jwtIssuer)
            .withSubject(userId)
            .withClaim("role", role.name.lowercase())
            .withIssuedAt(now)
            .withExpiresAt(Date(now.time + config.accessTokenTtlSeconds * 1000))
            .sign(algorithm)
}
