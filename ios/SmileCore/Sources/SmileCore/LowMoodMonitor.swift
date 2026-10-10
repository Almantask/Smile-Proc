import Foundation

public enum LowMoodMonitor {
    public static func lowRunArmed(entries: [Entry], config: PatternConfig, dismissedEntryID: String?) -> Bool {
        let ordered = entries.sorted { lhs, rhs in
            if lhs.createdAt != rhs.createdAt { return lhs.createdAt < rhs.createdAt }
            return lhs.id < rhs.id
        }
        if let dismissedEntryID,
           let index = ordered.firstIndex(where: { $0.id == dismissedEntryID }) {
            let after = ordered.suffix(from: ordered.index(after: index))
            if after.allSatisfy({ $0.mood == .low }) {
                return false
            }
        }
        let length = config.lowRunLength
        if ordered.count < length { return false }
        let tail = Array(ordered.suffix(length))
        if tail.contains(where: { $0.mood != .low }) { return false }
        var dates: Set<LocalDate> = []
        for entry in tail {
            let zone = (try? ZoneClock.zone(entry.timeZoneID)) ?? InstantFormat.utc
            dates.insert(ZoneClock.localDate(entry.createdAt, zone: zone))
        }
        return dates.count >= config.lowRunMinDays
    }
}
