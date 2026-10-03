package app.chargeshare.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Farb-Tokens aus `docs/ChargeShare-App-Design.html`. */
object ChargeShareColors {
    val Primary = Color(0xFF0B6E4F)
    val PrimaryLight = Color(0xFFD4EDE5)
    val PrimarySurface = Color(0xFFE8F5EE)
    val Accent = Color(0xFFF0A500)
    val AccentLight = Color(0xFFFFF4CC)
    val AccentDark = Color(0xFFC87800)
    val OnPrimary = Color.White
    val OnAccent = Color(0xFF1B1B1B)
    val Error = Color(0xFFD0392B)
    val ErrorLight = Color(0xFFFDECEA)
    val Background = Color(0xFFF5F4F1)
    val Surface = Color.White
    val SurfaceVariant = Color(0xFFECEAE6)
    val Outline = Color(0xFFE5E3DE)
    val OnSurface = Color(0xFF1A1A1A)
    val OnSurfaceVariant = Color(0xFF555250)

    /** Statusfarbe „verfügbar“. */
    val Available = Color(0xFF1A8A55)
}

/** Abstände (Vielfache von 4 dp). */
object Spacing {
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val XLarge = 32.dp

    /** Mindestgröße für Klickflächen (Barrierefreiheit). */
    val MinTouchTarget = 48.dp
}

private val LightColors = lightColorScheme(
    primary = ChargeShareColors.Primary,
    onPrimary = ChargeShareColors.OnPrimary,
    primaryContainer = ChargeShareColors.PrimaryLight,
    onPrimaryContainer = ChargeShareColors.Primary,
    secondary = ChargeShareColors.Accent,
    onSecondary = ChargeShareColors.OnAccent,
    secondaryContainer = ChargeShareColors.AccentLight,
    onSecondaryContainer = ChargeShareColors.OnAccent,
    tertiary = ChargeShareColors.AccentDark,
    background = ChargeShareColors.Background,
    onBackground = ChargeShareColors.OnSurface,
    surface = ChargeShareColors.Surface,
    onSurface = ChargeShareColors.OnSurface,
    surfaceVariant = ChargeShareColors.SurfaceVariant,
    onSurfaceVariant = ChargeShareColors.OnSurfaceVariant,
    outline = ChargeShareColors.OnSurfaceVariant,
    outlineVariant = ChargeShareColors.Outline,
    error = ChargeShareColors.Error,
    onError = Color.White,
    errorContainer = ChargeShareColors.ErrorLight,
    onErrorContainer = ChargeShareColors.Error,
)

/** Eckenradien sm/md/lg/xl aus dem Design (10/14/20/26). */
private val ChargeShareShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Das Dunkel-Design aus dem Mockup ist noch nicht umgesetzt; es gilt immer das helle Schema. */
@Composable
fun ChargeShareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, shapes = ChargeShareShapes, content = content)
}
