import ComposeApp
import PassKit
import StripePaymentSheet
import UIKit

/// Brücke zwischen der Kotlin-App (`IosPaymentLauncher`) und dem Stripe-iOS-SDK.
///
/// Zeigt das Stripe-PaymentSheet; bei `useApplePay` mit Apple Pay (Merchant-ID aus `GET /api/v1/payments/config`).
/// ACHTUNG: Auf Linux nicht kompilierbar und nicht getestet – siehe README, Abschnitt „Zahlung“.
final class StripePaymentBridge: NSObject, IosPaymentBridge {
    /// Rückkehr-URL für Authentifizierungen mit Weiterleitung (3-D-Secure-Fallback); das URL-Schema steht in `project.yml`.
    private static let returnURL = "chargeshare://stripe-redirect"
    private static let applePayNetworks: [PKPaymentNetwork] = [.visa, .masterCard, .amex, .girocard]

    /// Hält das Sheet, solange es offen ist (sonst wird es sofort freigegeben).
    private var paymentSheet: PaymentSheet?

    func canUseApplePay() -> Bool {
        PKPaymentAuthorizationController.canMakePayments(usingNetworks: Self.applePayNetworks)
    }

    func presentPaymentSheet(request: IosPaymentRequest, callback: IosPaymentCallback) {
        DispatchQueue.main.async {
            guard let presenter = Self.topViewController() else {
                callback.onFailed(message: "Kein ViewController zum Anzeigen des Zahlungsfensters gefunden")
                return
            }
            STPAPIClient.shared.publishableKey = request.publishableKey

            var configuration = PaymentSheet.Configuration()
            configuration.merchantDisplayName = request.merchantDisplayName
            configuration.returnURL = Self.returnURL
            // Ohne `applePay` zeigt das Sheet keinen Apple-Pay-Button – das entspricht der Auswahl „Karte und weitere“.
            if request.useApplePay, let merchantId = request.applePayMerchantId {
                configuration.applePay = .init(merchantId: merchantId, merchantCountryCode: request.merchantCountryCode)
            }

            let sheet = PaymentSheet(paymentIntentClientSecret: request.clientSecret, configuration: configuration)
            self.paymentSheet = sheet
            sheet.present(from: presenter) { [weak self] result in
                self?.paymentSheet = nil
                switch result {
                case .completed:
                    callback.onCompleted()
                case .canceled:
                    callback.onCanceled()
                case .failed(let error):
                    callback.onFailed(message: error.localizedDescription)
                }
            }
        }
    }

    /// Oberster sichtbarer ViewController der aktiven Szene (Compose liegt in einem `UIViewController` unter SwiftUI).
    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
