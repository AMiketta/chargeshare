package app.chargeshare.ui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.chargeshare.di.AppContainer
import app.chargeshare.platform.PlatformContext
import app.chargeshare.platform.createPreferenceStorage
import app.chargeshare.platform.createSecureTokenStore

/** Server-Adresse für den Desktop: Umgebungsvariable `CHARGESHARE_API_URL`, sonst `http://localhost:8080`. */
private val apiBaseUrl: String = System.getenv("CHARGESHARE_API_URL") ?: "http://localhost:8080"

/** Der Desktop-Build ist die Entwickler-Vorschau (kein Release-Artefakt), daher mit Debug-Angaben. */
fun main() = application {
    val container = AppContainer(
        apiBaseUrl,
        createSecureTokenStore(PlatformContext()),
        createPreferenceStorage(PlatformContext()),
        showDebugInfo = true,
    )
    Window(onCloseRequest = ::exitApplication, title = Strings.AppName) {
        App(container)
    }
}
