package app.chargeshare.shared.api

/** Haelt Access-/Refresh-Token. Plattformen ersetzen das durch Keychain/EncryptedPrefs/localStorage. */
interface TokenStore {
    suspend fun accessToken(): String?
    suspend fun refreshToken(): String?
    suspend fun save(accessToken: String, refreshToken: String)
    suspend fun clear()
}

class InMemoryTokenStore(
    private var access: String? = null,
    private var refresh: String? = null,
) : TokenStore {
    override suspend fun accessToken(): String? = access
    override suspend fun refreshToken(): String? = refresh
    override suspend fun save(accessToken: String, refreshToken: String) {
        access = accessToken
        refresh = refreshToken
    }

    override suspend fun clear() {
        access = null
        refresh = null
    }
}
