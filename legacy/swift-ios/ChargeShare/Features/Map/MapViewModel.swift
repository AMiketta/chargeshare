import Foundation
import MapKit
import CoreLocation

@MainActor
@Observable
final class MapViewModel {
    var wallboxes: [Wallbox] = []
    var selectedWallbox: Wallbox?
    var cameraPosition: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 51.1657, longitude: 10.4515),
            span: MKCoordinateSpan(latitudeDelta: 5, longitudeDelta: 5)
        )
    )
    var isLoading = false
    var errorMessage: String?
    var searchText = ""
    var connectorFilter: ConnectorType?
    var showOffline = false

    private let repository = WallboxRepository()

    // MARK: - Computed

    var filteredWallboxes: [Wallbox] {
        var result = wallboxes

        // Offline-Filter
        if !showOffline {
            result = result.filter { $0.isOnline }
        }

        // Connector-Filter
        if let connector = connectorFilter {
            result = result.filter { $0.connectorType == connector }
        }

        // Text-Filter
        let query = searchText.trimmingCharacters(in: .whitespaces).lowercased()
        if !query.isEmpty {
            result = result.filter {
                $0.name.lowercased().contains(query) ||
                $0.city.lowercased().contains(query) ||
                $0.address.lowercased().contains(query)
            }
        }

        return result
    }

    // MARK: - Public Interface

    func loadNearby() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let location = LocationManager.shared.userLocation
        let lat = location?.latitude ?? 51.1657
        let lng = location?.longitude ?? 10.4515

        do {
            wallboxes = try await repository.nearby(lat: lat, lng: lng, radius: 10000)

            // Kamera auf User-Position setzen wenn verfügbar
            if let location {
                withAnimation {
                    cameraPosition = .region(
                        MKCoordinateRegion(
                            center: location,
                            span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
                        )
                    )
                }
            }
        } catch let error as NetworkError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func select(_ wallbox: Wallbox) {
        selectedWallbox = wallbox
        withAnimation {
            cameraPosition = .region(
                MKCoordinateRegion(
                    center: wallbox.coordinate,
                    span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)
                )
            )
        }
    }

    func deselect() {
        selectedWallbox = nil
    }

    func clearFilters() {
        connectorFilter = nil
        showOffline = false
        searchText = ""
    }

    var hasActiveFilters: Bool {
        connectorFilter != nil || showOffline || !searchText.trimmingCharacters(in: .whitespaces).isEmpty
    }
}
