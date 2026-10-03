import Foundation

struct BookingRepository {
    private let client: APIClient

    init(client: APIClient = .shared) {
        self.client = client
    }

    func myBookings() async throws -> [Booking] {
        try await client.request(.myBookings())
    }

    func create(wallboxID: UUID, startTime: Date, endTime: Date) async throws -> Booking {
        try await client.request(.createBooking(wallboxID: wallboxID, startTime: startTime, endTime: endTime))
    }

    func cancel(id: UUID) async throws {
        try await client.requestEmpty(.cancelBooking(id: id))
    }

    func hostBookings() async throws -> [Booking] {
        try await client.request(.hostBookings())
    }

    func hostEarnings(from: Date, to: Date) async throws -> HostEarnings {
        try await client.request(.hostEarnings(from: from, to: to))
    }
}
