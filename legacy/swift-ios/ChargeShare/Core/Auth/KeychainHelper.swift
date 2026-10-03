import Foundation
import Security

struct KeychainHelper {
    // MARK: - Keys
    static let accessTokenKey = "accessToken"
    static let refreshTokenKey = "refreshToken"
    static let userIDKey = "userID"

    // MARK: - Save

    @discardableResult
    static func save(_ value: String, forKey key: String) -> Bool {
        guard let data = value.data(using: .utf8) else { return false }

        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: bundleID,
            kSecAttrAccount: key
        ]

        // Delete existing item first
        SecItemDelete(query as CFDictionary)

        let attributes: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: bundleID,
            kSecAttrAccount: key,
            kSecValueData: data,
            kSecAttrAccessible: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]

        let status = SecItemAdd(attributes as CFDictionary, nil)
        return status == errSecSuccess
    }

    // MARK: - Read

    static func get(forKey key: String) -> String? {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: bundleID,
            kSecAttrAccount: key,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let data = result as? Data,
              let string = String(data: data, encoding: .utf8) else {
            return nil
        }
        return string
    }

    // MARK: - Delete

    static func delete(forKey key: String) {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: bundleID,
            kSecAttrAccount: key
        ]
        SecItemDelete(query as CFDictionary)
    }

    static func deleteAll() {
        delete(forKey: accessTokenKey)
        delete(forKey: refreshTokenKey)
        delete(forKey: userIDKey)
    }

    // MARK: - Private

    private static var bundleID: String {
        Bundle.main.bundleIdentifier ?? "de.chargeschare.app"
    }
}
