import SwiftUI
import ComposeApp

/// Duenne Huelle: die komplette UI liegt in Kotlin (composeApp, Compose Multiplatform).
struct ComposeView: UIViewControllerRepresentable {
    /// Wird vom Simulator-Mac aus erreicht; fuer echte Geraete/Produktion die Server-Adresse eintragen.
    private static let apiBaseUrl = "http://localhost:8080"

    /// Die Kotlin-Seite (`IosPaymentLauncher`) haelt die Bruecke fest; hier wird sie nur einmal erzeugt.
    private let paymentBridge = StripePaymentBridge()

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(apiBaseUrl: Self.apiBaseUrl, paymentBridge: paymentBridge)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
    }
}
