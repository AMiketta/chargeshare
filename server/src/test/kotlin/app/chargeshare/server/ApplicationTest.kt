package app.chargeshare.server

import app.chargeshare.server.auth.TokenService
import app.chargeshare.server.config.AppConfig
import app.chargeshare.shared.api.ChargeShareJson
import app.chargeshare.shared.model.ApiError
import app.chargeshare.shared.model.ErrorCodes
import app.chargeshare.shared.model.UserRole
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    private val config = AppConfig()

    private fun err(body: String) = ChargeShareJson.decodeFromString<ApiError>(body)

    @Test
    fun healthReportsOkWithoutDatabase() = testApplication {
        application { module(config) }
        val res = client.get("/health")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals("""{"status":"ok","database":"disabled"}""", res.bodyAsText())
    }

    @Test
    fun registerValidatesPayload() = testApplication {
        application { module(config) }
        val res = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"kaputt","password":"x","name":"Ada"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertEquals(ErrorCodes.VALIDATION_ERROR, err(res.bodyAsText()).code)
    }

    @Test
    fun registerWithValidPayloadIsStub501() = testApplication {
        application { module(config) }
        val res = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"ada@example.com","password":"geheim1234","name":"Ada"}""")
        }
        assertEquals(HttpStatusCode.NotImplemented, res.status)
        assertEquals(ErrorCodes.NOT_IMPLEMENTED, err(res.bodyAsText()).code)
    }

    @Test
    fun invalidJsonIsBadRequest() = testApplication {
        application { module(config) }
        val res = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("{nicht json")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(ErrorCodes.BAD_REQUEST, err(res.bodyAsText()).code)
    }

    @Test
    fun otherAuthStubsReturn501() = testApplication {
        application { module(config) }
        for (path in listOf("/api/v1/auth/apple", "/api/v1/auth/refresh")) {
            assertEquals(HttpStatusCode.NotImplemented, client.post(path).status, path)
        }
    }

    @Test
    fun protectedRoutesRequireValidJwt() = testApplication {
        application { module(config) }
        val none = client.get("/api/v1/users/me")
        assertEquals(HttpStatusCode.Unauthorized, none.status)
        assertEquals(ErrorCodes.UNAUTHORIZED, err(none.bodyAsText()).code)

        val bad = client.get("/api/v1/users/me") { header(HttpHeaders.Authorization, "Bearer abc.def.ghi") }
        assertEquals(HttpStatusCode.Unauthorized, bad.status)

        val token = TokenService(config).createAccessToken("11111111-2222-3333-4444-555555555555", UserRole.DRIVER)
        val ok = client.get("/api/v1/users/me") { header(HttpHeaders.Authorization, "Bearer $token") }
        // Authentifiziert -> erreicht den Stub-Handler
        assertEquals(HttpStatusCode.NotImplemented, ok.status)
    }

    @Test
    fun tokenWithWrongSecretIsRejected() = testApplication {
        application { module(config) }
        val foreign = TokenService(config.copy(jwtSecret = "x".repeat(40))).createAccessToken("u1", UserRole.HOST)
        val res = client.get("/api/v1/users/me") { header(HttpHeaders.Authorization, "Bearer $foreign") }
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun productionConfigRequiresRealSecrets() {
        val ex = runCatching { AppConfig(env = "production") }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        AppConfig(env = "production", jwtSecret = "s".repeat(40), jwtRefreshSecret = "r".repeat(40))
    }

    @Test
    fun jdbcUrlConversion() {
        assertEquals("jdbc:postgresql://u:p@h:5432/db", app.chargeshare.server.db.DatabaseFactory.toJdbcUrl("postgres://u:p@h:5432/db"))
        assertEquals("jdbc:postgresql://h/db", app.chargeshare.server.db.DatabaseFactory.toJdbcUrl("jdbc:postgresql://h/db"))
    }
}
