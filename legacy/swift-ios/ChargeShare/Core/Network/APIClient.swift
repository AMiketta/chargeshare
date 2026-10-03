import Foundation

@Observable
final class APIClient: @unchecked Sendable {
    static let shared = APIClient()

    var baseURL = URL(string: "http://localhost:8080/api/v1")!

    private var accessToken: String? {
        KeychainHelper.get(forKey: "accessToken")
    }

    private init() {}

    // MARK: - Public Interface

    func request<T: Decodable>(_ endpoint: Endpoint) async throws -> T {
        let urlRequest = try buildRequest(endpoint)
        let (data, response) = try await perform(urlRequest)
        try validateResponse(response, data: data)
        return try decode(T.self, from: data)
    }

    func requestEmpty(_ endpoint: Endpoint) async throws {
        let urlRequest = try buildRequest(endpoint)
        let (data, response) = try await perform(urlRequest)
        try validateResponse(response, data: data)
    }

    func requestWithBody<Body: Encodable, Response: Decodable>(
        _ endpoint: Endpoint,
        body: Body
    ) async throws -> Response {
        var ep = endpoint
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        encoder.dateEncodingStrategy = .iso8601
        ep.body = try encoder.encode(body)
        let urlRequest = try buildRequest(ep)
        let (data, response) = try await perform(urlRequest)
        try validateResponse(response, data: data)
        return try decode(Response.self, from: data)
    }

    // MARK: - Private Helpers

    private func perform(_ request: URLRequest) async throws -> (Data, URLResponse) {
        do {
            return try await URLSession.shared.data(for: request)
        } catch let urlError as URLError {
            switch urlError.code {
            case .notConnectedToInternet, .networkConnectionLost:
                throw NetworkError.noInternet
            case .timedOut:
                throw NetworkError.timeout
            default:
                throw NetworkError.custom(urlError.localizedDescription)
            }
        }
    }

    private func buildRequest(_ endpoint: Endpoint) throws -> URLRequest {
        guard var components = URLComponents(url: baseURL.appendingPathComponent(endpoint.path), resolvingAgainstBaseURL: false) else {
            throw NetworkError.invalidURL
        }

        if let queryItems = endpoint.queryItems, !queryItems.isEmpty {
            components.queryItems = queryItems
        }

        guard let url = components.url else {
            throw NetworkError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = endpoint.method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 30

        if endpoint.requiresAuth, let token = accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        if let body = endpoint.body {
            request.httpBody = body
        }

        return request
    }

    private func validateResponse(_ response: URLResponse, data: Data) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.custom("Ungültige Serverantwort")
        }

        switch httpResponse.statusCode {
        case 200...299:
            return
        case 401:
            throw NetworkError.unauthorized
        case 404:
            throw NetworkError.notFound
        default:
            // Versuche API-Fehlermeldung zu lesen
            if let apiError = try? JSONDecoder().decode(APIErrorResponse.self, from: data) {
                throw NetworkError.serverError(httpResponse.statusCode, apiError.error)
            }
            throw NetworkError.serverError(httpResponse.statusCode, nil)
        }
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .iso8601
        do {
            return try decoder.decode(type, from: data)
        } catch let decodingError as DecodingError {
            throw NetworkError.decodingError(decodingError.localizedDescription)
        }
    }
}
