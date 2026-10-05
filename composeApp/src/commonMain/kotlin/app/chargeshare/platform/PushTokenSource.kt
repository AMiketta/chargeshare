package app.chargeshare.platform

import app.chargeshare.shared.model.PushPlatform

/**
 * Liefert das plattformspezifische Push-Token (FCM/APNs).
 * Ohne Firebase/APNs-Konfiguration bleibt [currentToken] `null` – die App registriert dann nichts.
 */
interface PushTokenSource {
    /** `null`, wenn die Plattform Push nicht unterstützt (Desktop/Web). */
    val platform: PushPlatform?

    /** Aktuelles Gerätetoken oder `null`, wenn keins verfügbar ist. */
    suspend fun currentToken(): String?

    /** Fragt die OS-Berechtigung an (iOS/Android 13+); No-Op, wenn nicht nötig. */
    suspend fun requestPermission()
}

object UnsupportedPushTokenSource : PushTokenSource {
    override val platform: PushPlatform? = null
    override suspend fun currentToken(): String? = null
    override suspend fun requestPermission() = Unit
}

expect fun createPushTokenSource(context: PlatformContext): PushTokenSource
