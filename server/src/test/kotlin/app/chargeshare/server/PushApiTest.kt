package app.chargeshare.server

import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.PushNotificationKind
import app.chargeshare.shared.model.PushPlatform
import app.chargeshare.shared.model.RegisterDeviceRequest
import app.chargeshare.shared.model.ReportProblemRequest
import app.chargeshare.shared.model.UnregisterDeviceRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Push-Geräte und Buchungs-Hooks (`POST|DELETE /devices`, `POST /bookings/{id}/problem`). */
class PushApiTest {

    @Test
    fun registerAndUnregisterDevice() = serverTest {
        val user = registerUser("push-driver@example.com")
        user.api.registerDevice(RegisterDeviceRequest(PushPlatform.ANDROID, "fcm-token-abc"))
        user.api.unregisterDevice(UnregisterDeviceRequest("fcm-token-abc"))
    }

    @Test
    fun confirmedBookingNotifiesHostAndDriver() = serverTest {
        val host = registerUser("push-host@example.com", host = true)
        val wallboxId = host.api.createWallbox(newWallbox(name = "PushBox")).id
        host.api.setWallboxOnline(wallboxId, true)
        host.api.registerDevice(RegisterDeviceRequest(PushPlatform.IOS, "host-token"))

        val driver = registerUser("push-driver2@example.com")
        driver.api.registerDevice(RegisterDeviceRequest(PushPlatform.ANDROID, "driver-token"))

        val booking = driver.api.createBooking(
            CreateBookingRequest(wallboxId, inFuture(2, 9), inFuture(2, 10)),
        ).booking!!
        assertEquals(BookingStatus.CONFIRMED, booking.status)

        val types = push.sent.map { it.payload.type }.toSet()
        assertTrue(PushNotificationKind.TYPE_HOST_UNLOCK in types, "expected host unlock, got $types")
        assertTrue(PushNotificationKind.TYPE_DRIVER_REMINDER in types, "expected driver reminder, got $types")
        assertTrue(push.sent.any { it.token == "host-token" && it.payload.type == PushNotificationKind.TYPE_HOST_UNLOCK })
        assertTrue(push.sent.any { it.token == "driver-token" && it.payload.type == PushNotificationKind.TYPE_DRIVER_REMINDER })
        assertTrue(push.sent.any { it.payload.deepLink?.contains("action=cancel") == true })
    }

    @Test
    fun cancelNotifiesHostWhenConfirmed() = serverTest {
        val host = registerUser("push-host-cancel@example.com", host = true)
        val wallboxId = host.api.createWallbox(newWallbox(name = "CancelBox")).id
        host.api.setWallboxOnline(wallboxId, true)
        host.api.registerDevice(RegisterDeviceRequest(PushPlatform.ANDROID, "host-cancel-token"))

        val driver = registerUser("push-driver-cancel@example.com")
        val booking = driver.api.createBooking(
            CreateBookingRequest(wallboxId, inFuture(3, 9), inFuture(3, 10)),
        ).booking!!
        push.sent.clear()

        driver.api.cancelBooking(booking.id)
        assertTrue(
            push.sent.any {
                it.token == "host-cancel-token" && it.payload.type == PushNotificationKind.TYPE_HOST_CANCELLED
            },
            push.sent.toString(),
        )
    }

    @Test
    fun problemReportNotifiesHost() = serverTest {
        val host = registerUser("push-host-problem@example.com", host = true)
        val wallboxId = host.api.createWallbox(newWallbox(name = "ProblemBox")).id
        host.api.setWallboxOnline(wallboxId, true)
        host.api.registerDevice(RegisterDeviceRequest(PushPlatform.ANDROID, "host-problem-token"))

        val driver = registerUser("push-driver-problem@example.com")
        val booking = driver.api.createBooking(
            CreateBookingRequest(wallboxId, inFuture(4, 9), inFuture(4, 10)),
        ).booking!!
        push.sent.clear()

        driver.api.reportBookingProblem(booking.id, ReportProblemRequest("Wallbox reagiert nicht"))
        assertTrue(
            push.sent.any {
                it.token == "host-problem-token" &&
                    it.payload.type == PushNotificationKind.TYPE_HOST_PROBLEM &&
                    it.payload.body.contains("Wallbox reagiert nicht")
            },
            push.sent.toString(),
        )
    }

    @Test
    fun devicesRequireAuth() = serverTest {
        val res = raw.post("/api/v1/devices") {
            contentType(ContentType.Application.Json)
            setBody("""{"platform":"android","token":"x"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun emptyTokenRejected() = serverTest {
        val user = registerUser("push-empty@example.com")
        val token = user.store.accessToken()!!
        val res = raw.post("/api/v1/devices") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"platform":"android","token":"   "}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
    }
}
