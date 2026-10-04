package app.chargeshare.server

import app.chargeshare.server.auth.AppleTokenVerifier
import app.chargeshare.server.auth.DisabledAppleTokenVerifier
import app.chargeshare.server.config.AppConfig
import app.chargeshare.server.db.DatabaseFactory
import app.chargeshare.server.geocoding.GeocodeHit
import app.chargeshare.server.geocoding.Geocoder
import app.chargeshare.server.geocoding.GeocoderUnavailableException
import app.chargeshare.server.storage.InMemoryPhotoStorage
import app.chargeshare.server.mail.MailMessage
import app.chargeshare.server.mail.Mailer
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

val testConfig = AppConfig(bcryptCost = 4, authRateLimitPerWindow = 0)

/** Sammelt verschickte Mails, damit Tests den Reset-Link auslesen können. */
class CapturingMailer : Mailer {
    val sent: MutableList<MailMessage> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var failure: Throwable? = null

    override fun send(message: MailMessage) {
        failure?.let { throw it }
        sent += message
    }
}

/** Ersatz für die Adresssuche: feste Treffer je Straße, optional ein Ausfall. */
class FakeGeocoder : Geocoder {
    val hits = mutableMapOf<String, GeocodeHit>()
    @Volatile var unavailable = false
    val calls = java.util.Collections.synchronizedList(mutableListOf<Triple<String, String, String>>())

    override suspend fun geocode(address: String, postalCode: String, city: String): GeocodeHit? {
        calls += Triple(address, postalCode, city)
        if (unavailable) throw GeocoderUnavailableException("down")
        return hits[address]
    }
}

class ServerScope(
    private val builder: ApplicationTestBuilder,
    val payments: PaymentProvider,
    val mailer: CapturingMailer,
    val photoStorage: InMemoryPhotoStorage,
    val geocoder: FakeGeocoder,
) {
    fun wsClient(): HttpClient = builder.createClient { install(io.ktor.client.plugins.websocket.WebSockets) }

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
    mailer: CapturingMailer = CapturingMailer(),
    photoStorage: InMemoryPhotoStorage = InMemoryPhotoStorage(),
    geocoder: FakeGeocoder = FakeGeocoder(),
    block: suspend ServerScope.() -> Unit,
) = testApplication {
    TestDb.reset()
    application { module(config, TestDb.database, payments, apple, mailer, photoStorage, geocoder) }
    ServerScope(this, payments, mailer, photoStorage, geocoder).block()
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

// ---- Fixtures -----------------------------------------------------------------------------

val allDays = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

/** Jeden Tag 08:00-12:00 und 14:00-18:00 UTC verfuegbar. */
fun weekPlan(): Map<String, List<app.chargeshare.shared.model.TimeSlot>> =
    allDays.associateWith {
        listOf(app.chargeshare.shared.model.TimeSlot("08:00", "12:00"), app.chargeshare.shared.model.TimeSlot("14:00", "18:00"))
    }

fun newWallbox(
    name: String = "Testbox",
    lat: Double = 53.0758,
    lng: Double = 8.8072,
    power: Double = 11.0,
    price: Double = 0.40,
    connector: app.chargeshare.shared.model.ConnectorType = app.chargeshare.shared.model.ConnectorType.TYPE2,
) = app.chargeshare.shared.model.CreateWallboxRequest(
    name = name, address = "Marktplatz 1", city = "Bremen", postalCode = "28195", lat = lat, lng = lng,
    powerKw = power, connectorType = connector, pricePerKwh = price, availability = weekPlan(),
)

/** Registriert einen Benutzer; mit [host] wird die Rolle auf `host` gesetzt. */
suspend fun ServerScope.registerUser(email: String, host: Boolean = false): TestClient {
    val c = newApi()
    c.api.register(email, "geheim1234", email.substringBefore('@'))
    if (host) c.api.updateRole(app.chargeshare.shared.model.UserRole.HOST)
    return c
}

fun inFuture(daysAhead: Long, hour: Int, minute: Int = 0): kotlin.time.Instant =
    java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(daysAhead).atTime(hour, minute)
        .toInstant(java.time.ZoneOffset.UTC).let { kotlin.time.Instant.fromEpochSeconds(it.epochSecond) }

/** Setzt eine Buchung per SQL auf `completed` (die OCPP-/Zeit-Logik dafuer laeuft sonst ausserhalb der API). */
fun forceBookingStatus(bookingId: String, status: String) {
    org.jetbrains.exposed.v1.jdbc.transactions.transaction(TestDb.database) {
        exec("UPDATE bookings SET status = '$status' WHERE id = '$bookingId'")
    }
}

/** Datiert eine Buchung per SQL zurueck, damit sie als „schon laenger offen“ gilt. */
fun backdateBooking(bookingId: String, minutesAgo: Long) {
    org.jetbrains.exposed.v1.jdbc.transactions.transaction(TestDb.database) {
        exec("UPDATE bookings SET created_at = now() - interval '$minutesAgo minutes' WHERE id = '$bookingId'")
    }
}

/** Setzt die tatsaechlich geladenen kWh einer Buchung per SQL (sonst nur durch einen echten OCPP-Ladevorgang). */
fun forceBookingActualKwh(bookingId: String, kwh: Double) {
    org.jetbrains.exposed.v1.jdbc.transactions.transaction(TestDb.database) {
        exec("UPDATE bookings SET actual_kwh = $kwh WHERE id = '$bookingId'")
    }
}

/** Verschiebt eine Buchung per SQL auf [start]..[end] (z. B. in den Vormonat, damit Monatswerte prüfbar sind). */
fun moveBooking(bookingId: String, start: java.time.Instant, end: java.time.Instant) {
    org.jetbrains.exposed.v1.jdbc.transactions.transaction(TestDb.database) {
        exec("UPDATE bookings SET start_time = '$start', end_time = '$end' WHERE id = '$bookingId'")
    }
}

/** Setzt Betrag und gemessene kWh einer Buchung per SQL (sonst nur durch einen echten OCPP-Ladevorgang). */
fun forceBookingTotals(bookingId: String, amount: Double, kwh: Double) {
    org.jetbrains.exposed.v1.jdbc.transactions.transaction(TestDb.database) {
        exec("UPDATE bookings SET total_amount = $amount, actual_kwh = $kwh WHERE id = '$bookingId'")
    }
}
