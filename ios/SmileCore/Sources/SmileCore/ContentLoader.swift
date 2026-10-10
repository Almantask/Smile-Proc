import Foundation

public struct EmotionCore: Identifiable, Sendable, Equatable {
    public var id: String
    public var color: String
    public var fine: [String]

    public init(id: String, color: String, fine: [String]) {
        self.id = id
        self.color = color
        self.fine = fine
    }
}

public struct EmotionTagCatalog: Sendable, Equatable {
    public var version: Int
    public var cores: [EmotionCore]

    public init(version: Int, cores: [EmotionCore]) {
        self.version = version
        self.cores = cores
    }

    public var allowedIDs: Set<String> {
        var ids: Set<String> = []
        for core in cores {
            ids.insert(core.id)
            ids.formUnion(core.fine)
        }
        return ids
    }
}

public struct Helpline: Sendable, Equatable, Identifiable {
    public var name: String
    public var phone: String
    public var sms: String?
    public var verified: Bool

    public var id: String { name + phone }

    public init(name: String, phone: String, sms: String?, verified: Bool) {
        self.name = name
        self.phone = phone
        self.sms = sms
        self.verified = verified
    }
}

public struct Helplines: Sendable, Equatable {
    public var regions: [String: [Helpline]]
    public var emergency: [String: String]
    public var directoryName: String
    public var directoryURL: String
    public var directoryVerified: Bool

    public init(
        regions: [String: [Helpline]],
        emergency: [String: String],
        directoryName: String,
        directoryURL: String,
        directoryVerified: Bool
    ) {
        self.regions = regions
        self.emergency = emergency
        self.directoryName = directoryName
        self.directoryURL = directoryURL
        self.directoryVerified = directoryVerified
    }
}

public enum ContentLoader {
    public static func loadPatternConfig(_ data: Data) throws -> PatternConfig {
        let object = try CanonicalJson.jsonObject(data)
        var consumed: [String] = []
        func number(_ key: String) throws -> Double {
            consumed.append(key)
            guard let value = CanonicalJson.doubleValue(object[key]) else { throw SmileError.missingConfigKey }
            return value
        }
        func integer(_ key: String) throws -> Int {
            Int(try number(key))
        }
        return PatternConfig(
            version: try integer("version"),
            minSegment: try integer("minSegment"),
            minRest: try integer("minRest"),
            patternMinSegment: try integer("patternMinSegment"),
            patternMinRest: try integer("patternMinRest"),
            minAbsDiff: try number("minAbsDiff"),
            zHint: try number("zHint"),
            zPattern: try number("zPattern"),
            sdFloor: try number("sdFloor"),
            weekdayMinSegment: try integer("weekdayMinSegment"),
            weekdayMinDistinctDates: try integer("weekdayMinDistinctDates"),
            weekdayMinAbsDiff: try number("weekdayMinAbsDiff"),
            weekdayMinDataDays: try number("weekdayMinDataDays"),
            tagMinLowTagged: try integer("tagMinLowTagged"),
            tagMinCount: try integer("tagMinCount"),
            tagMinShare: try number("tagMinShare"),
            tagMinShareGap: try number("tagMinShareGap"),
            tagPatternCount: try integer("tagPatternCount"),
            tagAtPlaceMinShare: try number("tagAtPlaceMinShare"),
            fineTagDominance: try number("fineTagDominance"),
            shortWeekThreshold: try integer("shortWeekThreshold"),
            maxItems: try integer("maxItems"),
            maxItemsLowWeek: try integer("maxItemsLowWeek"),
            notEnoughDataBelow: try integer("notEnoughDataBelow"),
            firstWeekDays: try number("firstWeekDays"),
            firstCardMinEntries: try integer("firstCardMinEntries"),
            lowWeekMinEntries: try integer("lowWeekMinEntries"),
            lowWeekShare: try number("lowWeekShare"),
            lowRunLength: try integer("lowRunLength"),
            lowRunMinDays: try integer("lowRunMinDays"),
            placeRadiusMeters: try number("placeRadiusMeters"),
            placeMaxAccuracyMeters: try number("placeMaxAccuracyMeters"),
            consumedKeys: consumed
        )
    }

    public static func loadLibrary(_ data: Data) throws -> [LibraryEntry] {
        let object = try JSONSerialization.jsonObject(with: data)
        guard let rows = object as? [[String: Any]] else { throw SmileError.invalidContent }
        return try rows.map { row in
            guard let id = row["id"] as? String,
                  let match = row["match"] as? [String: Any],
                  let detectorText = match["detector"] as? String,
                  let detector = Detector(rawValue: detectorText),
                  let title = row["title"] as? String,
                  let common = row["common"] as? String,
                  let tip = row["tip"] as? String,
                  let question = row["question"] as? String
            else { throw SmileError.invalidContent }
            let direction = (match["direction"] as? String).flatMap(Direction.init(rawValue:))
            var keyCount = 1
            if match["direction"] != nil { keyCount += 1 }
            if match["segment"] != nil { keyCount += 1 }
            if match["weekday"] != nil { keyCount += 1 }
            if match["placeKind"] != nil { keyCount += 1 }
            if match["tag"] != nil { keyCount += 1 }
            return LibraryEntry(
                id: id,
                match: LibraryMatch(
                    detector: detector,
                    direction: direction,
                    segment: match["segment"] as? String,
                    weekday: match["weekday"] as? String,
                    placeKind: match["placeKind"] as? String,
                    tag: match["tag"] as? String,
                    keyCount: keyCount
                ),
                title: title,
                common: common,
                tip: tip,
                question: question
            )
        }
    }

    public static func loadEmotionTags(_ data: Data) throws -> EmotionTagCatalog {
        let object = try CanonicalJson.jsonObject(data)
        guard let version = CanonicalJson.intValue(object["version"]),
              let cores = object["cores"] as? [[String: Any]]
        else { throw SmileError.invalidContent }
        let parsed = try cores.map { core -> EmotionCore in
            guard let id = core["id"] as? String,
                  let color = core["color"] as? String,
                  let fine = core["fine"] as? [String]
            else { throw SmileError.invalidContent }
            return EmotionCore(id: id, color: color, fine: fine)
        }
        return EmotionTagCatalog(version: version, cores: parsed)
    }

    public static func loadHelplines(_ data: Data) throws -> Helplines {
        let object = try CanonicalJson.jsonObject(data)
        guard let regionObject = object["regions"] as? [String: Any],
              let emergencyObject = object["emergency"] as? [String: Any],
              let directory = object["directory"] as? [String: Any],
              let directoryName = directory["name"] as? String,
              let directoryURL = directory["url"] as? String
        else { throw SmileError.invalidContent }
        var regions: [String: [Helpline]] = [:]
        for (key, value) in regionObject {
            guard let rows = value as? [[String: Any]] else { throw SmileError.invalidContent }
            regions[key] = try rows.map { row in
                guard let name = row["name"] as? String, let phone = row["phone"] as? String else {
                    throw SmileError.invalidContent
                }
                return Helpline(
                    name: name,
                    phone: phone,
                    sms: row["sms"] as? String,
                    verified: (row["verified"] as? Bool) ?? false
                )
            }
        }
        var emergency: [String: String] = [:]
        for (key, value) in emergencyObject {
            if let text = value as? String {
                emergency[key] = text
            }
        }
        return Helplines(
            regions: regions,
            emergency: emergency,
            directoryName: directoryName,
            directoryURL: directoryURL,
            directoryVerified: (directory["verified"] as? Bool) ?? false
        )
    }
}
