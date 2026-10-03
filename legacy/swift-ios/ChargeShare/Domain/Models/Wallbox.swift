import Foundation
import CoreLocation

struct Wallbox: Identifiable, Codable, Sendable, Equatable, Hashable {
    let id: UUID
    var ownerID: UUID
    var name: String
    var address: String
    var city: String
    var postalCode: String
    var lat: Double
    var lng: Double
    var powerKW: Double
    var connectorType: ConnectorType
    var pricePerKWh: Decimal
    var isOnline: Bool
    var isMetered: Bool
    var ocppID: String?
    var model: String?
    var manufacturer: String?
    var photos: [URL]
    var ratingAvg: Double
    var ratingCount: Int
    var availability: WeeklyAvailability
    var distanceMeters: Double?  // computed from search

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    var formattedDistance: String? {
        guard let distanceMeters else { return nil }
        if distanceMeters < 1000 {
            return "\(Int(distanceMeters)) m"
        } else {
            let km = distanceMeters / 1000
            return String(format: "%.1f km", km)
        }
    }

    var fullAddress: String {
        "\(address), \(postalCode) \(city)"
    }

    // Equatable / Hashable: only use id
    static func == (lhs: Wallbox, rhs: Wallbox) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

enum ConnectorType: String, Codable, Sendable, CaseIterable {
    case type2
    case ccs
    case chademo
    case type1

    var displayName: String {
        switch self {
        case .type2: return "Type 2"
        case .ccs: return "CCS"
        case .chademo: return "CHAdeMO"
        case .type1: return "Type 1"
        }
    }

    var systemIcon: String { "bolt.fill" }
}

struct WeeklyAvailability: Codable, Sendable, Equatable, Hashable {
    var monday: DaySchedule?
    var tuesday: DaySchedule?
    var wednesday: DaySchedule?
    var thursday: DaySchedule?
    var friday: DaySchedule?
    var saturday: DaySchedule?
    var sunday: DaySchedule?

    /// Heutigen Zeitplan zurückgeben
    var today: DaySchedule? {
        let weekday = Calendar.current.component(.weekday, from: Date())
        switch weekday {
        case 2: return monday
        case 3: return tuesday
        case 4: return wednesday
        case 5: return thursday
        case 6: return friday
        case 7: return saturday
        case 1: return sunday
        default: return nil
        }
    }

    var todayDisplayString: String {
        guard let schedule = today else { return "Nicht verfügbar" }
        return "\(schedule.from) – \(schedule.to) Uhr"
    }
}

struct DaySchedule: Codable, Sendable, Equatable, Hashable {
    var from: String  // "09:00"
    var to: String    // "20:00"
}
