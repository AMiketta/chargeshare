import Foundation

@MainActor
@Observable
final class AuthManager {
    static let shared = AuthManager()

    private(set) var currentUser: User?
    private(set) var isAuthenticated: Bool = false

    var accessToken: String? {
        KeychainHelper.get(forKey: KeychainHelper.accessTokenKey)
    }

    private let repository = AuthRepository()

    private init() {
        loadUserFromStorage()
    }

    // MARK: - Public Interface

    func login(email: String, password: String) async throws {
        let response = try await repository.login(email: email, password: password)
        handleAuthResponse(response)
    }

    func loginWithApple(identityToken: String, fullName: String?) async throws {
        let response = try await repository.loginWithApple(identityToken: identityToken, fullName: fullName)
        handleAuthResponse(response)
    }

    func register(email: String, password: String, name: String) async throws {
        let response = try await repository.register(email: email, password: password, name: name)
        handleAuthResponse(response)
    }

    func refreshTokenIfNeeded() async throws {
        guard let refreshToken = KeychainHelper.get(forKey: KeychainHelper.refreshTokenKey) else {
            throw NetworkError.unauthorized
        }
        let response = try await repository.refreshToken(refreshToken: refreshToken)
        storeTokens(access: response.accessToken, refresh: response.refreshToken)
        currentUser = response.user
    }

    func logout() {
        currentUser = nil
        isAuthenticated = false
        KeychainHelper.deleteAll()
    }

    // MARK: - Private Helpers

    private func handleAuthResponse(_ response: AuthResponse) {
        storeTokens(access: response.accessToken, refresh: response.refreshToken)
        currentUser = response.user
        KeychainHelper.save(response.user.id.uuidString, forKey: KeychainHelper.userIDKey)
        isAuthenticated = true
    }

    private func storeTokens(access: String, refresh: String) {
        KeychainHelper.save(access, forKey: KeychainHelper.accessTokenKey)
        KeychainHelper.save(refresh, forKey: KeychainHelper.refreshTokenKey)
    }

    private func loadUserFromStorage() {
        // Prüfe ob ein Token im Keychain vorhanden ist
        guard KeychainHelper.get(forKey: KeychainHelper.accessTokenKey) != nil else {
            isAuthenticated = false
            return
        }
        // Token vorhanden → als authentifiziert markieren, User wird lazy geladen
        isAuthenticated = true

        // Lade User-Daten im Hintergrund
        Task {
            do {
                let user = try await repository.me()
                self.currentUser = user
            } catch {
                // Token ungültig → ausloggen
                self.logout()
            }
        }
    }
}
