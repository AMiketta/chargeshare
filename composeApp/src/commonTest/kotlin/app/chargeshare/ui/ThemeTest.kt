package app.chargeshare.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/** Prüft die Lesbarkeit der Farbpaare (WCAG-Kontrast) in beiden Schemata. */
class ThemeTest {

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + CONTRAST_OFFSET) / (dark + CONTRAST_OFFSET)
    }

    private fun assertReadable(name: String, text: Color, background: Color, minimum: Float = NORMAL_TEXT) {
        val ratio = contrast(text, background)
        assertTrue(ratio >= minimum, "$name: Kontrast $ratio < $minimum")
    }

    @Test
    fun `Dunkel Schema hat lesbare Textfarben`() = with(DarkColors) {
        assertReadable("Text auf Fläche", onSurface, surface)
        assertReadable("Text auf Hintergrund", onSurface, background)
        assertReadable("Text 2 auf Fläche", onSurfaceVariant, surface)
        assertReadable("Text 2 auf Variante", onSurfaceVariant, surfaceVariant)
        assertReadable("Text 3 auf Fläche", onSurfaceMuted, surface)
        assertReadable("Grüner Text auf Fläche", primaryText, surface)
        assertReadable("Grüner Text auf Grün-Fläche", primaryText, primarySurface)
        assertReadable("Grüner Text auf Grün-Hell", primaryText, primaryLight)
        assertReadable("Weiß auf Grün", onPrimary, primary)
        assertReadable("Amber-Text auf Amber-Fläche", accentDark, accentLight)
        assertReadable("Status-Text auf Amber-Fläche", onAccentLight, accentLight)
        assertReadable("Schwarz auf Amber-Button", onAccent, accent)
        assertReadable("Fehler auf Fläche", error, surface)
        assertReadable("Fehler auf Fehler-Fläche", error, errorLight)
    }

    @Test
    fun `Hell Schema hat lesbare Textfarben`() = with(LightColors) {
        assertReadable("Text auf Fläche", onSurface, surface)
        assertReadable("Text 2 auf Fläche", onSurfaceVariant, surface)
        assertReadable("Grüner Text auf Fläche", primaryText, surface)
        assertReadable("Grüner Text auf Grün-Fläche", primaryText, primarySurface)
        assertReadable("Weiß auf Grün", onPrimary, primary)
        assertReadable("Status-Text auf Amber-Fläche", onAccentLight, accentLight)
        assertReadable("Schwarz auf Amber-Button", onAccent, accent)
        assertReadable("Fehler auf Fläche", error, surface)
        // Amber-Summe ist große Schrift (titleLarge): 3:1 genügt.
        assertReadable("Amber-Summe", accentDark, accentLight, LARGE_TEXT)
    }

    @Test
    fun `Dunkel und Hell unterscheiden sich in Hintergrund und Text`() {
        assertTrue(DarkColors.background.luminance() < LightColors.background.luminance())
        assertTrue(DarkColors.onSurface.luminance() > LightColors.onSurface.luminance())
    }

    private companion object {
        const val CONTRAST_OFFSET = 0.05f
        const val NORMAL_TEXT = 4.5f
        const val LARGE_TEXT = 3f
    }
}
