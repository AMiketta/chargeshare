import SwiftUI

struct EmptyStateView: View {
    var systemImage: String
    var title: String
    var subtitle: String?
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: systemImage)
                .font(.system(size: 56))
                .foregroundStyle(.csGreen.opacity(0.7))
                .symbolRenderingMode(.hierarchical)

            Text(title)
                .font(.headline)
                .multilineTextAlignment(.center)

            if let subtitle {
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle)
                }
                .buttonStyle(PrimaryButtonStyle())
                .padding(.top, 8)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity)
    }
}

#Preview {
    VStack(spacing: 32) {
        EmptyStateView(
            systemImage: "bolt.slash",
            title: "Keine Wallboxen gefunden",
            subtitle: "Versuche den Suchbereich zu vergrößern oder Filter anzupassen.",
            actionTitle: "Filter zurücksetzen",
            action: {}
        )

        EmptyStateView(
            systemImage: "calendar",
            title: "Keine Buchungen",
            subtitle: "Buche deinen ersten Ladepunkt auf der Karte."
        )
    }
}
