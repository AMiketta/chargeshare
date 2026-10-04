package app.chargeshare.presentation.theme

import app.chargeshare.data.PreferenceStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Darstellung, die der Nutzer im Profil wählt. */
enum class ThemeMode(val storageValue: String) {
    /** Folgt der Systemeinstellung des Geräts (Standard). */
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    /** Ob das dunkle Schema gelten soll; [systemDark] ist die aktuelle Systemeinstellung. */
    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        /** Liest einen gespeicherten Wert; unbekannt oder fehlend ergibt [System]. */
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: System
    }
}

/**
 * Hält die gewählte [ThemeMode] und speichert sie dauerhaft in einem [PreferenceStorage].
 * Wird einmal pro Prozess im `AppContainer` erzeugt.
 */
class ThemeSettings(private val storage: PreferenceStorage) {
    private val _mode = MutableStateFlow(ThemeMode.fromStorage(storage.read(KEY_THEME_MODE)))
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun select(mode: ThemeMode) {
        storage.write(KEY_THEME_MODE, mode.storageValue)
        _mode.value = mode
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
