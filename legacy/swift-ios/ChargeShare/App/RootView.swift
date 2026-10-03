import SwiftUI

struct RootView: View {
    @Environment(AuthManager.self) var authManager

    var body: some View {
        Group {
            if authManager.isAuthenticated {
                MainTabView()
                    .transition(.opacity)
            } else {
                AuthView()
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: authManager.isAuthenticated)
    }
}

#Preview {
    RootView()
        .environment(AuthManager.shared)
        .environment(LocationManager.shared)
}
