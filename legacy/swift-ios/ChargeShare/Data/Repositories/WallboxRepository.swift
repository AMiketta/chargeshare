import Foundation

struct WallboxRepository {
    private let client: APIClient

    init(client: APIClient = .shared) {
        self.client = client
    }

    func nearby(
        lat: Double,
        lng: Double,
        radius: Double = 5000,
        connector: ConnectorType? = nil,
        minPower: Double? = nil,
        maxPrice: Double? = nil
    ) async throws -> [Wallbox] {
        try await client.request(
            .wallboxes(
                lat: lat,
                lng: lng,
                radius: radius,
                connector: connector?.rawValue,
                minPower: minPower,
                maxPrice: maxPrice
            )
        )
    }

    func detail(id: UUID) async throws -> Wallbox {
        try await client.request(.wallbox(id: id))
    }

    func slots(id: UUID, date: Date) async throws -> [TimeSlot] {
        try await client.request(.wallboxSlots(id: id, date: date))
    }

    func reviews(id: UUID) async throws -> [Review] {
        try await client.request(.wallboxReviews(id: id))
    }

    func toggleOnline(id: UUID, online: Bool) async throws -> Wallbox {
        try await client.request(.toggleWallbox(id: id, online: online))
    }

    func create(_ request: CreateWallboxRequest) async throws -> Wallbox {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        encoder.dateEncodingStrategy = .iso8601
        let body = try encoder.encode(request)
        let endpoint = Endpoint(path: "/wallboxes", method: .POST, body: body, requiresAuth: true)
        return try await client.request(endpoint)
    }

    func createReview(wallboxID: UUID, rating: Int, comment: String?) async throws -> Review {
        try await client.request(.createReview(wallboxID: wallboxID, rating: rating, comment: comment))
    }
}
