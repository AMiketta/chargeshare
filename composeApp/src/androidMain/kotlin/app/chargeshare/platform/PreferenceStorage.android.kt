package app.chargeshare.platform

import android.content.Context
import app.chargeshare.data.PreferenceStorage

actual fun createPreferenceStorage(context: PlatformContext): PreferenceStorage {
    val androidContext = requireNotNull(context.handle as? Context) { "Android braucht einen Context in PlatformContext" }
    return SharedPreferencesStorage(androidContext.applicationContext)
}

/** Einstellungen in `SharedPreferences` (Datei `chargeshare_prefs`). */
private class SharedPreferencesStorage(context: Context) : PreferenceStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(key: String): String? = prefs.getString(key, null)

    override fun write(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    private companion object {
        const val PREFS_NAME = "chargeshare_prefs"
    }
}
