import Foundation

struct AuthRepository {
    private let client: APIClient

    init(client: APIClient = .shared) {
        self.client = client
    }

    func login(email: String, password: String) async throws -> AuthResponse {
        try await client.request(.login(email: email, password: password))
    }

    func register(email: String, password: String, name: String) async throws -> AuthResponse {
        try await client.request(.register(email: email, password: password, name: name))
    }

    func loginWithApple(identityToken: String, fullName: String?) async throws -> AuthResponse {
        try await client.request(.loginWithApple(identityToken: identityToken, fullName: fullName))
    }

    func refreshToken(refreshToken: String) async throws -> AuthResponse {
        try await client.request(.refreshToken(refreshToken))
    }

    func me() async throws -> User {
        try await client.request(.me())
    }

    func updateMe(name: String?, phone: String?) async throws -> User {
        try await client.request(.updateMe(name: name, phone: phone))
    }
}
