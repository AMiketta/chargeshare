package app.chargeshare.platform

import app.chargeshare.shared.api.TokenStore

/**
 * Plattform-Kontext für die Initialisierung. Auf Android ist [handle] der `Context`, auf allen anderen Plattformen `null`.
 * (Bewusst eine normale Klasse statt `expect class`: `Context` ist abstrakt und passt nicht zu einer finalen `expect`-Klasse.)
 */
class PlatformContext(val handle: Any? = null)

/**
 * Erstellt den dauerhaften Token-Speicher der Plattform:
 * Android = AES-GCM mit Android-Keystore, iOS = Keychain, Desktop = Datei im Benutzerverzeichnis, Web = localStorage.
 */
expect fun createSecureTokenStore(context: PlatformContext): TokenStore
