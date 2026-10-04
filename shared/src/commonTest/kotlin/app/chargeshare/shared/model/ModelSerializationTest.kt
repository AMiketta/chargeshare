package app.chargeshare.shared.model

import app.chargeshare.shared.api.ChargeShareJson
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json

class ModelSerializationTest {

    // Beispielantwort, wie sie das Go-Backend (models.Wallbox) ausliefert
    private val goWallboxJson = """
        {
          "id": "0b1d1d0e-6a53-4c1b-9d5e-0a1b2c3d4e5f",
          "owner_id": "11111111-2222-3333-4444-555555555555",
          "name": "Wallbox am Markt",
          "address": "Marktplatz 1",
          "city": "Bremen",
          "postal_code": "28195",
          "lat": 53.0758,
          "lng": 8.8072,
          "power_kw": 11,
          "connector_type": "type2",
          "price_per_kwh": 0.39,
          "min_duration_min": 30,
          "max_duration_min": 480,
          "is_online": true,
          "is_metered": true,
          "availability": {"monday": [{"start": "08:00", "end": "20:00"}]},
          "rating_avg": 4.5,
          "rating_count": 12,
          "created_at": "2026-04-01T12:00:00Z",
          "updated_at": "2026-04-02T08:30:15.123456789Z",
          "distance_m": 420.5
        }
    """.trimIndent()

    @Test
    fun decodesGoWallbox() {
        val wb = ChargeShareJson.decodeFromString<Wallbox>(goWallboxJson)
        assertEquals("Bremen", wb.city)
        assertEquals(ConnectorType.TYPE2, wb.connectorType)
        assertEquals(11.0, wb.powerKw)
        assertEquals(0.39, wb.pricePerKwh)
        assertEquals(420.5, wb.distanceM)
        assertEquals("08:00", wb.availability.getValue("monday").single().start)
        assertEquals(Instant.parse("2026-04-01T12:00:00Z"), wb.createdAt)
        assertEquals("Marktplatz 1, 28195 Bremen", wb.fullAddress)
        assertTrue(wb.photos.isEmpty())
    }

    @Test
    fun wallboxRoundTripKeepsSnakeCaseFieldNames() {
        val wb = ChargeShareJson.decodeFromString<Wallbox>(goWallboxJson)
        val obj = ChargeShareJson.parseToJsonElement(ChargeShareJson.encodeToString(wb)).jsonObject
        for (key in listOf("owner_id", "postal_code", "power_kw", "connector_type", "price_per_kwh",
            "min_duration_min", "is_online", "rating_avg", "created_at", "distance_m")) {
            assertTrue(key in obj, "Feld $key fehlt")
        }
        assertFalse("ownerId" in obj)
        assertEquals("type2", (obj.getValue("connector_type") as JsonPrimitive).content)
    }

    @Test
    fun nullListsAndMapsFromGoFallBackToDefaults() {
        val json = goWallboxJson.replace("\"availability\": {\"monday\": [{\"start\": \"08:00\", \"end\": \"20:00\"}]},",
            "\"availability\": null, \"photos\": null,")
        val wb = ChargeShareJson.decodeFromString<Wallbox>(json)
        assertTrue(wb.availability.isEmpty())
        assertTrue(wb.photos.isEmpty())
    }

    @Test
    fun decodesBookingWithOptionalFieldsAndStatus() {
        val json = """
            {"id":"b1","wallbox_id":"w1","driver_id":"u1",
             "start_time":"2026-05-01T10:00:00Z","end_time":"2026-05-01T12:00:00Z",
             "status":"confirmed","price_per_kwh":0.4,"total_amount":8.8,
             "stripe_payment_intent_id":"pi_123",
             "created_at":"2026-04-30T09:00:00Z","updated_at":"2026-04-30T09:00:00Z"}
        """.trimIndent()
        val b = ChargeShareJson.decodeFromString<Booking>(json)
        assertEquals(BookingStatus.CONFIRMED, b.status)
        assertEquals(8.8, b.totalAmount)
        assertEquals("pi_123", b.stripePaymentIntentId)
        assertNull(b.actualKwh)
        assertNull(b.wallbox)
        assertEquals(false, b.reviewed, "ohne Feld gilt die Buchung als nicht bewertet")
    }

    @Test
    fun decodesReviewedFlagOfBooking() {
        val json = """
            {"id":"b1","wallbox_id":"w1","driver_id":"u1",
             "start_time":"2026-05-01T10:00:00Z","end_time":"2026-05-01T12:00:00Z",
             "status":"completed","price_per_kwh":0.4,"reviewed":true,
             "created_at":"2026-04-30T09:00:00Z","updated_at":"2026-04-30T09:00:00Z"}
        """.trimIndent()
        assertEquals(true, ChargeShareJson.decodeFromString<Booking>(json).reviewed)
    }

    @Test
    fun createBookingRequestEncodesRfc3339AndSnakeCase() {
        val req = CreateBookingRequest("w1", Instant.parse("2026-05-01T10:00:00Z"), Instant.parse("2026-05-01T12:00:00Z"))
        val obj = Json.parseToJsonElement(ChargeShareJson.encodeToString(req)).jsonObject
        assertEquals("w1", (obj.getValue("wallbox_id") as JsonPrimitive).content)
        assertEquals("2026-05-01T10:00:00Z", (obj.getValue("start_time") as JsonPrimitive).content)
        assertEquals("2026-05-01T12:00:00Z", (obj.getValue("end_time") as JsonPrimitive).content)
    }

    @Test
    fun decodesAuthResponseWithUser() {
        val json = """
            {"access_token":"a","refresh_token":"r","user":{"id":"u1","email":"a@b.de","name":"Ada","role":"both",
             "is_verified":true,"created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}}
        """.trimIndent()
        val r = ChargeShareJson.decodeFromString<AuthResponse>(json)
        assertEquals("a", r.accessToken)
        assertEquals(UserRole.BOTH, r.user?.role)
        assertTrue(r.user!!.isVerified)
    }

    @Test
    fun requestDtosUseGoFieldNames() {
        fun keys(s: String): Set<String> = Json.parseToJsonElement(s).jsonObject.keys
        assertEquals(setOf("identity_token", "full_name"),
            keys(ChargeShareJson.encodeToString(AppleAuthRequest("tok", "Ada"))))
        assertEquals(setOf("refresh_token"), keys(ChargeShareJson.encodeToString(RefreshRequest("r"))))
        assertEquals(setOf("booking_id", "rating", "comment"),
            keys(ChargeShareJson.encodeToString(CreateReviewRequest("b1", 5, "top"))))
        // null-Felder werden nicht geschrieben (Go: omitempty)
        assertEquals(setOf("booking_id", "rating"),
            keys(ChargeShareJson.encodeToString(CreateReviewRequest("b1", 5))))
    }

    @Test
    fun decodesApiErrorAndHostEarnings() {
        val e = ChargeShareJson.decodeFromString<ApiError>("""{"error":"wallbox not found","code":"NOT_FOUND"}""")
        assertEquals("wallbox not found", e.message)
        assertEquals(ErrorCodes.NOT_FOUND, e.code)
        val h = ChargeShareJson.decodeFromString<HostEarnings>("""{"total":120.5,"month":30,"sessions":7,"avg_rating":4.8}""")
        assertEquals(7, h.sessions)
        assertEquals(4.8, h.avgRating)
        // Ältere Server liefern die neuen Dashboard-Felder nicht: Defaults statt Fehler.
        assertEquals(emptyList(), h.week)
        assertNull(h.monthChangePct)
        assertEquals(0.0, h.kwh)
    }

    @Test
    fun decodesPaymentConfigFromServer() {
        val json = """
            {"provider":"stripe","publishable_key":"pk_test_1","merchant_display_name":"ChargeShare",
             "merchant_country_code":"DE","currency":"eur","google_pay_test_environment":true,
             "apple_pay_merchant_id":"merchant.app.chargeshare","unbekanntes_feld":1}
        """.trimIndent()
        val config = ChargeShareJson.decodeFromString<PaymentConfig>(json)
        assertEquals(PaymentProviderKind.STRIPE, config.provider)
        assertTrue(config.requiresClientPayment)
        assertEquals("pk_test_1", config.publishableKey)
        assertEquals("merchant.app.chargeshare", config.applePayMerchantId)
        assertTrue(config.googlePayTestEnvironment)
    }

    @Test
    fun fakePaymentConfigNeedsNoClientPayment() {
        val json = """{"provider":"fake","merchant_display_name":"ChargeShare","merchant_country_code":"DE","currency":"eur","google_pay_test_environment":true}"""
        val config = ChargeShareJson.decodeFromString<PaymentConfig>(json)
        assertFalse(config.requiresClientPayment)
        assertNull(config.publishableKey)
        assertNull(config.applePayMerchantId)
    }
}
