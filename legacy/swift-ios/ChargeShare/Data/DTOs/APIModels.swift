import Foundation

// MARK: - Auth

struct AuthResponse: Codable, Sendable {
    let accessToken: String
    let refreshToken: String
    let user: User
}

// MARK: - Wallbox Requests

struct CreateWallboxRequest: Encodable, Sendable {
    let name: String
    let address: String
    let city: String
    let postalCode: String
    let lat: Double
    let lng: Double
    let powerKW: Double
    let connectorType: String
    let pricePerKWh: Decimal
    let isMetered: Bool
    let availability: WeeklyAvailability
}

// MARK: - Booking Requests

struct CreateBookingRequest: Encodable, Sendable {
    let wallboxId: String
    let startTime: Date
    let endTime: Date

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(wallboxId, forKey: .wallboxId)
        try container.encode(startTime.iso8601String, forKey: .startTime)
        try container.encode(endTime.iso8601String, forKey: .endTime)
    }

    enum CodingKeys: String, CodingKey {
        case wallboxId = "wallbox_id"
        case startTime = "start_time"
        case endTime = "end_time"
    }
}

// MARK: - Slots

struct TimeSlot: Identifiable, Codable, Sendable, Equatable {
    let startTime: Date
    let endTime: Date
    let available: Bool

    var id: String { startTime.iso8601String }
}

// MARK: - Host Earnings

struct HostEarnings: Codable, Sendable {
    let totalAmount: Decimal
    let monthAmount: Decimal
    let sessionCount: Int
    let avgRating: Double
    let dailyBreakdown: [DailyEarning]
}

struct DailyEarning: Codable, Sendable, Identifiable {
    let date: Date
    let amount: Decimal
    let sessions: Int

    var id: String { date.iso8601String }

    /// Double-Wert für Charts
    var amountDouble: Double {
        NSDecimalNumber(decimal: amount).doubleValue
    }
}

// MARK: - Review Requests

struct CreateReviewRequest: Encodable, Sendable {
    let rating: Int
    let comment: String?
}

// MARK: - Profile Update

struct UpdateProfileRequest: Encodable, Sendable {
    let name: String?
    let phone: String?
}
