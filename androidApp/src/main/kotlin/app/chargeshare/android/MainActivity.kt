package app.chargeshare.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.chargeshare.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ChargeShareApplication).container
        // Das PaymentSheet muss vor dem Start der Activity registriert werden, daher hier und nicht erst in der UI.
        val paymentLauncher = StripePaymentLauncher(this)
        setContent { App(container, paymentLauncher) }
    }
}
