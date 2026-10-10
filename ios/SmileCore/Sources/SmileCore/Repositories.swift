import CryptoKit
import Foundation
@preconcurrency import GRDB

public struct EntryRepository: Sendable {
    public var database: AppDatabase
    public var sealer: Sealer

    public init(database: AppDatabase, key: SymmetricKey) {
        self.database = database
        self.sealer = Sealer(key: key)
    }

    public func insert(_ entry: Entry) throws {
        let payload = try sealer.seal(Data(CanonicalJson.encodeEntry(entry).utf8), id: entry.id)
        try database.queue.write { db in
            try db.execute(
                sql: "INSERT INTO entry (id, createdAt, updatedAt, payload) VALUES (?, ?, ?, ?)",
                arguments: [
                    entry.id,
                    entry.createdAt.timeIntervalSince1970,
                    entry.updatedAt.timeIntervalSince1970,
                    payload,
                ]
            )
        }
    }

    public func update(_ entry: Entry) throws {
        let payload = try sealer.seal(Data(CanonicalJson.encodeEntry(entry).utf8), id: entry.id)
        try database.queue.write { db in
            try db.execute(
                sql: "UPDATE entry SET updatedAt = ?, payload = ? WHERE id = ?",
                arguments: [entry.updatedAt.timeIntervalSince1970, payload, entry.id]
            )
        }
    }

    public func delete(id: String) throws {
        try database.queue.write { db in
            try db.execute(sql: "DELETE FROM entry WHERE id = ?", arguments: [id])
        }
    }

    public func entry(id: String) throws -> Entry? {
        try database.queue.read { db in
            guard let row = try Row.fetchOne(db, sql: "SELECT payload FROM entry WHERE id = ?", arguments: [id]) else {
                return nil
            }
            guard let payload: Data = row["payload"] else { throw SmileError.databaseFailure }
            let plaintext = try sealer.open(payload, id: id)
            return try CanonicalJson.parseEntry(plaintext)
        }
    }

    public func all() throws -> [Entry] {
        try database.queue.read { db in
            let rows = try Row.fetchAll(db, sql: "SELECT id, payload FROM entry ORDER BY createdAt, id")
            var entries: [Entry] = []
            for row in rows {
                guard let id: String = row["id"], let payload: Data = row["payload"] else {
                    throw SmileError.databaseFailure
                }
                let plaintext = try sealer.open(payload, id: id)
                entries.append(try CanonicalJson.parseEntry(plaintext))
            }
            return entries
        }
    }

    public func upsertImported(_ entry: Entry) throws {
        if let existing = try self.entry(id: entry.id) {
            if entry.updatedAt > existing.updatedAt {
                try update(entry)
            }
        } else {
            try insert(entry)
        }
    }

    public func removeAllLocations(now: Date) throws {
        let entries = try all()
        for var entry in entries where entry.location != nil {
            entry.location = nil
            entry.updatedAt = now
            try update(entry)
        }
    }
}

public struct PlaceRepository: Sendable {
    public var database: AppDatabase
    public var sealer: Sealer

    public init(database: AppDatabase, key: SymmetricKey) {
        self.database = database
        self.sealer = Sealer(key: key)
    }

    public func upsert(_ place: PlaceLabel) throws {
        let payload = try sealer.seal(Data(CanonicalJson.encodePlace(place).utf8), id: place.id)
        try database.queue.write { db in
            try db.execute(sql: "DELETE FROM place WHERE id = ?", arguments: [place.id])
            try db.execute(
                sql: "INSERT INTO place (id, payload) VALUES (?, ?)",
                arguments: [place.id, payload]
            )
        }
    }

    public func delete(id: String) throws {
        try database.queue.write { db in
            try db.execute(sql: "DELETE FROM place WHERE id = ?", arguments: [id])
        }
    }

    public func deleteAll() throws {
        try database.queue.write { db in
            try db.execute(sql: "DELETE FROM place")
        }
    }

    public func all() throws -> [PlaceLabel] {
        try database.queue.read { db in
            let rows = try Row.fetchAll(db, sql: "SELECT id, payload FROM place ORDER BY id")
            var places: [PlaceLabel] = []
            for row in rows {
                guard let id: String = row["id"], let payload: Data = row["payload"] else {
                    throw SmileError.databaseFailure
                }
                let plaintext = try sealer.open(payload, id: id)
                places.append(try CanonicalJson.parsePlace(plaintext))
            }
            return places
        }
    }
}

public struct WeeklyCardRepository: Sendable {
    public var database: AppDatabase
    public var sealer: Sealer

    public init(database: AppDatabase, key: SymmetricKey) {
        self.database = database
        self.sealer = Sealer(key: key)
    }

    public func insert(_ card: WeeklyCard) throws {
        let stamp = card.periodStart.timeIntervalSince1970
        let payload = try sealer.seal(storedData(card), id: Self.cardKey(stamp))
        try database.queue.write { db in
            try db.execute(
                sql: "INSERT INTO weekly_card (periodStart, payload) VALUES (?, ?)",
                arguments: [stamp, payload]
            )
        }
    }

    public func all() throws -> [WeeklyCard] {
        try database.queue.read { db in
            let rows = try Row.fetchAll(db, sql: "SELECT periodStart, payload FROM weekly_card ORDER BY periodStart DESC")
            var cards: [WeeklyCard] = []
            for row in rows {
                guard let start: Double = row["periodStart"], let payload: Data = row["payload"] else {
                    throw SmileError.databaseFailure
                }
                let plaintext = try sealer.open(payload, id: Self.cardKey(start))
                cards.append(try parseStored(plaintext))
            }
            return cards
        }
    }

    public func periodEnds() throws -> [String] {
        try all().map { InstantFormat.format($0.periodEnd) }
    }

    public func deleteAll() throws {
        try database.queue.write { db in
            try db.execute(sql: "DELETE FROM weekly_card")
        }
    }

    private static func cardKey(_ stamp: Double) -> String {
        String(format: "%.6f", locale: Locale(identifier: "en_US_POSIX"), stamp)
    }

    private func storedData(_ card: WeeklyCard) -> Data {
        let items = card.items.map { item -> JSONValue in
            .object([
                ("patternID", .string(item.patternID)),
                ("libraryID", .string(item.libraryID)),
                ("detector", .string(item.pattern.detector.rawValue)),
                ("direction", item.pattern.direction.map { .string($0.rawValue) } ?? .null),
                ("segment", item.pattern.params.segment.map { .string($0) } ?? .null),
                ("weekday", item.pattern.params.weekday.map { .string($0) } ?? .null),
                ("clusterID", item.pattern.params.clusterID.map { .string($0) } ?? .null),
                ("placeKind", item.pattern.params.placeKind.map { .string($0) } ?? .null),
                ("tag", item.pattern.params.tag.map { .string($0) } ?? .null),
                ("nSegment", .int(item.pattern.nSegment)),
                ("confidence", .string(item.pattern.confidence.rawValue)),
            ])
        }
        let value = JSONValue.object([
            ("periodStart", .string(InstantFormat.format(card.periodStart))),
            ("periodEnd", .string(InstantFormat.format(card.periodEnd))),
            ("windowKind", .string(card.windowKind.rawValue)),
            ("entryCount", .int(card.entryCount)),
            ("moodCounts", .array(card.moodCounts.map { .int($0) })),
            ("days", .array(card.days.map { .array($0.map { .int($0) }) })),
            ("isLowWeek", .bool(card.isLowWeek)),
            ("variant", .string(card.variant.rawValue)),
            ("maxItemCount", .int(card.maxItemCount)),
            ("items", .array(items)),
        ])
        return CanonicalJson.encodeData(value)
    }

    private func parseStored(_ data: Data) throws -> WeeklyCard {
        let object = try CanonicalJson.jsonObject(data)
        guard let startText = object["periodStart"] as? String,
              let endText = object["periodEnd"] as? String,
              let windowText = object["windowKind"] as? String,
              let window = WindowKind(rawValue: windowText),
              let entryCount = CanonicalJson.intValue(object["entryCount"]),
              let moodRows = object["moodCounts"] as? [Any],
              let dayRows = object["days"] as? [Any],
              let low = object["isLowWeek"] as? Bool,
              let variantText = object["variant"] as? String,
              let variant = CardVariant(rawValue: variantText),
              let maxItemCount = CanonicalJson.intValue(object["maxItemCount"]),
              let itemRows = object["items"] as? [[String: Any]]
        else { throw SmileError.invalidContent }
        let items = try itemRows.map { row -> CardItem in
            guard let patternID = row["patternID"] as? String,
                  let libraryID = row["libraryID"] as? String,
                  let detectorText = row["detector"] as? String,
                  let detector = Detector(rawValue: detectorText),
                  let nSegment = CanonicalJson.intValue(row["nSegment"]),
                  let confidenceText = row["confidence"] as? String,
                  let confidence = Confidence(rawValue: confidenceText)
            else { throw SmileError.invalidContent }
            let direction = (row["direction"] as? String).flatMap(Direction.init(rawValue:))
            let pattern = DetectedPattern(
                id: patternID,
                detector: detector,
                family: detector.family,
                direction: direction,
                params: PatternParams(
                    segment: row["segment"] as? String,
                    weekday: row["weekday"] as? String,
                    clusterID: row["clusterID"] as? String,
                    placeKind: row["placeKind"] as? String,
                    tag: row["tag"] as? String
                ),
                nSegment: nSegment,
                nRest: 0,
                meanSegment: nil,
                meanRest: nil,
                diff: nil,
                z: nil,
                score: 0,
                confidence: confidence,
                supportingEntryIDs: []
            )
            return CardItem(patternID: patternID, libraryID: libraryID, pattern: pattern)
        }
        return WeeklyCard(
            periodStart: try InstantFormat.parse(startText),
            periodEnd: try InstantFormat.parse(endText),
            windowKind: window,
            entryCount: entryCount,
            moodCounts: moodRows.compactMap { CanonicalJson.intValue($0) },
            days: dayRows.map { row in
                (row as? [Any] ?? []).compactMap { CanonicalJson.intValue($0) }
            },
            isLowWeek: low,
            variant: variant,
            maxItemCount: maxItemCount,
            items: items,
            candidates: [],
            dayparts: [:],
            places: PlaceAssignment(entryClusters: [:], clusters: [])
        )
    }
}
