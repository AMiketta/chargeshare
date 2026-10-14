package app.chargeshare.platform

import app.chargeshare.shared.model.PushPlatform

/**
 * Android-Push-Token. Ohne `google-services.json` / Firebase Messaging bleibt das Token `null`.
 * Aktivierung: siehe `README.de.md#push-benachrichtigungen-mvp` und `androidApp/.../push/FcmMessagingService.kt`.
 */
actual fun createPushTokenSource(context: PlatformContext): PushTokenSource = AndroidPushTokenSource()

private class AndroidPushTokenSource : PushTokenSource {
    override val platform: PushPlatform = PushPlatform.ANDROID

    override suspend fun requestPermission() {
        // POST_NOTIFICATIONS (API 33+) wird ergänzt, sobald FCM aktiv ist.
    }

    override suspend fun currentToken(): String? = null
}
