import SwiftUI

@main
struct ChargeShareApp: App {
    @State private var authManager = AuthManager.shared
    @State private var locationManager = LocationManager.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(authManager)
                .environment(locationManager)
        }
    }
}
