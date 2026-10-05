package app.chargeshare.server.push

import app.chargeshare.shared.model.PushPayload
import app.chargeshare.shared.model.PushPlatform
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/** Eine ausgehende Push-Nachricht an ein Gerät. */
data class DevicePush(
    val userId: Uuid,
    val platform: PushPlatform,
    val token: String,
    val payload: PushPayload,
)

/**
 * Versand von Push-Nachrichten. Austauschbar: Entwicklung und CI nutzen [LoggingPushNotifier];
 * Livebetrieb später FCM/APNs (siehe `docs/push.md`).
 */
interface PushNotifier {
    fun send(messages: List<DevicePush>)

    companion object {
        fun logging(logBody: Boolean = true): PushNotifier = LoggingPushNotifier(logBody)
    }
}

/**
 * Schreibt Push-Nachrichten ins Log statt sie zu versenden – damit Tests und Dev ohne Firebase-Secrets laufen.
 */
class LoggingPushNotifier(private val logBody: Boolean) : PushNotifier {
    private val log = LoggerFactory.getLogger(LoggingPushNotifier::class.java)

    override fun send(messages: List<DevicePush>) {
        if (messages.isEmpty()) return
        for (m in messages) {
            if (logBody) {
                log.info(
                    "[dev-push] user={} platform={} type={} title={} body={} deepLink={} tokenSuffix=…{}",
                    m.userId,
                    m.platform.name.lowercase(),
                    m.payload.type,
                    m.payload.title,
                    m.payload.body,
                    m.payload.deepLink,
                    m.token.takeLast(8),
                )
            } else {
                log.info(
                    "[push] skipped (no provider): user={} type={} devices=1",
                    m.userId,
                    m.payload.type,
                )
            }
        }
    }
}

