import Foundation

@MainActor
@Observable
final class ProfileViewModel {
    var isLoading = false
    var totalSessions: Int = 0
    var totalKWh: Double = 0
    var avgRating: Double = 0
    var errorMessage: String?

    private let bookingRepo = BookingRepository()

    func load() async {
        isLoading = true
        defer { isLoading = false }

        do {
            let bookings = try await bookingRepo.myBookings()
            let completed = bookings.filter { $0.status == .completed }
            totalSessions = completed.count
            totalKWh = completed.compactMap { $0.actualKWh }.reduce(0, +)
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
