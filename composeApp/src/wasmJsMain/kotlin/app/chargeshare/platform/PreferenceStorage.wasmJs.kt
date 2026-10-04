package app.chargeshare.platform

import app.chargeshare.data.PreferenceStorage
import kotlinx.browser.localStorage

actual fun createPreferenceStorage(context: PlatformContext): PreferenceStorage = LocalStoragePreferences()

/** Einstellungen im `localStorage` des Browsers (Schlüssel mit Präfix `chargeshare.`). */
private class LocalStoragePreferences : PreferenceStorage {
    override fun read(key: String): String? = localStorage.getItem(PREFIX + key)

    override fun write(key: String, value: String?) {
        if (value == null) localStorage.removeItem(PREFIX + key) else localStorage.setItem(PREFIX + key, value)
    }

    private companion object {
        const val PREFIX = "chargeshare."
    }
}
