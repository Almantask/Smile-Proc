import Foundation

public struct WeeklyCardBuilder: Sendable {
    public var config: PatternConfig
    public var library: [LibraryEntry]

    public init(config: PatternConfig, library: [LibraryEntry]) {
        self.config = config
        self.library = library
    }

    public func build(
        entries: [Entry],
        labels: [PlaceLabel],
        now: Date,
        zoneID: String,
        weekday: Int,
        hour: Int,
        minute: Int
    ) throws -> WeeklyCard {
        let period = try CardPeriod.resolve(now: now, zoneID: zoneID, weekday: weekday, hour: hour, minute: minute)
        let detection = try PatternEngine(config: config).detect(entries: entries, labels: labels, period: period)
        let variant = variantFor(periodEntries: detection.periodEntries, allEntries: entries, periodEnd: period.periodEnd)
        let low = isLowWeek(detection.periodEntries)
        let limit = itemLimit(variant: variant, isLowWeek: low)
        let picked = selectItems(detection.candidates, limit: limit)
        var items: [CardItem] = []
        for candidate in picked {
            guard let libraryID = LibraryMatcher.match(candidate, library: library) else { continue }
            items.append(CardItem(patternID: candidate.id, libraryID: libraryID, pattern: candidate))
        }
        let engine = PatternEngine(config: config)
        var dayparts: [String: String] = [:]
        for entry in entries {
            dayparts[entry.id] = engine.daypart(of: entry)
        }
        return WeeklyCard(
            periodStart: period.periodStart,
            periodEnd: period.periodEnd,
            windowKind: detection.windowKind,
            entryCount: detection.periodEntries.count,
            moodCounts: moodCounts(detection.periodEntries),
            days: weekStrip(detection.periodEntries, periodEnd: period.periodEnd, zoneID: zoneID),
            isLowWeek: low,
            variant: variant,
            maxItemCount: limit,
            items: items,
            candidates: detection.candidates.map { $0.rounded() },
            dayparts: dayparts,
            places: detection.places
        )
    }

    public func shouldBuildCard(
        entries: [Entry],
        now: Date,
        zoneID: String,
        weekday: Int,
        hour: Int,
        minute: Int,
        existingPeriodEnds: [String]
    ) throws -> Bool {
        if entries.count < config.firstCardMinEntries { return false }
        let ordered = entries.sorted { lhs, rhs in
            if lhs.createdAt != rhs.createdAt { return lhs.createdAt < rhs.createdAt }
            return lhs.id < rhs.id
        }
        guard let earliest = ordered.first else { return false }
        let period = try CardPeriod.resolve(now: now, zoneID: zoneID, weekday: weekday, hour: hour, minute: minute)
        if period.periodEnd < earliest.createdAt { return false }
        return !existingPeriodEnds.contains(InstantFormat.format(period.periodEnd))
    }

    public func variantFor(periodEntries: [Entry], allEntries: [Entry], periodEnd: Date) -> CardVariant {
        if periodEntries.count < config.notEnoughDataBelow { return .notEnoughData }
        let ordered = allEntries.sorted { lhs, rhs in
            if lhs.createdAt != rhs.createdAt { return lhs.createdAt < rhs.createdAt }
            return lhs.id < rhs.id
        }
        guard let first = ordered.first else { return .notEnoughData }
        let ageDays = periodEnd.timeIntervalSince(first.createdAt) / 86400
        if ageDays <= config.firstWeekDays { return .firstWeek }
        return .normal
    }

    public func isLowWeek(_ periodEntries: [Entry]) -> Bool {
        if periodEntries.count < config.lowWeekMinEntries { return false }
        let lows = periodEntries.filter { $0.mood == .low }.count
        return Double(lows) / Double(periodEntries.count) >= config.lowWeekShare
    }

    public func itemLimit(variant: CardVariant, isLowWeek: Bool) -> Int {
        var limit = (variant == .notEnoughData || variant == .firstWeek) ? 1 : config.maxItems
        if isLowWeek {
            limit = min(limit, config.maxItemsLowWeek)
        }
        return limit
    }

    public func selectItems(_ candidates: [DetectedPattern], limit: Int) -> [DetectedPattern] {
        let patterns = candidates.filter { $0.confidence == .pattern }
        let hints = candidates.filter { $0.confidence == .earlyHint }
        var picked: [DetectedPattern] = []
        var families: Set<Family> = []
        func take(_ pool: [DetectedPattern]) {
            for candidate in pool {
                if picked.count >= limit { return }
                if families.contains(candidate.family) { continue }
                picked.append(candidate)
                families.insert(candidate.family)
            }
        }
        take(patterns)
        take(hints)
        if limit >= 2 && !picked.isEmpty && !picked.contains(where: { $0.direction == .higher }) {
            if let replacement = patterns.first(where: { $0.direction == .higher && !picked.contains($0) }) {
                picked[picked.count - 1] = replacement
            }
        }
        return picked
    }

    public func moodCounts(_ entries: [Entry]) -> [Int] {
        [
            entries.filter { $0.mood == .low }.count,
            entries.filter { $0.mood == .okay }.count,
            entries.filter { $0.mood == .good }.count,
        ]
    }

    public func weekStrip(_ periodEntries: [Entry], periodEnd: Date, zoneID: String) -> [[Int]] {
        let zone = (try? ZoneClock.zone(zoneID)) ?? InstantFormat.utc
        let endMinus = periodEnd.addingTimeInterval(-0.001)
        let endDate = ZoneClock.localDate(endMinus, zone: zone)
        let iso = ZoneClock.isoWeekday(endMinus, zone: zone)
        let monday = (try? ZoneClock.addingDays(-(iso - 1), to: endDate, zone: zone)) ?? endDate
        var days: [[Int]] = []
        for index in 0..<7 {
            let day = (try? ZoneClock.addingDays(index, to: monday, zone: zone)) ?? monday
            let onDay = periodEntries.filter { entry in
                let entryZone = (try? ZoneClock.zone(entry.timeZoneID)) ?? InstantFormat.utc
                return ZoneClock.localDate(entry.createdAt, zone: entryZone) == day
            }
            days.append(moodCounts(onDay))
        }
        return days
    }
}
