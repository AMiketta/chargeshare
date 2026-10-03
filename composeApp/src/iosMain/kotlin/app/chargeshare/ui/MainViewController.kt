package app.chargeshare.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.chargeshare.di.AppContainer
import app.chargeshare.platform.PlatformContext
import app.chargeshare.platform.createSecureTokenStore
import platform.UIKit.UIViewController

/** Im iOS-Simulator erreicht `localhost` den Mac, auf dem das Backend läuft. */
private const val DEFAULT_API_URL = "http://localhost:8080"

/** Wird von iosApp (SwiftUI-Hülle) aufgerufen. Swift kennt keine Standardparameter, daher gibt es zwei Überladungen. */
fun MainViewController(): UIViewController = MainViewController(DEFAULT_API_URL)

/** @param apiBaseUrl Server-Wurzel, z. B. `https://api.chargeshare.app` */
fun MainViewController(apiBaseUrl: String): UIViewController = ComposeUIViewController {
    val container = remember { AppContainer(apiBaseUrl, createSecureTokenStore(PlatformContext())) }
    App(container)
}
