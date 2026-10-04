package app.chargeshare.platform

import app.chargeshare.data.PreferenceStorage
import platform.Foundation.NSUserDefaults

actual fun createPreferenceStorage(context: PlatformContext): PreferenceStorage = UserDefaultsStorage()

/**
 * Einstellungen in `NSUserDefaults`.
 *
 * Ungetestet auf echter Hardware/Simulator: auf Linux ist nur die Kompilierung möglich.
 */
private class UserDefaultsStorage : PreferenceStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(key: String): String? = defaults.stringForKey(key)

    override fun write(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }
}
