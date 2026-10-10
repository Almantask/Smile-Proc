import Foundation

public enum LocationKind: String, Codable, Sendable, Equatable {
    case fresh
    case cached
}

public struct LocationSample: Codable, Sendable, Equatable {
    public var latitude: Double
    public var longitude: Double
    public var horizontalAccuracy: Double
    public var capturedAt: Date
    public var kind: LocationKind

    public init(
        latitude: Double,
        longitude: Double,
        horizontalAccuracy: Double,
        capturedAt: Date,
        kind: LocationKind
    ) {
        self.latitude = latitude
        self.longitude = longitude
        self.horizontalAccuracy = horizontalAccuracy
        self.capturedAt = capturedAt
        self.kind = kind
    }
}
