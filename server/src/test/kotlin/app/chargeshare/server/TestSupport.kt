package app.chargeshare.server

import app.chargeshare.server.auth.AppleTokenVerifier
import app.chargeshare.server.auth.DisabledAppleTokenVerifier
import app.chargeshare.server.config.AppConfig
import app.chargeshare.server.db.DatabaseFactory
import app.chargeshare.server.payments.FakePaymentProvider
import app.chargeshare.server.payments.PaymentProvider
import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.api.ChargeShareJson
import app.chargeshare.shared.api.InMemoryTokenStore
import app.chargeshare.shared.api.createHttpClient
import app.chargeshare.shared.model.ApiError
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.v1.jdbc.Database

/** Ein eingebettetes PostgreSQL pro Test-JVM; vor jedem Test werden alle Tabellen geleert. */
object TestDb {
    private val pg: EmbeddedPostgres by lazy {
        EmbeddedPostgres.builder().start().also { Runtime.getRuntime().addShutdownHook(Thread { runCatching { it.close() } }) }
    }
    val database: Database by lazy {
        DatabaseFactory.migrate(pg.postgresDatabase)
        Database.connect(pg.postgresDatabase)
    }

    fun reset() {
        database // Migration sicherstellen
        pg.postgresDatabase.connection.use { c ->
            c.createStatement().use {
                it.execute("TRUNCATE users, wallboxes, bookings, reviews, charging_sessions, refresh_tokens CASCADE")
            }
        }
    }
}

val testConfig = AppConfig(bcryptCost = 4)

class ServerScope(private val builder: ApplicationTestBuilder, val payments: PaymentProvider) {
    /** Roher HTTP-Client (ohne Auth) fuer Statuscode-/JSON-Pruefungen. */
    val raw: HttpClient get() = builder.client

    /** Neue Instanz des echten shared-[ChargeShareApi]-Clients mit eigenem TokenStore (= ein Geraet/Benutzer). */
    fun newApi(): TestClient {
        val store = InMemoryTokenStore()
        return TestClient(ChargeShareApi(createHttpClient("http://localhost/", store, engine = builder.client.engine), store), store)
    }
}

class TestClient(val api: ChargeShareApi, val store: InMemoryTokenStore)

fun serverTest(
    config: AppConfig = testConfig,
    payments: PaymentProvider = FakePaymentProvider(),
    apple: AppleTokenVerifier = DisabledAppleTokenVerifier,
    block: suspend ServerScope.() -> Unit,
) = testApplication {
    TestDb.reset()
    application { module(config, TestDb.database, payments, apple) }
    ServerScope(this, payments).block()
}

fun errOf(body: String): ApiError = ChargeShareJson.decodeFromString(body)

suspend fun HttpClient.postJson(path: String, body: String, token: String? = null): HttpResponse = post(path) {
    json(body, token)
}

suspend fun HttpClient.putJson(path: String, body: String, token: String? = null): HttpResponse = put(path) {
    json(body, token)
}

private fun HttpRequestBuilder.json(body: String, token: String?) {
    contentType(ContentType.Application.Json)
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    setBody(body)
}

suspend fun HttpClient.getAuth(path: String, token: String?): HttpResponse = get(path) {
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
}

suspend fun HttpClient.deleteAuth(path: String, token: String?): HttpResponse = delete(path) {
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
}

@Suppress("unused")
suspend fun HttpResponse.text(): String = bodyAsText()
