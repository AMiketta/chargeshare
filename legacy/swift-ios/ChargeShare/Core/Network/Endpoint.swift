import Foundation

enum HTTPMethod: String {
    case GET
    case POST
    case PUT
    case DELETE
    case PATCH
}

struct Endpoint {
    var path: String
    var method: HTTPMethod
    var queryItems: [URLQueryItem]?
    var body: Data?
    var requiresAuth: Bool

    // MARK: - Auth Endpoints

    static func login(email: String, password: String) -> Endpoint {
        let body = try? JSONEncoder().encode(["email": email, "password": password])
        return Endpoint(path: "/auth/login", method: .POST, body: body, requiresAuth: false)
    }

    static func register(email: String, password: String, name: String) -> Endpoint {
        let payload = ["email": email, "password": password, "name": name]
        let body = try? JSONEncoder().encode(payload)
        return Endpoint(path: "/auth/register", method: .POST, body: body, requiresAuth: false)
    }

    static func loginWithApple(identityToken: String, fullName: String?) -> Endpoint {
        var payload: [String: String] = ["identity_token": identityToken]
        if let name = fullName { payload["full_name"] = name }
        let body = try? JSONEncoder().encode(payload)
        return Endpoint(path: "/auth/apple", method: .POST, body: body, requiresAuth: false)
    }

    static func refreshToken(_ token: String) -> Endpoint {
        let body = try? JSONEncoder().encode(["refresh_token": token])
        return Endpoint(path: "/auth/refresh", method: .POST, body: body, requiresAuth: false)
    }

    static func me() -> Endpoint {
        Endpoint(path: "/auth/me", method: .GET, requiresAuth: true)
    }

    static func updateMe(name: String?, phone: String?) -> Endpoint {
        var payload: [String: String] = [:]
        if let name { payload["name"] = name }
        if let phone { payload["phone"] = phone }
        let body = try? JSONEncoder().encode(payload)
        return Endpoint(path: "/auth/me", method: .PUT, body: body, requiresAuth: true)
    }

    // MARK: - Wallbox Endpoints

    static func wallboxes(
        lat: Double,
        lng: Double,
        radius: Double = 5000,
        connector: String? = nil,
        minPower: Double? = nil,
        maxPrice: Double? = nil
    ) -> Endpoint {
        var items: [URLQueryItem] = [
            URLQueryItem(name: "lat", value: String(lat)),
            URLQueryItem(name: "lng", value: String(lng)),
            URLQueryItem(name: "radius", value: String(radius))
        ]
        if let connector { items.append(URLQueryItem(name: "connector", value: connector)) }
        if let minPower { items.append(URLQueryItem(name: "min_power", value: String(minPower))) }
        if let maxPrice { items.append(URLQueryItem(name: "max_price", value: String(maxPrice))) }
        return Endpoint(path: "/wallboxes", method: .GET, queryItems: items, requiresAuth: false)
    }

    static func wallbox(id: UUID) -> Endpoint {
        Endpoint(path: "/wallboxes/\(id.uuidString.lowercased())", method: .GET, requiresAuth: false)
    }

    static func wallboxSlots(id: UUID, date: Date) -> Endpoint {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        let dateString = formatter.string(from: date)
        let items = [URLQueryItem(name: "date", value: dateString)]
        return Endpoint(
            path: "/wallboxes/\(id.uuidString.lowercased())/slots",
            method: .GET,
            queryItems: items,
            requiresAuth: true
        )
    }

    static func wallboxReviews(id: UUID) -> Endpoint {
        Endpoint(path: "/wallboxes/\(id.uuidString.lowercased())/reviews", method: .GET, requiresAuth: false)
    }

    static func createWallbox(body: Data) -> Endpoint {
        Endpoint(path: "/wallboxes", method: .POST, body: body, requiresAuth: true)
    }

    static func toggleWallbox(id: UUID, online: Bool) -> Endpoint {
        let body = try? JSONEncoder().encode(["online": online])
        return Endpoint(
            path: "/wallboxes/\(id.uuidString.lowercased())/online",
            method: .PATCH,
            body: body,
            requiresAuth: true
        )
    }

    static func createReview(wallboxID: UUID, rating: Int, comment: String?) -> Endpoint {
        var payload: [String: Any] = ["rating": rating]
        if let comment { payload["comment"] = comment }
        let body = try? JSONSerialization.data(withJSONObject: payload)
        return Endpoint(
            path: "/wallboxes/\(wallboxID.uuidString.lowercased())/reviews",
            method: .POST,
            body: body,
            requiresAuth: true
        )
    }

    // MARK: - Booking Endpoints

    static func createBooking(wallboxID: UUID, startTime: Date, endTime: Date) -> Endpoint {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        let payload = CreateBookingPayload(
            wallboxId: wallboxID.uuidString.lowercased(),
            startTime: startTime,
            endTime: endTime
        )
        let body = try? encoder.encode(payload)
        return Endpoint(path: "/bookings", method: .POST, body: body, requiresAuth: true)
    }

    static func myBookings() -> Endpoint {
        Endpoint(path: "/bookings", method: .GET, requiresAuth: true)
    }

    static func cancelBooking(id: UUID) -> Endpoint {
        Endpoint(path: "/bookings/\(id.uuidString.lowercased())", method: .DELETE, requiresAuth: true)
    }

    static func hostBookings() -> Endpoint {
        Endpoint(path: "/host/bookings", method: .GET, requiresAuth: true)
    }

    static func hostEarnings(from: Date, to: Date) -> Endpoint {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withFullDate]
        let items = [
            URLQueryItem(name: "from", value: formatter.string(from: from)),
            URLQueryItem(name: "to", value: formatter.string(from: to))
        ]
        return Endpoint(path: "/host/earnings", method: .GET, queryItems: items, requiresAuth: true)
    }
}

// MARK: - Private Helpers

private struct CreateBookingPayload: Encodable {
    let wallboxId: String
    let startTime: Date
    let endTime: Date
}
