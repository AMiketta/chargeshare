package app.chargeshare.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.chargeshare.di.AppContainer
import app.chargeshare.platform.BrowserReceiptSharer
import app.chargeshare.platform.BrowserUrlOpener
import app.chargeshare.platform.PlatformContext
import app.chargeshare.platform.createPreferenceStorage
import app.chargeshare.platform.createSecureTokenStore
import app.chargeshare.platform.loadTimeZoneDatabase
import app.chargeshare.ui.App
import kotlinx.browser.document
import kotlinx.browser.window

private const val DEFAULT_API_URL = "http://localhost:8080"

/** Server-Adresse: Query-Parameter `?api=https://…`, sonst `http://localhost:8080`. */
private fun apiBaseUrl(): String =
    window.location.search.removePrefix("?").split('&')
        .firstOrNull { it.startsWith("api=") }
        ?.removePrefix("api=")
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?: DEFAULT_API_URL

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    loadTimeZoneDatabase()
    val container = AppContainer(apiBaseUrl(), createSecureTokenStore(PlatformContext()), createPreferenceStorage(PlatformContext()), urlOpener = BrowserUrlOpener(), receiptSharer = BrowserReceiptSharer())
    // `document.body` ist in einem Browser immer gesetzt, sobald das Skript läuft.
    ComposeViewport(checkNotNull(document.body)) {
        App(container)
    }
}
