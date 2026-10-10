import Foundation

public struct InboxIngestReport: Sendable, Equatable {
    public var inserted: Int
    public var locationsAttached: Int
    public var skipped: Int
    public var deletedCorrupt: Int

    public init(inserted: Int = 0, locationsAttached: Int = 0, skipped: Int = 0, deletedCorrupt: Int = 0) {
        self.inserted = inserted
        self.locationsAttached = locationsAttached
        self.skipped = skipped
        self.deletedCorrupt = deletedCorrupt
    }
}

public struct InboxIngestor: Sendable {
    public var corruptTTL: TimeInterval

    public init(corruptTTL: TimeInterval = 24 * 60 * 60) {
        self.corruptTTL = corruptTTL
    }

    public func ingest(directory: URL, repository: EntryRepository, now: Date) throws -> InboxIngestReport {
        let manager = FileManager.default
        guard manager.fileExists(atPath: directory.path) else {
            return InboxIngestReport()
        }
        let urls = try manager.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.contentModificationDateKey])
        var report = InboxIngestReport()
        var entries: [String: (InboxEntryFile, URL)] = [:]
        var locations: [String: (InboxLocationFile, URL)] = [:]
        var corrupt: [URL] = []

        for url in urls {
            let name = url.lastPathComponent
            let data = try? Data(contentsOf: url)
            if name.hasSuffix(".entry.json") {
                if let data, let parsed = try? InboxCodec.parseEntry(data) {
                    entries[parsed.id] = (parsed, url)
                } else {
                    corrupt.append(url)
                }
            } else if name.hasSuffix(".loc.json") {
                if let data, let parsed = try? InboxCodec.parseLocation(data) {
                    locations[parsed.id] = (parsed, url)
                } else {
                    corrupt.append(url)
                }
            }
        }

        for (id, pair) in entries {
            let file = pair.0
            let url = pair.1
            let locationPair = locations[id]
            if var existing = try repository.entry(id: id) {
                if existing.location == nil, let locationPair {
                    existing.location = locationPair.0.location
                    existing.updatedAt = now
                    try repository.update(existing)
                    try? manager.removeItem(at: locationPair.1)
                    locations[id] = nil
                    report.locationsAttached += 1
                }
                try? manager.removeItem(at: url)
                continue
            }
            let entry = Entry(
                id: file.id,
                createdAt: file.createdAt,
                updatedAt: file.createdAt,
                timeZoneID: file.timeZoneID,
                mood: file.mood,
                tags: [],
                comment: nil,
                location: locationPair?.0.location,
                source: file.source
            )
            try repository.insert(entry)
            try? manager.removeItem(at: url)
            if let locationPair {
                try? manager.removeItem(at: locationPair.1)
                locations[id] = nil
            }
            report.inserted += 1
        }

        for (id, pair) in locations {
            guard manager.fileExists(atPath: pair.1.path) else { continue }
            if var existing = try repository.entry(id: id) {
                if existing.location == nil {
                    existing.location = pair.0.location
                    existing.updatedAt = now
                    try repository.update(existing)
                    report.locationsAttached += 1
                }
                try? manager.removeItem(at: pair.1)
            }
        }

        for url in corrupt {
            report.skipped += 1
            let modified = (try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? now
            if now.timeIntervalSince(modified) >= corruptTTL {
                try? manager.removeItem(at: url)
                report.deletedCorrupt += 1
            }
        }
        return report
    }
}
