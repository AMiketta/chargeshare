package app.chargeshare.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.chargeshare.presentation.theme.ThemeSettings

/**
 * Semantische Farben der App (Tokens aus `docs/ChargeShare-App-Design.html`, Hell und Dunkel).
 * Zugriff in Composables über [ChargeShareColors]; Material-Komponenten bekommen davon abgeleitet das `colorScheme`.
 *
 * Wo das Mockup nicht ausreicht (Textfarben auf dunklen Flächen), weicht die Dunkel-Variante bewusst ab, damit der
 * Kontrast stimmt: [primaryText], [error], [onSurfaceMuted] sind dort heller als im Mockup (siehe `ThemeTest`).
 */
@Immutable
class ChargeShareColorScheme(
    val isDark: Boolean,
    /** Füllfarbe für Hero, Buttons, aktive Pins und gewählte Chips (hinterlegt mit [onPrimary]). */
    val primary: Color,
    /** Grün als Text-/Icon-/Rahmenfarbe auf Flächen; im Dunkeln heller als [primary], sonst zu kontrastarm. */
    val primaryText: Color,
    val primaryLight: Color,
    val primarySurface: Color,
    val onPrimary: Color,
    val accent: Color,
    val accentLight: Color,
    /** Amber als Text auf [accentLight] bzw. auf der Fläche. */
    val accentDark: Color,
    val onAccent: Color,
    /** Text auf [accentLight] (z. B. Status „Ausstehend“). */
    val onAccentLight: Color,
    val error: Color,
    val errorLight: Color,
    val background: Color,
    val surface: Color,
    /** Zweite Flächenstufe (`--c-surface-2`), z. B. neutrale Icon-Hintergründe. */
    val surface2: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    /** Dritte Textstufe (inaktive Navigation, Platzhalter, gesperrte Elemente). */
    val onSurfaceMuted: Color,
    /** Statusfarbe „verfügbar“. */
    val available: Color,
)

/** Hell: Tokens aus `:root` des Mockups. */
val LightColors = ChargeShareColorScheme(
    isDark = false,
    primary = Color(0xFF0B6E4F),
    primaryText = Color(0xFF0B6E4F),
    primaryLight = Color(0xFFD4EDE5),
    primarySurface = Color(0xFFE8F5EE),
    onPrimary = Color.White,
    accent = Color(0xFFF0A500),
    accentLight = Color(0xFFFFF4CC),
    accentDark = Color(0xFFC87800),
    onAccent = Color(0xFF1B1B1B),
    onAccentLight = Color(0xFF1B1B1B),
    error = Color(0xFFD0392B),
    errorLight = Color(0xFFFDECEA),
    background = Color(0xFFF5F4F1),
    surface = Color.White,
    surface2 = Color(0xFFF5F4F1),
    surfaceVariant = Color(0xFFECEAE6),
    outline = Color(0xFFE5E3DE),
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF555250),
    onSurfaceMuted = Color(0xFF9E9B98),
    available = Color(0xFF1A8A55),
)

/** Dunkel: Tokens aus `[data-theme="dark"]` des Mockups, ergänzt um kontrastreichere Text-Varianten (siehe [ChargeShareColorScheme]). */
val DarkColors = ChargeShareColorScheme(
    isDark = true,
    primary = Color(0xFF0B6E4F),
    primaryText = Color(0xFF5ECC8A),
    primaryLight = Color(0xFF0D3824),
    primarySurface = Color(0xFF0D2B1F),
    onPrimary = Color.White,
    accent = Color(0xFFF0A500),
    accentLight = Color(0xFF2D2000),
    accentDark = Color(0xFFF0A500),
    onAccent = Color(0xFF1B1B1B),
    onAccentLight = Color(0xFFF0A500),
    error = Color(0xFFFF7A6B),
    errorLight = Color(0xFF3A1612),
    background = Color(0xFF141310),
    surface = Color(0xFF1E1D1A),
    surface2 = Color(0xFF272522),
    surfaceVariant = Color(0xFF302E2A),
    outline = Color(0xFF363330),
    onSurface = Color(0xFFE8E6E2),
    onSurfaceVariant = Color(0xFFA8A5A0),
    onSurfaceMuted = Color(0xFF8C8985),
    available = Color(0xFF5ECC8A),
)

/** Das aktive [ChargeShareColorScheme]; wird von [ChargeShareTheme] gesetzt. */
val LocalChargeShareColors = staticCompositionLocalOf { LightColors }

/**
 * Zugriff auf die Farben des aktuellen Themes (wie `MaterialTheme.colorScheme`): nur in Composables nutzbar,
 * nie in Konstanten zwischenspeichern – sonst bleibt die Farbe beim Wechsel Hell/Dunkel stehen.
 */
object ChargeShareColors {
    val IsDark: Boolean @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.isDark
    val Primary: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.primary
    val PrimaryText: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.primaryText
    val PrimaryLight: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.primaryLight
    val PrimarySurface: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.primarySurface
    val OnPrimary: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.onPrimary
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.accent
    val AccentLight: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.accentLight
    val AccentDark: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.accentDark
    val OnAccentLight: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.onAccentLight
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.onAccent
    val Error: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.error
    val ErrorLight: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.errorLight
    val Background: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.surface
    val Surface2: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.surface2
    val SurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.surfaceVariant
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.outline
    val OnSurface: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.onSurface
    val OnSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.onSurfaceVariant
    val OnSurfaceMuted: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.onSurfaceMuted
    val Available: Color @Composable @ReadOnlyComposable get() = LocalChargeShareColors.current.available
}

/** Schattenstufen des Designs (`--shadow-sm/md`), als Elevation in dp. */
object Elevation {
    /** Karten und Chips. */
    val Card = 2.dp

    /** Schwebende Elemente (Karten-Pins, Bottom-Sheet). */
    val Raised = 6.dp
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

/** Leitet das Material-`ColorScheme` aus den App-Farben ab, damit Dialoge, Textfelder, Switches usw. mitziehen. */
private fun ChargeShareColorScheme.toMaterial(): ColorScheme {
    val onPrimaryFill = if (isDark) Color(0xFF003822) else onPrimary
    // Material nutzt `primary` für Fokus, Cursor, Textbuttons und Switches – das braucht im Dunkeln das hellere Grün.
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primaryText,
        onPrimary = onPrimaryFill,
        primaryContainer = primaryLight,
        onPrimaryContainer = primaryText,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentLight,
        onSecondaryContainer = onAccentLight,
        tertiary = accentDark,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = Color.Transparent,
        outline = onSurfaceVariant,
        outlineVariant = outline,
        error = error,
        onError = if (isDark) Color(0xFF3A0A05) else Color.White,
        errorContainer = errorLight,
        onErrorContainer = error,
        surfaceDim = background,
        surfaceBright = surface,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface2,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceVariant,
    )
}

/** Eckenradien sm/md/lg/xl aus dem Design (10/14/20/26). */
private val ChargeShareShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/**
 * App-Theme in Hell oder Dunkel.
 *
 * @param darkTheme `true` = Dunkel-Schema; Standard folgt der Systemeinstellung. Die gewählte Einstellung der Nutzer
 * (Hell/Dunkel/System) löst [app.chargeshare.presentation.theme.ThemeMode.resolveDark] auf.
 */
@Composable
fun ChargeShareTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val typography = chargeShareTypography(rememberChargeShareFontFamily())
    CompositionLocalProvider(LocalChargeShareColors provides colors) {
        MaterialTheme(colorScheme = colors.toMaterial(), shapes = ChargeShareShapes, typography = typography, content = content)
    }
}

/** Ob gerade das dunkle Schema gilt: die Wahl in [settings] (Hell/Dunkel/System) zusammen mit der Systemeinstellung. */
@Composable
fun rememberIsDarkTheme(settings: ThemeSettings): Boolean {
    val mode by settings.mode.collectAsState()
    return mode.resolveDark(isSystemInDarkTheme())
}
