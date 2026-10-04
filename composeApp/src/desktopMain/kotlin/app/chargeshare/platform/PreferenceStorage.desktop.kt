package app.chargeshare.platform

import app.chargeshare.data.PreferenceStorage
import java.util.prefs.Preferences

actual fun createPreferenceStorage(context: PlatformContext): PreferenceStorage = JavaPreferenceStorage()

/** Einstellungen in `java.util.prefs` (Linux: `~/.java/.userPrefs`, macOS: plist, Windows: Registry). */
private class JavaPreferenceStorage : PreferenceStorage {
    private val prefs = Preferences.userRoot().node(NODE)

    override fun read(key: String): String? = prefs.get(key, null)

    override fun write(key: String, value: String?) {
        if (value == null) prefs.remove(key) else prefs.put(key, value)
        prefs.flush()
    }

    private companion object {
        const val NODE = "app/chargeshare"
    }
}
