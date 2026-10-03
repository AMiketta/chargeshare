import SwiftUI

struct LoadingOverlay: ViewModifier {
    let isLoading: Bool

    func body(content: Content) -> some View {
        content.overlay {
            if isLoading {
                ZStack {
                    Color(.systemBackground)
                        .opacity(0.6)
                        .ignoresSafeArea()

                    VStack(spacing: 12) {
                        ProgressView()
                            .progressViewStyle(.circular)
                            .tint(.csGreen)
                            .scaleEffect(1.3)
                    }
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: isLoading)
    }
}

extension View {
    func loadingOverlay(_ isLoading: Bool) -> some View {
        self.modifier(LoadingOverlay(isLoading: isLoading))
    }
}

#Preview {
    Text("Content")
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .loadingOverlay(true)
}
