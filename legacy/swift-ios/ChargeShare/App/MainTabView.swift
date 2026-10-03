import SwiftUI

struct MainTabView: View {
    @Environment(AuthManager.self) var authManager

    var body: some View {
        TabView {
            // Karte (immer sichtbar)
            MapView()
                .tabItem {
                    Label("Karte", systemImage: "location.fill")
                }

            // Buchungen (immer sichtbar)
            BookingsListView()
                .tabItem {
                    Label("Buchungen", systemImage: "calendar")
                }

            // Host Dashboard (nur für Hosts sichtbar)
            if authManager.currentUser?.role.isHost == true {
                HostDashboardView()
                    .tabItem {
                        Label("Host", systemImage: "bolt.fill")
                    }
            }

            // Profil (immer sichtbar)
            ProfileView()
                .tabItem {
                    Label("Profil", systemImage: "person.fill")
                }
        }
        .tint(.csGreen)
    }
}

#Preview {
    MainTabView()
        .environment(AuthManager.shared)
        .environment(LocationManager.shared)
}
