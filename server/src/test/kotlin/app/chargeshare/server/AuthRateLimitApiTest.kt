package app.chargeshare.server

import app.chargeshare.server.config.AppConfig
import app.chargeshare.shared.model.ErrorCodes
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Rate-Limiting für Registrierung/Login: 429 mit `Retry-After`, andere Routen bleiben unberührt. */
class AuthRateLimitApiTest {
    private val limited = testConfig.copy(authRateLimitPerWindow = 3, authRateLimitWindowSeconds = 600)

    private suspend fun ServerScope.login(forwardedFor: String? = null): io.ktor.client.statement.HttpResponse =
        raw.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            forwardedFor?.let { header("X-Forwarded-For", it) }
            setBody("""{"email":"niemand@example.com","password":"falsch12345"}""")
        }

    @Test
    fun loginIsBlockedAfterTheLimitWithRetryAfter() = serverTest(config = limited) {
        repeat(3) { assertEquals(HttpStatusCode.Unauthorized, login().status) }
        val blocked = login()
        assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
        assertEquals(ErrorCodes.RATE_LIMITED, errOf(blocked.bodyAsText()).code)
        val retryAfter = assertNotNull(blocked.headers[HttpHeaders.RetryAfter]).toLong()
        assertTrue(retryAfter in 1..600)
    }

    @Test
    fun registrationAndLoginShareTheSameCounter() = serverTest(config = limited) {
        raw.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"a@example.com","password":"geheim1234","name":"A"}""")
        }
        raw.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"b@example.com","password":"geheim1234","name":"B"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, login().status)
        assertEquals(HttpStatusCode.TooManyRequests, login().status)
    }

    @Test
    fun otherEndpointsAreNotLimited() = serverTest(config = limited) {
        repeat(4) { login() }
        repeat(6) {
            assertEquals(HttpStatusCode.OK, raw.get("/health").status)
            assertEquals(HttpStatusCode.OK, raw.get("/api/v1/wallboxes").status)
        }
    }

    @Test
    fun forwardedForIsIgnoredUnlessProxyIsTrusted() = serverTest(config = limited) {
        repeat(3) { login(forwardedFor = "10.0.0.$it") }
        // gefälschte Adressen umgehen das Limit nicht
        assertEquals(HttpStatusCode.TooManyRequests, login(forwardedFor = "10.0.0.99").status)
    }

    @Test
    fun trustedProxyUsesTheLastForwardedAddress() = serverTest(config = limited.copy(trustProxyHeaders = true)) {
        repeat(3) { assertEquals(HttpStatusCode.Unauthorized, login(forwardedFor = "1.1.1.1, 9.9.9.9").status) }
        assertEquals(HttpStatusCode.TooManyRequests, login(forwardedFor = "2.2.2.2, 9.9.9.9").status)
        // andere Client-Adresse hinter dem Proxy ist nicht betroffen
        assertEquals(HttpStatusCode.Unauthorized, login(forwardedFor = "2.2.2.2, 8.8.8.8").status)
    }

    @Test
    fun zeroDisablesTheLimit() = serverTest(config = limited.copy(authRateLimitPerWindow = 0)) {
        repeat(8) { assertEquals(HttpStatusCode.Unauthorized, login().status) }
    }

    @Test
    fun configIsValidatedAndReadFromEnv() {
        val config = AppConfig.fromEnv(mapOf("AUTH_RATE_LIMIT_PER_WINDOW" to "5", "AUTH_RATE_LIMIT_WINDOW_SECONDS" to "30", "TRUST_PROXY_HEADERS" to "true"))
        assertEquals(5, config.authRateLimitPerWindow)
        assertEquals(30, config.authRateLimitWindowSeconds)
        assertTrue(config.trustProxyHeaders)
        val defaults = AppConfig.fromEnv(emptyMap())
        assertEquals(10, defaults.authRateLimitPerWindow)
        assertEquals(false, defaults.trustProxyHeaders)
        assertTrue(runCatching { AppConfig(authRateLimitPerWindow = -1) }.isFailure)
        assertTrue(runCatching { AppConfig(authRateLimitWindowSeconds = 0) }.isFailure)
    }
}
