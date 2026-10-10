import Foundation

public struct Entry: Identifiable, Sendable, Equatable {
    public let id: String
    public var createdAt: Date
    public var updatedAt: Date
    public var timeZoneID: String
    public var mood: Mood
    public var tags: [String]
    public var comment: String?
    public var location: LocationSample?
    public var source: EntrySource

    public init(
        id: String,
        createdAt: Date,
        updatedAt: Date,
        timeZoneID: String,
        mood: Mood,
        tags: [String],
        comment: String?,
        location: LocationSample?,
        source: EntrySource
    ) {
        self.id = id
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.timeZoneID = timeZoneID
        self.mood = mood
        self.tags = tags
        self.comment = comment
        self.location = location
        self.source = source
    }
}

public enum EntryValidator {
    public static let maxTags = 3
    public static let maxCommentLength = 1000

    public static func normalizedComment(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return nil }
        if trimmed.count <= maxCommentLength { return trimmed }
        return String(trimmed.prefix(maxCommentLength))
    }

    public static func normalizedTags(_ tags: [String], allowed: Set<String>) -> [String] {
        var seen: Set<String> = []
        var result: [String] = []
        for tag in tags {
            if allowed.contains(tag), seen.insert(tag).inserted, result.count < maxTags {
                result.append(tag)
            }
        }
        return result
    }
}

public enum Identifiers {
    public static func newID() -> String {
        UUID().uuidString.lowercased()
    }
}
