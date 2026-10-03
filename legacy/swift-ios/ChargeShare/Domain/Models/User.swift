import Foundation

struct User: Identifiable, Codable, Sendable, Equatable {
    let id: UUID
    var email: String
    var name: String
    var phone: String?
    var role: UserRole
    var avatarURL: URL?
    var isVerified: Bool
    var stripeAccountID: String?
    var createdAt: Date

    /// Initialen für Avatar-Anzeige (bis zu 2 Zeichen)
    var initials: String {
        let parts = name.split(separator: " ")
        if parts.count >= 2 {
            return String(parts[0].prefix(1)) + String(parts[1].prefix(1))
        }
        return String(name.prefix(2)).uppercased()
    }
}

enum UserRole: String, Codable, Sendable, CaseIterable {
    case driver
    case host
    case both
    case admin

    var displayName: String {
        switch self {
        case .driver: return "Fahrer"
        case .host: return "Host"
        case .both: return "Fahrer & Host"
        case .admin: return "Administrator"
        }
    }

    var isHost: Bool {
        self == .host || self == .both || self == .admin
    }
}
