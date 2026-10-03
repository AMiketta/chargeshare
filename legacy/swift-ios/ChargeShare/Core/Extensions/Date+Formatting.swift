import Foundation

extension Date {
    /// Relative Darstellung (z.B. "vor 2 Stunden", "gestern")
    var relativeString: String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        formatter.locale = Locale(identifier: "de_DE")
        return formatter.localizedString(for: self, relativeTo: .now)
    }

    /// Kurzdatum (z.B. "15. Jan")
    var shortDateString: String {
        formatted(.dateTime.day().month(.abbreviated).locale(Locale(identifier: "de_DE")))
    }

    /// Vollständiges Datum (z.B. "15. Januar 2024")
    var longDateString: String {
        formatted(.dateTime.day().month(.wide).year().locale(Locale(identifier: "de_DE")))
    }

    /// Uhrzeit (z.B. "14:30")
    var timeString: String {
        formatted(.dateTime.hour().minute().locale(Locale(identifier: "de_DE")))
    }

    /// Datum und Uhrzeit (z.B. "15. Jan, 14:30")
    var shortDateTimeString: String {
        formatted(
            .dateTime.day().month(.abbreviated).hour().minute()
            .locale(Locale(identifier: "de_DE"))
        )
    }

    /// Wochentag-Abkürzung (z.B. "Mo")
    var weekdayShort: String {
        formatted(.dateTime.weekday(.abbreviated).locale(Locale(identifier: "de_DE")))
    }

    /// ISO8601 String
    var iso8601String: String {
        ISO8601DateFormatter().string(from: self)
    }

    /// Datum ohne Uhrzeit (Mitternacht)
    var startOfDay: Date {
        Calendar.current.startOfDay(for: self)
    }

    /// Anfang des aktuellen Monats
    static var startOfCurrentMonth: Date {
        let calendar = Calendar.current
        let components = calendar.dateComponents([.year, .month], from: .now)
        return calendar.date(from: components) ?? .now
    }

    /// Nächste volle Stunde
    var nextFullHour: Date {
        let calendar = Calendar.current
        var components = calendar.dateComponents([.year, .month, .day, .hour], from: self)
        components.hour = (components.hour ?? 0) + 1
        components.minute = 0
        return calendar.date(from: components) ?? self
    }
}
