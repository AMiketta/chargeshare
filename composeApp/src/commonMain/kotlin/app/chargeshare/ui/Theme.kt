package app.chargeshare.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Markenfarben aus dem Konzept/Design. */
object ChargeShareColors {
    val Primary = Color(0xFF0B6E4F)
    val Accent = Color(0xFFF0A500)
    val OnPrimary = Color.White
    val OnAccent = Color(0xFF1B1B1B)
}

private val LightColors = lightColorScheme(
    primary = ChargeShareColors.Primary,
    onPrimary = ChargeShareColors.OnPrimary,
    secondary = ChargeShareColors.Accent,
    onSecondary = ChargeShareColors.OnAccent,
    tertiary = ChargeShareColors.Accent,
)

@Composable
fun ChargeShareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
