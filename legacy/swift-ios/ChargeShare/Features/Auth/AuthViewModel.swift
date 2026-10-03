import Foundation

@MainActor
@Observable
final class AuthViewModel {
    var email = ""
    var password = ""
    var confirmPassword = ""
    var name = ""
    var isLoading = false
    var errorMessage: String?

    private let authManager: AuthManager

    init(authManager: AuthManager = .shared) {
        self.authManager = authManager
    }

    // MARK: - Public Actions

    func login() async {
        guard validateLogin() else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            try await authManager.login(email: email.lowercased().trimmingCharacters(in: .whitespaces), password: password)
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func register() async {
        guard validateRegister() else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            try await authManager.register(
                email: email.lowercased().trimmingCharacters(in: .whitespaces),
                password: password,
                name: name.trimmingCharacters(in: .whitespaces)
            )
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func loginWithApple(identityToken: String, fullName: String?) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            try await authManager.loginWithApple(identityToken: identityToken, fullName: fullName)
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - Validation

    private func validateLogin() -> Bool {
        errorMessage = nil

        guard !email.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Bitte gib deine E-Mail-Adresse ein."
            return false
        }
        guard email.contains("@") else {
            errorMessage = "Bitte gib eine gültige E-Mail-Adresse ein."
            return false
        }
        guard !password.isEmpty else {
            errorMessage = "Bitte gib dein Passwort ein."
            return false
        }
        return true
    }

    private func validateRegister() -> Bool {
        errorMessage = nil

        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Bitte gib deinen Namen ein."
            return false
        }
        guard !email.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Bitte gib deine E-Mail-Adresse ein."
            return false
        }
        guard email.contains("@") else {
            errorMessage = "Bitte gib eine gültige E-Mail-Adresse ein."
            return false
        }
        guard password.count >= 8 else {
            errorMessage = "Das Passwort muss mindestens 8 Zeichen lang sein."
            return false
        }
        guard password == confirmPassword else {
            errorMessage = "Die Passwörter stimmen nicht überein."
            return false
        }
        return true
    }
}
