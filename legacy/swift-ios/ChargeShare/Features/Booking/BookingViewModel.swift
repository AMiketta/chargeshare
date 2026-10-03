import Foundation

@MainActor
@Observable
final class BookingViewModel {
    let wallbox: Wallbox

    var selectedDate: Date = Calendar.current.startOfDay(for: .now)
    var selectedStartTime: Date?
    var selectedDuration: Int = 2  // Stunden
    var selectedPayment: PaymentMethod = .applePay
    var availableSlots: [TimeSlot] = []
    var isLoading = false
    var isBooking = false
    var errorMessage: String?
    var createdBooking: Booking?

    private let repository = BookingRepository()

    init(wallbox: Wallbox) {
        self.wallbox = wallbox
    }

    // MARK: - Computed Properties

    var endTime: Date? {
        guard let start = selectedStartTime else { return nil }
        return Calendar.current.date(byAdding: .hour, value: selectedDuration, to: start)
    }

    var estimatedKWh: Double? {
        guard selectedStartTime != nil else { return nil }
        return wallbox.powerKW * Double(selectedDuration)
    }

    var estimatedCost: Decimal? {
        guard let kwh = estimatedKWh else { return nil }
        return wallbox.pricePerKWh * Decimal(kwh)
    }

    var canBook: Bool {
        !isBooking && selectedStartTime != nil && endTime != nil
    }

    var availableDates: [Date] {
        (0..<7).compactMap { offset in
            Calendar.current.date(byAdding: .day, value: offset, to: Calendar.current.startOfDay(for: .now))
        }
    }

    var durationOptions: [Int] {
        [1, 2, 3, 4, 6, 8]
    }

    // MARK: - Public Actions

    func loadSlots() async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }
        availableSlots = []
        selectedStartTime = nil

        do {
            availableSlots = try await WallboxRepository().slots(id: wallbox.id, date: selectedDate)
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func book() async {
        guard canBook, let start = selectedStartTime, let end = endTime else { return }
        isBooking = true
        errorMessage = nil
        defer { isBooking = false }

        do {
            let booking = try await repository.create(
                wallboxID: wallbox.id,
                startTime: start,
                endTime: end
            )
            createdBooking = booking
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - Payment Method

    enum PaymentMethod: String, CaseIterable, Identifiable {
        case applePay
        case card
        case paypal

        var id: String { rawValue }

        var icon: String {
            switch self {
            case .applePay: return "apple.logo"
            case .card: return "creditcard.fill"
            case .paypal: return "p.circle.fill"
            }
        }

        var label: String {
            switch self {
            case .applePay: return "Apple Pay"
            case .card: return "Kreditkarte"
            case .paypal: return "PayPal"
            }
        }
    }
}
