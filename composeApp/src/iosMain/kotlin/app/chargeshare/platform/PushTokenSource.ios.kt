package app.chargeshare.platform

import app.chargeshare.shared.model.PushPlatform

/**
 * iOS-Platzhalter für APNs. Token-Registrierung folgt mit der nativen Permission-Anfrage
 * (siehe `README.de.md#push-benachrichtigungen-mvp`); bis dahin bleibt [currentToken] `null`.
 */
actual fun createPushTokenSource(context: PlatformContext): PushTokenSource = IosPushTokenSource()

private class IosPushTokenSource : PushTokenSource {
    override val platform: PushPlatform = PushPlatform.IOS
    override suspend fun requestPermission() = Unit
    override suspend fun currentToken(): String? = null
}
