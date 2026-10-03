package app.chargeshare.platform

import app.chargeshare.data.PersistedTokenStore
import app.chargeshare.data.TokenStorage
import app.chargeshare.shared.api.TokenStore
import kotlinx.browser.localStorage

actual fun createSecureTokenStore(context: PlatformContext): TokenStore =
    PersistedTokenStore(LocalStorageTokenStorage())

/**
 * Speichert den Token-String im `localStorage`. Das ist **für XSS-Angriffe lesbar** – eine bekannte Einschränkung
 * von Web-Clients ohne httpOnly-Cookies; die Content-Security-Policy der Seite muss das abfangen.
 */
private class LocalStorageTokenStorage : TokenStorage {
    override fun read(): String? = localStorage.getItem(KEY)

    override fun write(value: String?) {
        if (value == null) localStorage.removeItem(KEY) else localStorage.setItem(KEY, value)
    }

    private companion object {
        const val KEY = "chargeshare.tokens"
    }
}
