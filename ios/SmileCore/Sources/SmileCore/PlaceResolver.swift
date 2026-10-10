import Foundation

public struct PlaceResolver: Sendable {
    public var config: PatternConfig

    public init(config: PatternConfig) {
        self.config = config
    }

    public func resolve(entries: [Entry], labels: [PlaceLabel]) -> PlaceAssignment {
        let radius = config.placeRadiusMeters
        let maxAccuracy = config.placeMaxAccuracyMeters
        let ordered = entries.sorted { lhs, rhs in
            if lhs.createdAt != rhs.createdAt { return lhs.createdAt < rhs.createdAt }
            return lhs.id < rhs.id
        }
        let eligible = ordered.filter { entry in
            guard let location = entry.location else { return false }
            return location.horizontalAccuracy <= maxAccuracy
        }

        var assignment: [String: String] = [:]
        let labelsByID = Dictionary(uniqueKeysWithValues: labels.map { ($0.id, $0) })

        for entry in eligible {
            guard let location = entry.location else { continue }
            var bestID: String?
            var bestDistance: Double?
            for label in labels {
                let distance = Geo.haversineMeters(
                    lat1: location.latitude,
                    lon1: location.longitude,
                    lat2: label.anchorLatitude,
                    lon2: label.anchorLongitude
                )
                let clusterID = "p:\(label.id)"
                if isCloser(distance, clusterID, than: bestDistance, bestID: bestID, radius: radius) {
                    bestDistance = distance
                    bestID = clusterID
                }
            }
            if let bestID {
                assignment[entry.id] = bestID
            }
        }

        var unnamed: [String: [Entry]] = [:]
        var unnamedOrder: [String] = []
        for entry in eligible {
            if assignment[entry.id] != nil { continue }
            guard let location = entry.location else { continue }
            var bestID: String?
            var bestDistance: Double?
            for clusterID in unnamedOrder {
                let members = unnamed[clusterID] ?? []
                let lat = mean(members.compactMap { $0.location?.latitude })
                let lon = mean(members.compactMap { $0.location?.longitude })
                let distance = Geo.haversineMeters(
                    lat1: location.latitude,
                    lon1: location.longitude,
                    lat2: lat,
                    lon2: lon
                )
                if isCloser(distance, clusterID, than: bestDistance, bestID: bestID, radius: radius) {
                    bestDistance = distance
                    bestID = clusterID
                }
            }
            if bestID == nil {
                let created = "u:\(entry.id)"
                unnamed[created] = [entry]
                unnamedOrder.append(created)
                assignment[entry.id] = created
            } else if let bestID {
                unnamed[bestID, default: []].append(entry)
                assignment[entry.id] = bestID
            }
        }

        if !unnamed.isEmpty {
            var centroids: [String: (Double, Double)] = [:]
            for (clusterID, members) in unnamed {
                centroids[clusterID] = (
                    mean(members.compactMap { $0.location?.latitude }),
                    mean(members.compactMap { $0.location?.longitude })
                )
            }
            var rebuilt: [String: [Entry]] = [:]
            for clusterID in unnamed.keys {
                rebuilt[clusterID] = []
            }
            for entry in eligible {
                guard let current = assignment[entry.id], current.hasPrefix("u:") else { continue }
                guard let location = entry.location else { continue }
                var bestID: String?
                var bestDistance: Double?
                for (clusterID, centroid) in centroids {
                    let distance = Geo.haversineMeters(
                        lat1: location.latitude,
                        lon1: location.longitude,
                        lat2: centroid.0,
                        lon2: centroid.1
                    )
                    if isCloser(distance, clusterID, than: bestDistance, bestID: bestID, radius: radius) {
                        bestDistance = distance
                        bestID = clusterID
                    }
                }
                let chosen = bestID ?? current
                rebuilt[chosen, default: []].append(entry)
            }
            var finalUnnamed: [String: [Entry]] = [:]
            for members in rebuilt.values where !members.isEmpty {
                let earliest = members.min { lhs, rhs in
                    if lhs.createdAt != rhs.createdAt { return lhs.createdAt < rhs.createdAt }
                    return lhs.id < rhs.id
                }
                guard let earliest else { continue }
                let finalID = "u:\(earliest.id)"
                finalUnnamed[finalID, default: []].append(contentsOf: members)
            }
            for (clusterID, members) in finalUnnamed {
                for member in members {
                    assignment[member.id] = clusterID
                }
            }
        }

        var clusters: [PlaceCluster] = []
        let clusterIDs = Set(assignment.values).sorted()
        for clusterID in clusterIDs {
            let members = eligible.filter { assignment[$0.id] == clusterID }
            if members.isEmpty { continue }
            let sortedMembers = members.sorted { lhs, rhs in
                if lhs.createdAt != rhs.createdAt { return lhs.createdAt < rhs.createdAt }
                return lhs.id < rhs.id
            }
            let lat = mean(sortedMembers.compactMap { $0.location?.latitude })
            let lon = mean(sortedMembers.compactMap { $0.location?.longitude })
            let labelID = clusterID.hasPrefix("p:") ? String(clusterID.dropFirst(2)) : nil
            let knownLabel = labelID.flatMap { labelsByID[$0]?.id }
            clusters.append(
                PlaceCluster(
                    id: clusterID,
                    centroidLatitude: lat,
                    centroidLongitude: lon,
                    memberIDs: sortedMembers.map(\.id),
                    labelID: knownLabel
                )
            )
        }
        let sortedAssignment = Dictionary(uniqueKeysWithValues: assignment.sorted { $0.key < $1.key })
        return PlaceAssignment(entryClusters: sortedAssignment, clusters: clusters)
    }

    public func nearestLabel(
        latitude: Double,
        longitude: Double,
        labels: [PlaceLabel],
        excluding: String? = nil
    ) -> PlaceLabel? {
        let radius = config.placeRadiusMeters
        var best: PlaceLabel?
        var bestDistance: Double?
        for label in labels where label.id != excluding {
            let distance = Geo.haversineMeters(
                lat1: latitude,
                lon1: longitude,
                lat2: label.anchorLatitude,
                lon2: label.anchorLongitude
            )
            if isCloser(distance, label.id, than: bestDistance, bestID: best?.id, radius: radius) {
                bestDistance = distance
                best = label
            }
        }
        return best
    }

    public static func combinedAnchor(
        anchorLatitude: Double,
        anchorLongitude: Double,
        centroidLatitude: Double,
        centroidLongitude: Double,
        memberCount: Int
    ) -> (latitude: Double, longitude: Double) {
        let count = Double(max(memberCount, 0))
        let latitude = (anchorLatitude + centroidLatitude * count) / (count + 1)
        let longitude = (anchorLongitude + centroidLongitude * count) / (count + 1)
        return (latitude, longitude)
    }

    private func isCloser(
        _ distance: Double,
        _ clusterID: String,
        than bestDistance: Double?,
        bestID: String?,
        radius: Double
    ) -> Bool {
        guard distance <= radius else { return false }
        guard let bestDistance, let bestID else { return true }
        if distance < bestDistance { return true }
        if distance == bestDistance && clusterID < bestID { return true }
        return false
    }

    private func mean(_ values: [Double]) -> Double {
        guard !values.isEmpty else { return 0 }
        return values.reduce(0, +) / Double(values.count)
    }
}
