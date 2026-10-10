import CoreLocation
import Foundation
import SmileCore

public enum LocationCaptureContext: Sendable {
    case app
    case widget
}

private struct LocationFix: Sendable {
    var latitude: Double
    var longitude: Double
    var accuracy: Double
    var timestamp: Date
}

@MainActor
public final class QuickLocation: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<LocationSample?, Never>?
    private var best: LocationFix?
    private var finished = false
    private var timeoutTask: Task<Void, Never>?

    public static func capture(timeout: TimeInterval, enabled: Bool, context: LocationCaptureContext) async -> LocationSample? {
        guard enabled else { return nil }
        let locator = QuickLocation()
        return await locator.run(timeout: timeout, context: context)
    }

    nonisolated public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        let fix = LocationFix(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            accuracy: location.horizontalAccuracy,
            timestamp: location.timestamp
        )
        Task { @MainActor in
            self.absorb(fix)
        }
    }

    nonisolated public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            self.timedOut()
        }
    }

    private func run(timeout: TimeInterval, context: LocationCaptureContext) async -> LocationSample? {
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { return nil }
        if context == .widget && !CLLocationManager.isAuthorizedForWidgetUpdates {
            return cached(manager.location)
        }
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        return await withCheckedContinuation { continuation in
            self.continuation = continuation
            manager.requestLocation()
            timeoutTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                await self?.timedOut()
            }
        }
    }

    private func absorb(_ fix: LocationFix) {
        if best == nil || fix.accuracy < (best?.accuracy ?? .greatestFiniteMagnitude) {
            best = fix
        }
        if fix.accuracy <= 100 {
            finish(sample(from: fix, kind: .fresh))
        }
    }

    private func timedOut() {
        if let best, best.accuracy <= 200 {
            finish(sample(from: best, kind: .fresh))
        } else {
            finish(fallback())
        }
    }

    private func fallback() -> LocationSample? {
        cached(manager.location)
    }

    private func cached(_ location: CLLocation?) -> LocationSample? {
        guard let location else { return nil }
        let age = Date().timeIntervalSince(location.timestamp)
        guard age <= 600, location.horizontalAccuracy <= 200 else { return nil }
        return LocationSample(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            horizontalAccuracy: location.horizontalAccuracy,
            capturedAt: location.timestamp,
            kind: .cached
        )
    }

    private func sample(from fix: LocationFix, kind: LocationKind) -> LocationSample {
        LocationSample(
            latitude: fix.latitude,
            longitude: fix.longitude,
            horizontalAccuracy: fix.accuracy,
            capturedAt: fix.timestamp,
            kind: kind
        )
    }

    private func finish(_ sample: LocationSample?) {
        guard !finished else { return }
        finished = true
        timeoutTask?.cancel()
        continuation?.resume(returning: sample)
        continuation = nil
    }
}
