package app.chargeshare.data

import app.chargeshare.platform.PushTokenSource
import app.chargeshare.shared.api.ChargeShareApi
import app.chargeshare.shared.model.RegisterDeviceRequest
import app.chargeshare.shared.model.UnregisterDeviceRequest
import kotlinx.coroutines.CancellationException

/**
 * Registriert und entfernt Push-Tokens beim Server.
 * Speichert das zuletzt registrierte Token lokal, damit Logout es abmelden kann.
 */
class PushDeviceRepository(
    private val api: ChargeShareApi,
    private val tokens: PushTokenSource,
    private val preferences: PreferenceStorage,
) {
    /**
     * Holt (falls vorhanden) das Gerätetoken und meldet es beim Server an.
     * Ohne Token oder ohne Plattform-Unterstützung ist das ein No-Op.
     */
    suspend fun syncRegistration() {
        val platform = tokens.platform ?: return
        try {
            tokens.requestPermission()
            val token = tokens.currentToken()?.trim().orEmpty()
            if (token.isEmpty()) return
            api.registerDevice(RegisterDeviceRequest(platform, token))
            preferences.write(KEY_TOKEN, token)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Registrierung ist best effort – fehlende Firebase-Konfiguration darf Login nicht blockieren.
        }
    }

    /** Meldet das lokal gespeicherte Token ab (best effort) und löscht den lokalen Eintrag. */
    suspend fun unregisterLocal() {
        val token = preferences.read(KEY_TOKEN)?.trim().orEmpty()
        if (token.isNotEmpty()) {
            try {
                api.unregisterDevice(UnregisterDeviceRequest(token))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Abmelden best effort
            }
        }
        preferences.write(KEY_TOKEN, null)
    }

    private companion object {
        const val KEY_TOKEN = "push.device_token"
    }
}
