import SwiftUI
import AuthenticationServices

struct AuthView: View {
    @Environment(AuthManager.self) private var authManager
    @State private var viewModel = AuthViewModel()
    @State private var showLogin = false
    @State private var showRegister = false

    var body: some View {
        ZStack {
            // Hintergrund-Gradient
            LinearGradient(
                colors: [Color.csGreen, Color.csGreen.opacity(0.7)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                // Logo + Branding
                VStack(spacing: 20) {
                    ZStack {
                        Circle()
                            .fill(.white.opacity(0.15))
                            .frame(width: 100, height: 100)

                        Image(systemName: "bolt.fill")
                            .font(.system(size: 52, weight: .bold))
                            .foregroundStyle(.white)
                    }

                    VStack(spacing: 8) {
                        Text("ChargeShare")
                            .font(.system(size: 36, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)

                        Text("Lade privat, teile nachhaltig")
                            .font(.body)
                            .foregroundStyle(.white.opacity(0.85))
                    }
                }

                Spacer()

                // Auth-Buttons
                VStack(spacing: 16) {
                    // Apple Sign In
                    SignInWithAppleButton(.signIn) { request in
                        request.requestedScopes = [.fullName, .email]
                    } onCompletion: { result in
                        handleAppleSignIn(result)
                    }
                    .signInWithAppleButtonStyle(.white)
                    .frame(height: 54)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    // Divider
                    HStack {
                        Rectangle().fill(.white.opacity(0.3)).frame(height: 1)
                        Text("oder")
                            .font(.subheadline)
                            .foregroundStyle(.white.opacity(0.7))
                            .padding(.horizontal, 12)
                        Rectangle().fill(.white.opacity(0.3)).frame(height: 1)
                    }

                    // E-Mail Login
                    Button {
                        showLogin = true
                    } label: {
                        HStack {
                            Image(systemName: "envelope.fill")
                            Text("Mit E-Mail anmelden")
                        }
                    }
                    .buttonStyle(SecondaryButtonStyle())
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(.white.opacity(0.5), lineWidth: 1.5)
                    }

                    // Registrieren
                    Button {
                        showRegister = true
                    } label: {
                        Text("Neues Konto erstellen")
                            .foregroundStyle(.white.opacity(0.9))
                    }
                    .font(.subheadline.weight(.medium))
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 48)
            }
        }
        .sheet(isPresented: $showLogin) {
            LoginView()
        }
        .sheet(isPresented: $showRegister) {
            RegisterView()
        }
        .loadingOverlay(viewModel.isLoading)
        .errorAlert(message: Binding(
            get: { viewModel.errorMessage },
            set: { viewModel.errorMessage = $0 }
        ))
    }

    private func handleAppleSignIn(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let auth):
            guard let credential = auth.credential as? ASAuthorizationAppleIDCredential,
                  let tokenData = credential.identityToken,
                  let token = String(data: tokenData, encoding: .utf8) else {
                viewModel.errorMessage = "Apple Sign In ist fehlgeschlagen."
                return
            }
            let fullName: String?
            if let nameComponents = credential.fullName,
               let givenName = nameComponents.givenName {
                fullName = [givenName, nameComponents.familyName].compactMap { $0 }.joined(separator: " ")
            } else {
                fullName = nil
            }
            Task {
                await viewModel.loginWithApple(identityToken: token, fullName: fullName)
            }
        case .failure(let error):
            if (error as NSError).code != ASAuthorizationError.canceled.rawValue {
                viewModel.errorMessage = error.localizedDescription
            }
        }
    }
}

#Preview {
    AuthView()
        .environment(AuthManager.shared)
}
