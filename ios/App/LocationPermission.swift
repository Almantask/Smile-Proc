import CoreLocation

@MainActor
final class LocationPermission: NSObject, CLLocationManagerDelegate {
    static let shared = LocationPermission()

    private let manager = CLLocationManager()

    func requestWhenInUse() -> Bool {
        manager.delegate = self
        let status = manager.authorizationStatus
        if status == .denied || status == .restricted { return false }
        if status == .notDetermined {
            manager.requestWhenInUseAuthorization()
        }
        return true
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {}
}
