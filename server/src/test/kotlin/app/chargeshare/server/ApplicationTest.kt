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
    fun apiWithoutDatabaseReturns503() = testApplication {
        application { module(config) }
        val res = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"ada@example.com","password":"geheim1234","name":"Ada"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        assertEquals(ErrorCodes.INTERNAL_ERROR, err(res.bodyAsText()).code)
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
