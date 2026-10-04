package app.chargeshare.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import app.chargeshare.ui.App
import app.chargeshare.ui.rememberIsDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ChargeShareApplication).container
        // Das PaymentSheet muss vor dem Start der Activity registriert werden, daher hier und nicht erst in der UI.
        val paymentLauncher = StripePaymentLauncher(this)
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
            App(container, paymentLauncher)
        }
    }
}
