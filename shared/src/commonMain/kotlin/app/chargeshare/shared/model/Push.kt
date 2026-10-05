package app.chargeshare.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Plattform eines Push-Tokens. */
@Serializable
enum class PushPlatform {
    @SerialName("android") ANDROID,
    @SerialName("ios") IOS,
}

/** `POST /devices` – Gerätetoken beim Server registrieren (Upsert per Token). */
@Serializable
data class RegisterDeviceRequest(
    val platform: PushPlatform,
    val token: String,
)

/** `DELETE /devices` – Token abmelden (z. B. beim Logout). */
@Serializable
data class UnregisterDeviceRequest(
    val token: String,
)

/** `POST /bookings/{id}/problem` – Fahrer meldet dem Host ein Problem. */
@Serializable
data class ReportProblemRequest(
    /** Freitext, max. [PushLimits.PROBLEM_MESSAGE_MAX] Zeichen. */
    val message: String,
)

object PushLimits {
    const val TOKEN_MAX = 512
    const val PROBLEM_MESSAGE_MAX = 500
}

/**
 * Fachliche Push-Ereignisse inkl. deutscher Titel/Texte für Host und Fahrer.
 * Der Server baut daraus [PushPayload]-Nachrichten; Clients können denselben Katalog für In-App-Hinweise nutzen.
 */
sealed class PushNotificationKind {
    abstract val type: String
    abstract val bookingId: String
    abstract val title: String
    abstract val body: String
    /** Deep-Link-Intent, z. B. `chargeshare://booking/{id}?action=cancel`. */
    open val deepLink: String? get() = "chargeshare://booking/$bookingId"

    /** Host: Wallbox vor Ladebeginn freischalten. */
    data class HostUnlockReminder(
        override val bookingId: String,
        val wallboxName: String,
    ) : PushNotificationKind() {
        override val type: String get() = TYPE_HOST_UNLOCK
        override val title: String get() = "Wallbox freischalten"
        override val body: String get() =
            "Deine Buchung an „$wallboxName“ steht an – bitte die Wallbox rechtzeitig freischalten."
    }

    /** Host: nach dem Laden die tatsächlichen kWh eintragen (besonders ohne Zähler). */
    data class HostEnterKwhReminder(
        override val bookingId: String,
        val wallboxName: String,
    ) : PushNotificationKind() {
        override val type: String get() = TYPE_HOST_ENTER_KWH
        override val title: String get() = "kWh eintragen"
        override val body: String get() =
            "Der Ladevorgang an „$wallboxName“ ist beendet – bitte die geladenen kWh eintragen."
    }

    /** Host: Fahrer meldet ein Problem. */
    data class HostProblemReported(
        override val bookingId: String,
        val wallboxName: String,
        val message: String,
    ) : PushNotificationKind() {
        override val type: String get() = TYPE_HOST_PROBLEM
        override val title: String get() = "Problem gemeldet"
        override val body: String get() {
            val preview = message.trim().replace('\n', ' ').take(120)
            return "Fahrer meldet zu „$wallboxName“: $preview"
        }
    }

    /** Host: Buchung wurde storniert. */
    data class HostBookingCancelled(
        override val bookingId: String,
        val wallboxName: String,
    ) : PushNotificationKind() {
        override val type: String get() = TYPE_HOST_CANCELLED
        override val title: String get() = "Buchung storniert"
        override val body: String get() = "Die Buchung an „$wallboxName“ wurde storniert."
    }

    /** Fahrer: Erinnerung an bestätigte Buchung inkl. Storno-Deep-Link. */
    data class DriverBookingReminder(
        override val bookingId: String,
        val wallboxName: String,
    ) : PushNotificationKind() {
        override val type: String get() = TYPE_DRIVER_REMINDER
        override val title: String get() = "Buchung bestätigt"
        override val body: String get() =
            "Deine Buchung an „$wallboxName“ ist bestätigt. Tippe für Details oder Storno."
        override val deepLink: String get() = "chargeshare://booking/$bookingId?action=cancel"
    }

    /** Beide: Laden hat begonnen. */
    data class ChargingStarted(
        override val bookingId: String,
        val wallboxName: String,
        val forHost: Boolean,
    ) : PushNotificationKind() {
        override val type: String get() = TYPE_CHARGING_STARTED
        override val title: String get() = "Laden gestartet"
        override val body: String get() =
            if (forHost) "An „$wallboxName“ wurde ein Ladevorgang gestartet."
            else "Dein Ladevorgang an „$wallboxName“ hat begonnen."
    }

    /** Beide: Laden ist beendet. */
    data class ChargingEnded(
        override val bookingId: String,
        val wallboxName: String,
        val forHost: Boolean,
        val actualKwh: Double? = null,
    ) : PushNotificationKind() {
        override val type: String get() = TYPE_CHARGING_ENDED
        override val title: String get() = "Laden beendet"
        override val body: String get() {
            val kwh = actualKwh?.let { " (${formatKwh(it)} kWh)" }.orEmpty()
            return if (forHost) "Der Ladevorgang an „$wallboxName“ ist beendet$kwh."
            else "Dein Ladevorgang an „$wallboxName“ ist beendet$kwh."
        }
    }

    companion object {
        const val TYPE_HOST_UNLOCK = "host_unlock"
        const val TYPE_HOST_ENTER_KWH = "host_enter_kwh"
        const val TYPE_HOST_PROBLEM = "host_problem"
        const val TYPE_HOST_CANCELLED = "host_cancelled"
        const val TYPE_DRIVER_REMINDER = "driver_reminder"
        const val TYPE_CHARGING_STARTED = "charging_started"
        const val TYPE_CHARGING_ENDED = "charging_ended"

        private fun formatKwh(value: Double): String {
            val tenths = ((value * 10.0) + if (value >= 0) 0.5 else -0.5).toInt()
            val whole = tenths / 10
            val frac = kotlin.math.abs(tenths % 10)
            return if (frac == 0) whole.toString() else "$whole.$frac"
        }
    }
}

/** Serialisierbare Push-Nachricht (Datenpayload für FCM/APNs). */
@Serializable
data class PushPayload(
    val type: String,
    @SerialName("booking_id") val bookingId: String,
    val title: String,
    val body: String,
    @SerialName("deep_link") val deepLink: String? = null,
)

fun PushNotificationKind.toPayload(): PushPayload =
    PushPayload(
        type = type,
        bookingId = bookingId,
        title = title,
        body = body,
        deepLink = deepLink,
    )
