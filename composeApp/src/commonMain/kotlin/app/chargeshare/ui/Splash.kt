package app.chargeshare.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Markenfarben des Startbilds. Dieselben Werte stehen in `androidApp/src/main/res/values(-night)/themes.xml` und
 * `colors.xml`, damit der Wechsel vom System-Splash zur Compose-Animation nahtlos aussieht.
 */
object SplashColors {
    /** Hell: Markengrün mit Blitz in Amber. */
    val LightBackground = Color(0xFF0B6E4F)
    val LightBolt = Color(0xFFF0A500)

    /** Dunkel: App-Hintergrund mit hellgrünem Blitz. */
    val DarkBackground = Color(0xFF141310)
    val DarkBolt = Color(0xFF5ECC8A)

    fun background(isDark: Boolean): Color = if (isDark) DarkBackground else LightBackground

    fun bolt(isDark: Boolean): Color = if (isDark) DarkBolt else LightBolt
}

/** Der Startbildschirm geht erst weg, wenn die Animation durch ist **und** die Sitzung wiederhergestellt wurde. */
internal fun shouldDismissSplash(animationDone: Boolean, sessionRestoring: Boolean): Boolean = animationDone && !sessionRestoring

private const val PULSE_UP_MS = 220
private const val TEXT_DELAY_MS = 150L
private const val TEXT_FADE_MS = 350
private const val HOLD_MS = 350L
private const val FADE_OUT_MS = 250
private const val PULSE_SCALE = 1.12f
private val BOLT_SIZE = 96.dp

/**
 * Kurze Startanimation: Blitz pulsiert, der Markenname blendet ein, dann blendet das Ganze aus (insgesamt etwa eine Sekunde).
 * Liegt über der App und fängt Eingaben ab, bis es weg ist. Auf Android setzt es das System-Splash fort
 * (gleiche Farben, gleicher Blitz an derselben Stelle).
 *
 * @param sessionRestoring `true`, solange die gespeicherte Anmeldung noch geprüft wird
 * @param onFinished wird genau einmal aufgerufen, wenn der Overlay ausgeblendet ist
 */
@Composable
fun SplashOverlay(isDark: Boolean, sessionRestoring: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val pulse = remember { Animatable(1f) }
    val title = remember { Animatable(0f) }
    val overlay = remember { Animatable(1f) }
    var animationDone by remember { mutableStateOf(false) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                pulse.animateTo(PULSE_SCALE, tween(PULSE_UP_MS))
                pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
            launch {
                delay(TEXT_DELAY_MS)
                title.animateTo(1f, tween(TEXT_FADE_MS))
            }
        }
        delay(HOLD_MS)
        animationDone = true
    }
    LaunchedEffect(animationDone, sessionRestoring) {
        if (shouldDismissSplash(animationDone, sessionRestoring)) {
            overlay.animateTo(0f, tween(FADE_OUT_MS))
            currentOnFinished()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .alpha(overlay.value)
            .background(SplashColors.background(isDark))
            // Eingaben nicht an die App darunter durchlassen, solange der Splash steht.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            AppIcons.Bolt,
            contentDescription = null,
            tint = SplashColors.bolt(isDark),
            modifier = Modifier.size(BOLT_SIZE).scale(pulse.value),
        )
        Text(
            Strings.AppName,
            style = MaterialTheme.typography.headlineMedium,
            color = if (isDark) SplashColors.DarkBolt else Color.White,
            modifier = Modifier.alpha(title.value).clearAndSetSemantics {},
        )
    }
}
