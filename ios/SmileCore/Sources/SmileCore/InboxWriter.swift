import Foundation

public struct InboxEntryFile: Sendable, Equatable {
    public var version: Int
    public var id: String
    public var createdAt: Date
    public var timeZoneID: String
    public var mood: Mood
    public var source: EntrySource

    public init(version: Int = 1, id: String, createdAt: Date, timeZoneID: String, mood: Mood, source: EntrySource) {
        self.version = version
        self.id = id
        self.createdAt = createdAt
        self.timeZoneID = timeZoneID
        self.mood = mood
        self.source = source
    }
}

public struct InboxLocationFile: Sendable, Equatable {
    public var version: Int
    public var id: String
    public var location: LocationSample

    public init(version: Int = 1, id: String, location: LocationSample) {
        self.version = version
        self.id = id
        self.location = location
    }
}

public enum InboxCodec {
    public static func encodeEntry(_ file: InboxEntryFile) -> Data {
        let value = JSONValue.object([
            ("v", .int(file.version)),
            ("id", .string(file.id)),
            ("createdAt", .string(InstantFormat.format(file.createdAt))),
            ("timeZoneID", .string(file.timeZoneID)),
            ("mood", .int(file.mood.rawValue)),
            ("source", .string(file.source.rawValue)),
        ])
        return CanonicalJson.encodeData(value)
    }

    public static func encodeLocation(_ file: InboxLocationFile) -> Data {
        let value = JSONValue.object([
            ("v", .int(file.version)),
            ("id", .string(file.id)),
            ("latitude", .double(file.location.latitude)),
            ("longitude", .double(file.location.longitude)),
            ("horizontalAccuracy", .double(file.location.horizontalAccuracy)),
            ("capturedAt", .string(InstantFormat.format(file.location.capturedAt))),
            ("kind", .string(file.location.kind.rawValue)),
        ])
        return CanonicalJson.encodeData(value)
    }

    public static func parseEntry(_ data: Data) throws -> InboxEntryFile {
        let object = try CanonicalJson.jsonObject(data)
        guard CanonicalJson.intValue(object["v"]) == 1,
              let id = object["id"] as? String,
              let createdText = object["createdAt"] as? String,
              let zone = object["timeZoneID"] as? String,
              let moodValue = CanonicalJson.intValue(object["mood"]),
              let mood = Mood(raw: moodValue),
              let sourceText = object["source"] as? String,
              let source = EntrySource(rawValue: sourceText)
        else { throw SmileError.invalidContent }
        return InboxEntryFile(
            id: id,
            createdAt: try InstantFormat.parse(createdText),
            timeZoneID: zone,
            mood: mood,
            source: source
        )
    }

    public static func parseLocation(_ data: Data) throws -> InboxLocationFile {
        let object = try CanonicalJson.jsonObject(data)
        guard CanonicalJson.intValue(object["v"]) == 1,
              let id = object["id"] as? String,
              let latitude = CanonicalJson.doubleValue(object["latitude"]),
              let longitude = CanonicalJson.doubleValue(object["longitude"]),
              let accuracy = CanonicalJson.doubleValue(object["horizontalAccuracy"]),
              let capturedText = object["capturedAt"] as? String,
              let kindText = object["kind"] as? String,
              let kind = LocationKind(rawValue: kindText)
        else { throw SmileError.invalidContent }
        return InboxLocationFile(
            id: id,
            location: LocationSample(
                latitude: latitude,
                longitude: longitude,
                horizontalAccuracy: accuracy,
                capturedAt: try InstantFormat.parse(capturedText),
                kind: kind
            )
        )
    }
}

public struct InboxWriter: Sendable {
    public var directory: URL

    public init(directory: URL) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        FileProtection.excludeFromBackup(directory)
        FileProtection.applyCompleteProtection(at: directory)
        self.directory = directory
    }

    public func writeEntry(_ file: InboxEntryFile) throws {
        let url = directory.appendingPathComponent("\(file.id).entry.json")
        try InboxCodec.encodeEntry(file).write(to: url, options: Self.writeOptions)
        FileProtection.applyCompleteProtection(at: url)
    }

    public func writeLocation(_ file: InboxLocationFile) throws {
        let url = directory.appendingPathComponent("\(file.id).loc.json")
        try InboxCodec.encodeLocation(file).write(to: url, options: Self.writeOptions)
        FileProtection.applyCompleteProtection(at: url)
    }

    public static var writeOptions: Data.WritingOptions {
        #if os(iOS)
        return [.completeFileProtectionUnlessOpen, .withoutOverwriting]
        #else
        return [.withoutOverwriting]
        #endif
    }
}
