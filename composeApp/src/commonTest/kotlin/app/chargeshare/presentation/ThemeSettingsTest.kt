package app.chargeshare.presentation

import app.chargeshare.data.InMemoryPreferenceStorage
import app.chargeshare.presentation.theme.ThemeMode
import app.chargeshare.presentation.theme.ThemeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeSettingsTest {

    @Test
    fun `System folgt der Systemeinstellung, Hell und Dunkel sind fest`() {
        assertTrue(ThemeMode.System.resolveDark(systemDark = true))
        assertFalse(ThemeMode.System.resolveDark(systemDark = false))
        assertFalse(ThemeMode.Light.resolveDark(systemDark = true))
        assertTrue(ThemeMode.Dark.resolveDark(systemDark = false))
    }

    @Test
    fun `ohne gespeicherten Wert gilt System`() {
        assertEquals(ThemeMode.System, ThemeSettings(InMemoryPreferenceStorage()).mode.value)
    }

    @Test
    fun `unbekannter gespeicherter Wert faellt auf System zurueck`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStorage("neon"))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(null))
    }

    @Test
    fun `jeder Modus ueberlebt Speichern und Neustart`() {
        ThemeMode.entries.forEach { mode ->
            val storage = InMemoryPreferenceStorage()
            val settings = ThemeSettings(storage)

            settings.select(mode)

            assertEquals(mode, settings.mode.value)
            assertEquals(mode, ThemeSettings(storage).mode.value, "neue Instanz liest $mode aus dem Speicher")
        }
    }
}
