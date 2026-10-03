import Foundation

@MainActor
@Observable
final class HostDashboardViewModel {
    var earnings: HostEarnings?
    var recentBookings: [Booking] = []
    var myWallboxes: [Wallbox] = []
    var isLoading = false
    var errorMessage: String?

    private let bookingRepo = BookingRepository()
    private let wallboxRepo = WallboxRepository()

    var isOnline: Bool {
        myWallboxes.first?.isOnline ?? false
    }

    var greetingName: String {
        AuthManager.shared.currentUser.map { String($0.name.split(separator: " ").first ?? Substring($0.name)) } ?? "Host"
    }

    // MARK: - Public Actions

    func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let monthStart = Date.startOfCurrentMonth
        let now = Date.now

        do {
            async let earningsTask = bookingRepo.hostEarnings(from: monthStart, to: now)
            async let bookingsTask = bookingRepo.hostBookings()

            let (loadedEarnings, loadedBookings) = try await (earningsTask, bookingsTask)
            earnings = loadedEarnings
            recentBookings = Array(loadedBookings.prefix(10))
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func toggleWallbox(_ wallbox: Wallbox) async {
        // Optimistic UI
        if let index = myWallboxes.firstIndex(where: { $0.id == wallbox.id }) {
            myWallboxes[index].isOnline.toggle()
        }

        do {
            let updated = try await wallboxRepo.toggleOnline(id: wallbox.id, online: !wallbox.isOnline)
            if let index = myWallboxes.firstIndex(where: { $0.id == updated.id }) {
                myWallboxes[index] = updated
            }
        } catch {
            // Revert on failure
            if let index = myWallboxes.firstIndex(where: { $0.id == wallbox.id }) {
                myWallboxes[index].isOnline = wallbox.isOnline
            }
            errorMessage = (error as? NetworkError)?.errorDescription ?? error.localizedDescription
        }
    }

    // MARK: - Computed Stats

    var deltaPercent: Double? {
        guard let earnings else { return nil }
        guard earnings.totalAmount > 0 else { return nil }
        let prev = earnings.totalAmount - earnings.monthAmount
        guard prev > 0 else { return nil }
        return NSDecimalNumber(decimal: earnings.monthAmount - prev).doubleValue /
               NSDecimalNumber(decimal: prev).doubleValue * 100
    }
}
