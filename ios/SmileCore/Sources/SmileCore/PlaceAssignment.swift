import Foundation

public struct PlaceCluster: Identifiable, Sendable, Equatable, Hashable {
    public var id: String
    public var centroidLatitude: Double
    public var centroidLongitude: Double
    public var memberIDs: [String]
    public var labelID: String?

    public init(
        id: String,
        centroidLatitude: Double,
        centroidLongitude: Double,
        memberIDs: [String],
        labelID: String?
    ) {
        self.id = id
        self.centroidLatitude = centroidLatitude
        self.centroidLongitude = centroidLongitude
        self.memberIDs = memberIDs
        self.labelID = labelID
    }
}

public struct PlaceAssignment: Sendable, Equatable {
    public var entryClusters: [String: String]
    public var clusters: [PlaceCluster]

    public init(entryClusters: [String: String], clusters: [PlaceCluster]) {
        self.entryClusters = entryClusters
        self.clusters = clusters
    }

    public func cluster(id: String) -> PlaceCluster? {
        clusters.first { $0.id == id }
    }
}
