package app.chargeshare.server.auth

import app.chargeshare.server.util.notImplemented
import app.chargeshare.server.util.unauthorized
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AppleIdentity(val sub: String, val email: String?, val emailVerified: Boolean)

/** Prueft ein Apple-Identity-Token (Go: `VerifyAppleToken`, zusaetzlich mit `iss`-/`aud`-/`exp`-Pruefung). */
interface AppleTokenVerifier {
    fun verify(identityToken: String): AppleIdentity
}

/** Ohne `APPLE_BUNDLE_ID` nicht nutzbar -> 501 (dokumentierter Stub). */
object DisabledAppleTokenVerifier : AppleTokenVerifier {
    override fun verify(identityToken: String): AppleIdentity =
        throw notImplemented("Sign in with Apple is not configured (set APPLE_BUNDLE_ID)")
}

/**
 * @param keyProvider liefert den RSA-Public-Key zur `kid` (Standard: Apples JWKS unter
 * https://appleid.apple.com/auth/keys, 1 h gecacht); in Tests ersetzbar.
 */
class AppleJwksTokenVerifier(
    private val bundleId: String,
    private val keyProvider: (kid: String) -> RSAPublicKey? = ApplePublicKeys::get,
) : AppleTokenVerifier {
    override fun verify(identityToken: String): AppleIdentity {
        val decoded = runCatching { JWT.decode(identityToken) }.getOrNull() ?: throw unauthorized("invalid apple token format")
        val key = decoded.keyId?.let(keyProvider) ?: throw unauthorized("apple key not found")
        val jwt = runCatching {
            JWT.require(Algorithm.RSA256(key, null))
                .withIssuer("https://appleid.apple.com")
                .withAudience(bundleId)
                .build()
                .verify(identityToken)
        }.getOrNull() ?: throw unauthorized("invalid apple token signature")
        val sub = jwt.subject?.takeIf { it.isNotEmpty() } ?: throw unauthorized("missing sub in apple token")
        val verified = jwt.getClaim("email_verified").let { it.asBoolean() ?: it.asString()?.toBoolean() ?: false }
        return AppleIdentity(sub, jwt.getClaim("email").asString(), verified)
    }
}

object ApplePublicKeys {
    private val http = HttpClient.newHttpClient()
    @Volatile private var cache: Map<String, RSAPublicKey> = emptyMap()
    @Volatile private var fetchedAt = 0L

    fun get(kid: String): RSAPublicKey? {
        val now = System.currentTimeMillis()
        if (kid !in cache || now - fetchedAt > 3_600_000) refresh()
        return cache[kid]
    }

    private fun refresh() {
        val res = http.send(
            HttpRequest.newBuilder(URI("https://appleid.apple.com/auth/keys")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        val keys = Json.parseToJsonElement(res.body()).jsonObject["keys"]!!.jsonArray.associate { k ->
            val o = k.jsonObject
            val dec = Base64.getUrlDecoder()
            val n = BigInteger(1, dec.decode(o["n"]!!.jsonPrimitive.content))
            val e = BigInteger(1, dec.decode(o["e"]!!.jsonPrimitive.content))
            o["kid"]!!.jsonPrimitive.content to
                (KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e)) as RSAPublicKey)
        }
        cache = keys
        fetchedAt = System.currentTimeMillis()
    }
}
