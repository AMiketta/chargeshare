import SwiftUI

// MARK: - Loading Overlay

extension View {
    /// Zeigt einen Lade-Overlay über der View
    func loadingOverlay(_ isLoading: Bool) -> some View {
        self.modifier(LoadingOverlayModifier(isLoading: isLoading))
    }

    /// Zeigt einen Error-Alert wenn errorMessage gesetzt
    func errorAlert(message: Binding<String?>) -> some View {
        self.alert("Fehler", isPresented: Binding(
            get: { message.wrappedValue != nil },
            set: { if !$0 { message.wrappedValue = nil } }
        )) {
            Button("OK", role: .cancel) { message.wrappedValue = nil }
        } message: {
            if let msg = message.wrappedValue {
                Text(msg)
            }
        }
    }

    /// Karten-Stil mit Hintergrund, Ecken und Schatten
    func cardStyle(padding: CGFloat = 16) -> some View {
        self
            .padding(padding)
            .background(Color(.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    /// Abgerundeter Chip-Stil
    func chipStyle(isSelected: Bool = false, color: Color = .csGreen) -> some View {
        self
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(isSelected ? color : color.opacity(0.1))
            .foregroundStyle(isSelected ? .white : color)
            .clipShape(Capsule())
    }

    /// Versteckt View ohne Platz zu belegen
    @ViewBuilder
    func hidden(_ isHidden: Bool) -> some View {
        if isHidden {
            self.hidden()
        } else {
            self
        }
    }
}

// MARK: - LoadingOverlayModifier (private implementation)

private struct LoadingOverlayModifier: ViewModifier {
    let isLoading: Bool

    func body(content: Content) -> some View {
        content.overlay {
            if isLoading {
                ZStack {
                    Color(.systemBackground).opacity(0.4)
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.csGreen)
                        .scaleEffect(1.2)
                }
                .ignoresSafeArea()
            }
        }
    }
}
