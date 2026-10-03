package app.chargeshare.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.chargeshare.data.PersistedTokenStore
import app.chargeshare.data.TokenStorage
import app.chargeshare.shared.api.TokenStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual fun createSecureTokenStore(context: PlatformContext): TokenStore {
    val androidContext = requireNotNull(context.handle as? Context) { "Android braucht einen Context in PlatformContext" }
    return PersistedTokenStore(KeystoreTokenStorage(androidContext.applicationContext))
}

/**
 * Speichert den Token-String AES-GCM-verschlüsselt in `SharedPreferences`.
 * Der Schlüssel liegt im Android-Keystore und verlässt ihn nie.
 */
private class KeystoreTokenStorage(context: Context) : TokenStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): String? {
        val encoded = prefs.getString(KEY_VALUE, null) ?: return null
        return try {
            decrypt(Base64.decode(encoded, Base64.NO_WRAP))
        } catch (e: java.security.GeneralSecurityException) {
            // Schlüssel ungültig geworden (z. B. Gerätewechsel/Restore): gespeicherte Anmeldung verwerfen.
            prefs.edit().remove(KEY_VALUE).apply()
            null
        }
    }

    override fun write(value: String?) {
        val editor = prefs.edit()
        if (value == null) {
            editor.remove(KEY_VALUE)
        } else {
            editor.putString(KEY_VALUE, Base64.encodeToString(encrypt(value), Base64.NO_WRAP))
        }
        editor.apply()
    }

    private fun encrypt(plain: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        // Das Layout ist IV (12 Byte, vom Cipher erzeugt) gefolgt vom Chiffrat.
        return cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(data: ByteArray): String {
        val iv = data.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv)) }
        return String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "chargeshare_secure"
        const val KEY_VALUE = "tokens"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "chargeshare_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
