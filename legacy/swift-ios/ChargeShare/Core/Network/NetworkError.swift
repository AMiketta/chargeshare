import Foundation

enum NetworkError: LocalizedError, Equatable {
    case unauthorized
    case notFound
    case serverError(Int, String?)
    case decodingError(String)
    case encodingError
    case invalidURL
    case noInternet
    case timeout
    case custom(String)

    var errorDescription: String? {
        switch self {
        case .unauthorized:
            return "Nicht autorisiert. Bitte melde dich erneut an."
        case .notFound:
            return "Die angeforderte Ressource wurde nicht gefunden."
        case .serverError(let code, let message):
            if let message {
                return "Serverfehler (\(code)): \(message)"
            }
            return "Serverfehler (\(code))."
        case .decodingError(let detail):
            return "Antwort konnte nicht verarbeitet werden: \(detail)"
        case .encodingError:
            return "Anfrage konnte nicht erstellt werden."
        case .invalidURL:
            return "Ungültige URL."
        case .noInternet:
            return "Keine Internetverbindung. Bitte überprüfe deine Netzwerkeinstellungen."
        case .timeout:
            return "Zeitüberschreitung. Bitte versuche es erneut."
        case .custom(let message):
            return message
        }
    }

    static func == (lhs: NetworkError, rhs: NetworkError) -> Bool {
        switch (lhs, rhs) {
        case (.unauthorized, .unauthorized): return true
        case (.notFound, .notFound): return true
        case (.encodingError, .encodingError): return true
        case (.invalidURL, .invalidURL): return true
        case (.noInternet, .noInternet): return true
        case (.timeout, .timeout): return true
        case (.serverError(let c1, let m1), .serverError(let c2, let m2)):
            return c1 == c2 && m1 == m2
        case (.decodingError(let d1), .decodingError(let d2)):
            return d1 == d2
        case (.custom(let s1), .custom(let s2)):
            return s1 == s2
        default:
            return false
        }
    }
}

/// API-Fehlerantwort vom Backend
struct APIErrorResponse: Codable {
    let error: String
    let code: String?
}
