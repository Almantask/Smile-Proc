import Foundation

public enum PlaceKind: String, Codable, Sendable, Equatable, Hashable, CaseIterable {
    case home
    case work
    case other
}

public struct PlaceLabel: Identifiable, Sendable, Equatable {
    public var id: String
    public var name: String
    public var kind: PlaceKind
    public var anchorLatitude: Double
    public var anchorLongitude: Double
    public var createdAt: Date

    public init(
        id: String,
        name: String,
        kind: PlaceKind,
        anchorLatitude: Double,
        anchorLongitude: Double,
        createdAt: Date
    ) {
        self.id = id
        self.name = name
        self.kind = kind
        self.anchorLatitude = anchorLatitude
        self.anchorLongitude = anchorLongitude
        self.createdAt = createdAt
    }
}
