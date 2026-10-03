import Foundation
import CoreLocation

@MainActor
@Observable
final class LocationManager: NSObject, CLLocationManagerDelegate {
    static let shared = LocationManager()

    var userLocation: CLLocationCoordinate2D?
    var authorizationStatus: CLAuthorizationStatus = .notDetermined

    private let clManager = CLLocationManager()

    override private init() {
        super.init()
        clManager.delegate = self
        clManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        authorizationStatus = clManager.authorizationStatus

        // Starte Update wenn bereits berechtigt
        if clManager.authorizationStatus == .authorizedWhenInUse ||
           clManager.authorizationStatus == .authorizedAlways {
            clManager.startUpdatingLocation()
        }
    }

    // MARK: - Public Interface

    func requestPermission() {
        clManager.requestWhenInUseAuthorization()
    }

    func startUpdating() {
        clManager.startUpdatingLocation()
    }

    func stopUpdating() {
        clManager.stopUpdatingLocation()
    }

    // MARK: - CLLocationManagerDelegate

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        Task { @MainActor in
            self.userLocation = location.coordinate
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            self.authorizationStatus = manager.authorizationStatus
            if manager.authorizationStatus == .authorizedWhenInUse ||
               manager.authorizationStatus == .authorizedAlways {
                manager.startUpdatingLocation()
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Silent fail – die App funktioniert ohne Standort mit Deutschland-Mitte
    }
}
