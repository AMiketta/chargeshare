import SwiftUI
import StripePaymentSheet

@main
struct ChargeShareApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
                // Rueckkehr aus Stripe-Authentifizierungen mit Weiterleitung (chargeshare://stripe-redirect)
                .onOpenURL { url in
                    _ = StripeAPI.handleURLCallback(with: url)
                }
        }
    }
}
