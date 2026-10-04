package app.chargeshare.shared.api

import app.chargeshare.shared.model.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ChargeShareApiTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val userJson = """{"id":"u1","email":"a@b.de","name":"Ada","role":"driver","is_verified":false,
        "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}"""

    private fun api(tokens: TokenStore = InMemoryTokenStore(), handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): ChargeShareApi {
        val client = createHttpClient("https://example.test", tokens, MockEngine(handler))
        return ChargeShareApi(client, tokens)
    }

    @Test
    fun loginStoresTokensAndPostsToV1() = runTest {
        val tokens = InMemoryTokenStore()
        val api = api(tokens) { req ->
            assertEquals(HttpMethod.Post, req.method)
            assertEquals("/api/v1/auth/login", req.url.encodedPath)
            assertEquals("""{"email":"a@b.de","password":"secret123"}""", req.body.toByteArray().decodeToString())
            respond("""{"access_token":"AT","refresh_token":"RT","user":$userJson}""", HttpStatusCode.OK, jsonHeaders)
        }
        val res = api.login("a@b.de", "secret123")
        assertEquals("Ada", res.user?.name)
        assertEquals("AT", tokens.accessToken())
        assertEquals("RT", tokens.refreshToken())
    }

    @Test
    fun errorResponseBecomesApiException() = runTest {
        val api = api { respond("""{"error":"wallbox not found","code":"NOT_FOUND"}""", HttpStatusCode.NotFound, jsonHeaders) }
        val ex = assertFailsWith<ApiException> { api.wallbox("missing") }
        assertEquals(404, ex.status)
        assertEquals(ErrorCodes.NOT_FOUND, ex.code)
        assertEquals("wallbox not found", ex.message)
    }

    @Test
    fun searchWallboxesSendsGoQueryParameters() = runTest {
        val api = api { req ->
            assertEquals("/api/v1/wallboxes", req.url.encodedPath)
            val q = req.url.parameters
            assertEquals("53.07", q["lat"])
            assertEquals("8.8", q["lng"])
            assertEquals("5000.0", q["radius"])
            assertEquals("ccs", q["connector"])
            assertEquals("11.0", q["min_power"])
            assertNull(q["max_price"])
            respond("""{"wallboxes":[],"limit":20,"offset":0}""", HttpStatusCode.OK, jsonHeaders)
        }
        val res = api.searchWallboxes(WallboxSearch(53.07, 8.8, 5000.0, ConnectorType.CCS, 11.0))
        assertEquals(20, res.limit)
    }

    @Test
    fun authenticatedRequestsCarryBearerToken() = runTest {
        val api = api(InMemoryTokenStore("AT", "RT")) { req ->
            assertEquals("Bearer AT", req.headers[HttpHeaders.Authorization])
            respond(userJson, HttpStatusCode.OK, jsonHeaders)
        }
        assertEquals("u1", api.me().id)
    }

    @Test
    fun expiredAccessTokenIsRefreshedAndRequestRetried() = runTest {
        val tokens = InMemoryTokenStore("OLD", "RT")
        var meCalls = 0
        val api = api(tokens) { req ->
            when (req.url.encodedPath) {
                "/api/v1/users/me" -> {
                    meCalls++
                    if (req.headers[HttpHeaders.Authorization] == "Bearer NEW") {
                        respond(userJson, HttpStatusCode.OK, jsonHeaders)
                    } else {
                        respond("""{"error":"expired","code":"UNAUTHORIZED"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                    }
                }
                "/api/v1/auth/refresh" -> {
                    assertEquals("""{"refresh_token":"RT"}""", req.body.toByteArray().decodeToString())
                    respond("""{"access_token":"NEW","refresh_token":"RT2"}""", HttpStatusCode.OK, jsonHeaders)
                }
                else -> error("unexpected ${req.url}")
            }
        }
        assertEquals("u1", api.me().id)
        assertEquals("NEW", tokens.accessToken())
        assertEquals("RT2", tokens.refreshToken())
        assertEquals(2, meCalls)
    }

    @Test
    fun slotsAndBookingsDecode() = runTest {
        val api = api { req ->
            when (req.url.encodedPath) {
                "/api/v1/wallboxes/w1/slots" -> {
                    assertEquals("2026-05-01", req.url.parameters["date"])
                    respond("""{"date":"2026-05-01","slots":[{"start":"08:00","end":"08:30"}]}""", HttpStatusCode.OK, jsonHeaders)
                }
                "/api/v1/bookings" -> respond(
                    """{"bookings":[{"id":"b1","wallbox_id":"w1","driver_id":"u1","start_time":"2026-05-01T08:00:00Z",
                       "end_time":"2026-05-01T08:30:00Z","status":"pending","price_per_kwh":0.4,
                       "created_at":"2026-04-30T09:00:00Z","updated_at":"2026-04-30T09:00:00Z"}],"limit":20,"offset":0}""",
                    HttpStatusCode.OK, jsonHeaders)
                else -> error("unexpected ${req.url}")
            }
        }
        assertEquals("08:30", api.wallboxSlots("w1", "2026-05-01").slots.single().end)
        assertEquals(BookingStatus.PENDING, api.bookings().bookings.single().status)
    }

    @Test
    fun paymentConfigIsLoadedFromPaymentsConfigPath() = runTest {
        val api = api { req ->
            assertEquals("/api/v1/payments/config", req.url.encodedPath)
            respond(
                """{"provider":"stripe","publishable_key":"pk_test_1","merchant_display_name":"ChargeShare",
                   "merchant_country_code":"DE","currency":"eur","google_pay_test_environment":true}""",
                HttpStatusCode.OK, jsonHeaders)
        }
        val config = api.paymentConfig()
        assertEquals("pk_test_1", config.publishableKey)
        assertEquals("DE", config.merchantCountryCode)
    }

    @Test
    fun payBookingPostsToPayPathAndDecodesClientSecret() = runTest {
        val api = api { req ->
            assertEquals(HttpMethod.Post, req.method)
            assertEquals("/api/v1/bookings/b1/pay", req.url.encodedPath)
            respond(
                """{"booking":{"id":"b1","wallbox_id":"w1","driver_id":"u1","start_time":"2026-05-01T08:00:00Z",
                   "end_time":"2026-05-01T08:30:00Z","status":"pending","price_per_kwh":0.4,
                   "created_at":"2026-04-30T09:00:00Z","updated_at":"2026-04-30T09:00:00Z"},"client_secret":"pi_1_secret"}""",
                HttpStatusCode.OK, jsonHeaders)
        }
        val response = api.payBooking("b1")
        assertEquals(BookingStatus.PENDING, response.booking?.status)
        assertEquals("pi_1_secret", response.clientSecret)
    }

    @Test
    fun requestsUseNewTokenAfterLogoutAndLoginAsOtherUser() = runTest {
        val tokens = InMemoryTokenStore()
        val usedTokens = mutableListOf<String?>()
        val api = api(tokens) { req ->
            when (req.url.encodedPath) {
                "/api/v1/auth/login" -> {
                    val isHost = req.body.toByteArray().decodeToString().contains("host@")
                    val (access, refresh) = if (isHost) "HOST_AT" to "HOST_RT" else "DRIVER_AT" to "DRIVER_RT"
                    respond("""{"access_token":"$access","refresh_token":"$refresh","user":$userJson}""", HttpStatusCode.OK, jsonHeaders)
                }
                "/api/v1/auth/logout" -> respond("", HttpStatusCode.NoContent)
                else -> {
                    usedTokens += req.headers[HttpHeaders.Authorization]
                    respond(userJson, HttpStatusCode.OK, jsonHeaders)
                }
            }
        }
        api.login("driver@b.de", "secret123")
        api.me()
        api.logout()
        api.login("host@b.de", "secret123")
        api.me()
        assertEquals(listOf<String?>("Bearer DRIVER_AT", "Bearer HOST_AT"), usedTokens)
    }

    @Test
    fun wallboxHostDecodesPublicProfile() = runTest {
        val api = api { req ->
            assertEquals(HttpMethod.Get, req.method)
            assertEquals("/api/v1/wallboxes/w1/host", req.url.encodedPath)
            respond(
                """{"name":"Andreas M.","member_since":"2024-03-01T10:00:00Z","sessions":47,"rating_avg":4.8,"rating_count":23,"is_superhost":true}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val host = api.wallboxHost("w1")
        assertEquals("Andreas M.", host.name)
        assertEquals(47, host.sessions)
        assertEquals(4.8, host.ratingAvg)
        assertEquals(true, host.isSuperhost)
        assertEquals(2024, host.memberSince.toString().take(4).toInt())
    }

    @Test
    fun favoritesUseListPutAndDelete() = runTest {
        val calls = mutableListOf<String>()
        val api = api(InMemoryTokenStore("AT", "RT")) { req ->
            calls += "${req.method.value} ${req.url.encodedPath}"
            if (req.method == HttpMethod.Get) respond("""{"wallbox_ids":["w2","w1"]}""", HttpStatusCode.OK, jsonHeaders)
            else respond("", HttpStatusCode.NoContent)
        }
        assertEquals(listOf("w2", "w1"), api.favoriteIds())
        api.addFavorite("w1")
        api.removeFavorite("w2")
        assertEquals(
            listOf("GET /api/v1/users/me/favorites", "PUT /api/v1/users/me/favorites/w1", "DELETE /api/v1/users/me/favorites/w2"),
            calls,
        )
    }

    @Test
    fun favoritesResponseWithoutIdsIsEmpty() = runTest {
        val api = api(InMemoryTokenStore("AT", "RT")) { respond("{}", HttpStatusCode.OK, jsonHeaders) }
        assertEquals(emptyList(), api.favoriteIds())
    }
}
