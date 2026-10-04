package app.chargeshare.platform

import app.chargeshare.data.PreferenceStorage

/**
 * Erstellt den dauerhaften Einstellungsspeicher der Plattform (nicht für Geheimnisse):
 * Android = `SharedPreferences`, iOS = `NSUserDefaults`, Desktop = `java.util.prefs`, Web = `localStorage`.
 */
expect fun createPreferenceStorage(context: PlatformContext): PreferenceStorage
