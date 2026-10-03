import Foundation
import SwiftUI

struct Booking: Identifiable, Codable, Sendable, Equatable {
    let id: UUID
    var wallboxID: UUID
    var driverID: UUID
    var wallbox: Wallbox?   // embedded for display
    var startTime: Date
    var endTime: Date
    var status: BookingStatus
    var estimatedKWh: Double?
    var actualKWh: Double?
    var pricePerKWh: Decimal
    var totalAmount: Decimal?
    var stripePaymentIntentID: String?
    var stripeClientSecret: String?
    var createdAt: Date

    var durationHours: Double {
        endTime.timeIntervalSince(startTime) / 3600
    }

    static func == (lhs: Booking, rhs: Booking) -> Bool { lhs.id == rhs.id }
}

enum BookingStatus: String, Codable, Sendable, CaseIterable {
    case pending
    case confirmed
    case active
    case completed
    case cancelled
    case failed

    var displayName: String {
        switch self {
        case .pending: return "Ausstehend"
        case .confirmed: return "Bestätigt"
        case .active: return "Aktiv"
        case .completed: return "Abgeschlossen"
        case .cancelled: return "Storniert"
        case .failed: return "Fehlgeschlagen"
        }
    }

    var color: Color {
        switch self {
        case .pending: return .orange
        case .confirmed: return .blue
        case .active: return .green
        case .completed: return .secondary
        case .cancelled: return .red
        case .failed: return .red
        }
    }

    var systemImage: String {
        switch self {
        case .pending: return "clock"
        case .confirmed: return "checkmark.circle"
        case .active: return "bolt.fill"
        case .completed: return "checkmark.circle.fill"
        case .cancelled: return "xmark.circle"
        case .failed: return "exclamationmark.circle"
        }
    }

    var isUpcoming: Bool { self == .pending || self == .confirmed }
    var isPast: Bool { self == .completed || self == .cancelled || self == .failed }
}
