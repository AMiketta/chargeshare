package app.chargeshare.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.chargeshare.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 10.0.2.2 = Host-Rechner aus dem Android-Emulator
        setContent { App(apiBaseUrl = "http://10.0.2.2:8080") }
    }
}
