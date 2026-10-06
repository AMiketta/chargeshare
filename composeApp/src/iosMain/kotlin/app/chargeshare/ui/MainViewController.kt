package app.chargeshare.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.chargeshare.di.AppContainer
import app.chargeshare.platform.IosLocationProvider
import app.chargeshare.platform.IosUrlOpener
import app.chargeshare.platform.PlatformContext
import app.chargeshare.payment.IosPaymentBridge
import app.chargeshare.payment.IosPaymentLauncher
import app.chargeshare.payment.UnsupportedPaymentLauncher
import app.chargeshare.platform.createPreferenceStorage
import app.chargeshare.platform.createPushTokenSource
import app.chargeshare.platform.createPushTokenSource
import app.chargeshare.platform.createSecureTokenStore
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi

/** Im iOS-Simulator erreicht `localhost` den Mac, auf dem das Backend läuft. */
private const val DEFAULT_API_URL = "http://localhost:8080"

/** Wird von iosApp (SwiftUI-Hülle) aufgerufen. Swift kennt keine Standardparameter, daher gibt es zwei Überladungen. */
fun MainViewController(): UIViewController = MainViewController(DEFAULT_API_URL)

/** Ohne Zahlungsbrücke: Buchen funktioniert nur im Dev-Modus mit Fake-Zahlung des Servers. */
fun MainViewController(apiBaseUrl: String): UIViewController = MainViewController(apiBaseUrl, paymentBridge = null)

/**
 * @param apiBaseUrl Server-Wurzel, z. B. `https://api.chargeshare.app`
 * @param paymentBridge Swift-Brücke zum Stripe-PaymentSheet mit Apple Pay (`StripePaymentBridge` in `iosApp`);
 * `null` = keine Zahlungsfenster
 */
@OptIn(ExperimentalNativeApi::class)
fun MainViewController(apiBaseUrl: String, paymentBridge: IosPaymentBridge?): UIViewController = ComposeUIViewController {
    val container = remember {
        AppContainer(
            apiBaseUrl,
            createSecureTokenStore(PlatformContext()),
            createPreferenceStorage(PlatformContext()),
            // Debug-Framework (Xcode-Konfiguration „Debug“) = Debug-Angaben und Demo-Schnellanmeldung; Release nie.
            showDebugInfo = Platform.isDebugBinary,
            urlOpener = IosUrlOpener(),
            pushTokenSource = createPushTokenSource(PlatformContext()),
        )
    }
    val paymentLauncher = remember { paymentBridge?.let(::IosPaymentLauncher) ?: UnsupportedPaymentLauncher }
    val locationProvider = remember { IosLocationProvider() }
    App(container, paymentLauncher, locationProvider = locationProvider)
}
