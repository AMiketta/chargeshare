package app.chargeshare.data

import app.chargeshare.shared.api.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rohspeicher für den Token-String. Die plattformspezifischen Implementierungen sichern den Inhalt
 * (Android-Keystore, iOS-Keychain, Datei, localStorage); siehe `createSecureTokenStore`.
 */
interface TokenStorage {
    fun read(): String?

    /** `null` löscht den gespeicherten Wert. */
    fun write(value: String?)
}

/**
 * [TokenStore], der Access- und Refresh-Token dauerhaft in einem [TokenStorage] ablegt.
 * Die Tokens werden im Speicher zwischengehalten, damit nicht jeder Request den Speicher liest.
 */
class PersistedTokenStore(private val storage: TokenStorage) : TokenStore {
    private val mutex = Mutex()
    private var loaded = false
    private var access: String? = null
    private var refresh: String? = null

    override suspend fun accessToken(): String? = mutex.withLock { ensureLoaded(); access }

    override suspend fun refreshToken(): String? = mutex.withLock { ensureLoaded(); refresh }

    override suspend fun save(accessToken: String, refreshToken: String) = mutex.withLock {
        access = accessToken
        refresh = refreshToken
        loaded = true
        storage.write("$accessToken$SEPARATOR$refreshToken")
    }

    override suspend fun clear() = mutex.withLock {
        access = null
        refresh = null
        loaded = true
        storage.write(null)
    }

    private fun ensureLoaded() {
        if (loaded) return
        val parts = storage.read()?.split(SEPARATOR)
        if (parts != null && parts.size == 2 && parts.all { it.isNotBlank() }) {
            access = parts[0]
            refresh = parts[1]
        }
        loaded = true
    }

    private companion object {
        // Tokens (JWT/Hex) enthalten nie Zeilenumbrüche.
        const val SEPARATOR = "\n"
    }
}
