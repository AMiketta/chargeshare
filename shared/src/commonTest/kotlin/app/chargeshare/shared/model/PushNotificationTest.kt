package app.chargeshare.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import app.chargeshare.shared.api.ChargeShareJson

class PushNotificationTest {

    @Test
    fun hostAndDriverTemplatesAreGerman() {
        val unlock = PushNotificationKind.HostUnlockReminder("b1", "Garage Nord")
        assertEquals("Wallbox freischalten", unlock.title)
        assertTrue(unlock.body.contains("Garage Nord"))

        val reminder = PushNotificationKind.DriverBookingReminder("b1", "Garage Nord")
        assertEquals("chargeshare://booking/b1?action=cancel", reminder.deepLink)
        assertTrue(reminder.body.contains("Storno"))

        val problem = PushNotificationKind.HostProblemReported("b1", "Garage Nord", "Kein Strom")
        assertTrue(problem.body.contains("Kein Strom"))
    }

    @Test
    fun registerDeviceRequestSerializesSnakeFreeEnums() {
        val json = ChargeShareJson.encodeToString(RegisterDeviceRequest(PushPlatform.ANDROID, "tok"))
        assertTrue(json.contains("\"android\""))
        assertTrue(json.contains("\"token\""))
    }

    @Test
    fun payloadRoundTrip() {
        val kind = PushNotificationKind.ChargingEnded("b9", "Box", forHost = false, actualKwh = 12.5)
        val payload = kind.toPayload()
        assertEquals(PushNotificationKind.TYPE_CHARGING_ENDED, payload.type)
        assertTrue(payload.body.contains("12.5"))
        val encoded = ChargeShareJson.encodeToString(payload)
        val decoded = ChargeShareJson.decodeFromString(PushPayload.serializer(), encoded)
        assertEquals(payload, decoded)
    }
}
