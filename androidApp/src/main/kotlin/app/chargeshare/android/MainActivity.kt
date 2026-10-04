package app.chargeshare.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.chargeshare.data.Outcome
import app.chargeshare.ui.App
import app.chargeshare.ui.rememberIsDarkTheme

class MainActivity : ComponentActivity() {
    /** Die Activity läuft als `singleTask`: ein Deep Link der Rückkehr vom Stripe-Onboarding kommt hier an. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerDeepLink(intent)
    }

    private fun offerDeepLink(intent: Intent?) {
        val container = (application as ChargeShareApplication).container
        container.deepLinks.offer(intent?.data?.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Vor super.onCreate: tauscht das Startthema gegen Theme.ChargeShare; die Compose-Animation (SplashOverlay) setzt das Splash fort.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as ChargeShareApplication).container
        // Das PaymentSheet muss vor dem Start der Activity registriert werden, daher hier und nicht erst in der UI.
        val paymentLauncher = StripePaymentLauncher(this) {
            // Customer Session lässt das Zahlungsfenster gespeicherte Karten anzeigen; Fehler = Fenster ohne Karten-Speicher.
            (container.paymentRepository.customerSession() as? Outcome.Success)?.value
        }
        val photoPicker = AndroidPhotoPicker(this)
        // Kaltstart über einen Deep Link (z. B. App wurde im Browser-Onboarding beendet).
        offerDeepLink(intent)
        setContent {
            // Statusleisten-Symbole müssen zur gewählten Darstellung passen, nicht nur zum System (Profil: Hell/Dunkel/System).
            val darkTheme = rememberIsDarkTheme(container.themeSettings)
            DisposableEffect(darkTheme) {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                onDispose {}
            }
            App(container, paymentLauncher, photoPicker)
        }
    }
}
