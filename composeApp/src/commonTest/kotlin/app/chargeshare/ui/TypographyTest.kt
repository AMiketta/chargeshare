package app.chargeshare.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class TypographyTest {

    private val typography = chargeShareTypography(FontFamily.Default)

    @Test
    fun `alle Stile nutzen die uebergebene Schriftfamilie`() {
        val styles = listOf(
            typography.headlineSmall, typography.titleLarge, typography.titleMedium, typography.titleSmall,
            typography.bodyLarge, typography.bodyMedium, typography.bodySmall,
            typography.labelLarge, typography.labelMedium, typography.labelSmall,
        )
        styles.forEach { assertEquals(FontFamily.Default, it.fontFamily) }
    }

    @Test
    fun `Ueberschriften sind extrafett, Fliesstext mittel`() {
        assertEquals(FontWeight.ExtraBold, typography.headlineSmall.fontWeight)
        assertEquals(FontWeight.ExtraBold, typography.titleLarge.fontWeight)
        assertEquals(FontWeight.Medium, typography.bodyLarge.fontWeight)
    }
}
