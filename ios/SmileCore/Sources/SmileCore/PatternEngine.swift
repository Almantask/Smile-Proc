import Foundation

public struct PatternEngine: Sendable {
    public var config: PatternConfig

    public init(config: PatternConfig) {
        self.config = config
    }

    public func detect(entries: [Entry], labels: [PlaceLabel], period: CardPeriod) throws -> Detection {
        let ordered = entries.sorted { lhs, rhs in
            if lhs.createdAt != rhs.createdAt { return lhs.createdAt < rhs.createdAt }
            return lhs.id < rhs.id
        }
        let periodEntries = ordered.filter { $0.createdAt >= period.periodStart && $0.createdAt < period.periodEnd }
        var cardEntries = periodEntries
        var windowKind = WindowKind.oneWeek
        if periodEntries.count < config.shortWeekThreshold {
            let windowStart = try CardPeriod.shiftLocal(
                instant: period.periodEnd,
                zoneID: period.zoneID,
                days: 14,
                hour: period.hour,
                minute: period.minute
            )
            cardEntries = ordered.filter { $0.createdAt >= windowStart && $0.createdAt < period.periodEnd }
            windowKind = .twoWeeks
        }
        let weekdayStart = try CardPeriod.shiftLocal(
            instant: period.periodEnd,
            zoneID: period.zoneID,
            days: 28,
            hour: period.hour,
            minute: period.minute
        )
        let weekdayEntries = ordered.filter { $0.createdAt >= weekdayStart && $0.createdAt < period.periodEnd }
        var weekdayOK = false
        if let first = ordered.first {
            let ageDays = period.periodEnd.timeIntervalSince(first.createdAt) / 86400
            weekdayOK = ageDays >= config.weekdayMinDataDays
        }

        let places = PlaceResolver(config: config).resolve(entries: ordered, labels: labels)
        let clusters = places.entryClusters
        var candidates: [DetectedPattern] = []

        func addMean(
            detector: Detector,
            ident: String,
            params: PatternParams,
            segment: [Entry],
            rest: [Entry],
            weekdayRules: Bool
        ) {
            if segment.isEmpty || rest.isEmpty { return }
            let stats = compareMoods(segment.map { Double($0.mood.rawValue) }, rest.map { Double($0.mood.rawValue) })
            if let found = candidateMean(
                detector: detector,
                ident: ident,
                params: params,
                stats: stats,
                supporting: segment,
                weekdayRules: weekdayRules
            ) {
                candidates.append(found)
            }
        }

        for part in LibraryMatcher.dayparts {
            let segment = cardEntries.filter { daypart(of: $0) == part }
            let rest = cardEntries.filter { daypart(of: $0) != part }
            addMean(
                detector: .timeOfDay,
                ident: "timeOfDay.{dir}.\(part)",
                params: PatternParams(segment: part),
                segment: segment,
                rest: rest,
                weekdayRules: false
            )
        }

        if weekdayOK {
            for day in 1...7 {
                let segment = weekdayEntries.filter { isoWeekday(of: $0) == day }
                let rest = weekdayEntries.filter { isoWeekday(of: $0) != day }
                addMean(
                    detector: .weekday,
                    ident: "weekday.{dir}.\(day)",
                    params: PatternParams(weekday: String(day)),
                    segment: segment,
                    rest: rest,
                    weekdayRules: true
                )
                for part in LibraryMatcher.weekdayDayparts {
                    let segmentIDs = Set(
                        weekdayEntries.filter { isoWeekday(of: $0) == day && daypart(of: $0) == part }.map(\.id)
                    )
                    let segment = weekdayEntries.filter { segmentIDs.contains($0.id) }
                    let rest = weekdayEntries.filter { !segmentIDs.contains($0.id) }
                    addMean(
                        detector: .weekdayDaypart,
                        ident: "weekdayDaypart.{dir}.\(day).\(part)",
                        params: PatternParams(segment: part, weekday: String(day)),
                        segment: segment,
                        rest: rest,
                        weekdayRules: true
                    )
                }
            }
        }

        let clusterIDs = Set(cardEntries.compactMap { clusters[$0.id] }).sorted()
        for clusterID in clusterIDs {
            let segment = cardEntries.filter { clusters[$0.id] == clusterID }
            let rest = cardEntries.filter { clusters[$0.id] != nil && clusters[$0.id] != clusterID }
            var params = PatternParams(clusterID: clusterID)
            if let label = labels.first(where: { clusterID == "p:\($0.id)" }) {
                params.placeKind = label.kind.rawValue
            }
            addMean(
                detector: .place,
                ident: "place.{dir}.\(clusterID)",
                params: params,
                segment: segment,
                rest: rest,
                weekdayRules: false
            )
        }

        let homeLabels = labels.filter { $0.kind == .home }.sorted { $0.id < $1.id }
        if let home = homeLabels.first {
            let homeID = "p:\(home.id)"
            let segment = cardEntries.filter { clusters[$0.id] != nil && clusters[$0.id] != homeID }
            let rest = cardEntries.filter { clusters[$0.id] == homeID }
            addMean(
                detector: .awayFromHome,
                ident: "awayFromHome.{dir}",
                params: PatternParams(),
                segment: segment,
                rest: rest,
                weekdayRules: false
            )
        }

        let lowTagged = cardEntries.filter { $0.mood == .low && !$0.tags.isEmpty }
        if lowTagged.count >= config.tagMinLowTagged {
            let okayGood = cardEntries.filter { ($0.mood == .okay || $0.mood == .good) && !$0.tags.isEmpty }
            for core in LibraryMatcher.coreIDs {
                let hits = lowTagged.filter { entryHasCore($0, core: core) }
                let count = hits.count
                let shareSegment = Double(count) / Double(lowTagged.count)
                let shareRest: Double
                if okayGood.isEmpty {
                    shareRest = 0
                } else {
                    shareRest = Double(okayGood.filter { entryHasCore($0, core: core) }.count) / Double(okayGood.count)
                }
                if count < config.tagMinCount || shareSegment < config.tagMinShare { continue }
                if shareSegment - shareRest < config.tagMinShareGap { continue }
                let tag = dominantTag(core: core, hits: hits)
                if let found = candidateTag(
                    detector: .tagOnLow,
                    tag: tag,
                    params: PatternParams(),
                    count: count,
                    shareSegment: shareSegment,
                    shareRest: shareRest,
                    nRest: okayGood.count,
                    supporting: hits
                ) {
                    candidates.append(found)
                }
            }
        }

        for clusterID in clusterIDs {
            let inCluster = cardEntries.filter { clusters[$0.id] == clusterID }
            let tagged = inCluster.filter { !$0.tags.isEmpty }
            if tagged.isEmpty { continue }
            let elsewhere = cardEntries.filter { clusters[$0.id] != nil && clusters[$0.id] != clusterID && !$0.tags.isEmpty }
            for core in LibraryMatcher.coreIDs {
                let hits = tagged.filter { entryHasCore($0, core: core) }
                let count = hits.count
                let shareSegment = Double(count) / Double(tagged.count)
                if count < config.tagMinCount || shareSegment < config.tagAtPlaceMinShare { continue }
                let shareRest: Double
                if elsewhere.isEmpty {
                    shareRest = 0
                } else {
                    shareRest = Double(elsewhere.filter { entryHasCore($0, core: core) }.count) / Double(elsewhere.count)
                }
                if shareSegment - shareRest < config.tagMinShareGap { continue }
                let tag = dominantTag(core: core, hits: hits)
                if let found = candidateTag(
                    detector: .tagAtPlace,
                    tag: tag,
                    params: PatternParams(clusterID: clusterID),
                    count: count,
                    shareSegment: shareSegment,
                    shareRest: shareRest,
                    nRest: elsewhere.count,
                    supporting: hits
                ) {
                    candidates.append(found)
                }
            }
        }

        candidates.sort { lhs, rhs in
            let leftRank = lhs.confidence == .pattern ? 0 : 1
            let rightRank = rhs.confidence == .pattern ? 0 : 1
            if leftRank != rightRank { return leftRank < rightRank }
            let leftScore = Rounding.r9(lhs.score)
            let rightScore = Rounding.r9(rhs.score)
            if leftScore != rightScore { return leftScore > rightScore }
            return lhs.id < rhs.id
        }
        return Detection(
            candidates: candidates,
            windowKind: windowKind,
            periodEntries: periodEntries,
            cardEntries: cardEntries,
            places: places
        )
    }

    public func daypart(of entry: Entry) -> String {
        let zone = (try? ZoneClock.zone(entry.timeZoneID)) ?? InstantFormat.utc
        let hour = ZoneClock.hour(entry.createdAt, zone: zone)
        if (5...11).contains(hour) { return "morning" }
        if (12...16).contains(hour) { return "afternoon" }
        if (17...21).contains(hour) { return "evening" }
        return "night"
    }

    public func isoWeekday(of entry: Entry) -> Int {
        let zone = (try? ZoneClock.zone(entry.timeZoneID)) ?? InstantFormat.utc
        return ZoneClock.isoWeekday(entry.createdAt, zone: zone)
    }

    private struct MoodStats {
        var nSegment: Int
        var nRest: Int
        var meanSegment: Double
        var meanRest: Double
        var diff: Double
        var z: Double
    }

    private func mean(_ values: [Double]) -> Double {
        values.reduce(0, +) / Double(values.count)
    }

    private func sampleVariance(_ values: [Double]) -> Double {
        if values.count <= 1 { return 0 }
        let center = mean(values)
        return values.reduce(0) { $0 + ($1 - center) * ($1 - center) } / Double(values.count - 1)
    }

    private func compareMoods(_ segment: [Double], _ rest: [Double]) -> MoodStats {
        let nSegment = segment.count
        let nRest = rest.count
        let meanSegment = mean(segment)
        let meanRest = mean(rest)
        let diff = meanSegment - meanRest
        let denominator = nSegment + nRest - 2
        let pooled: Double
        if denominator <= 0 {
            pooled = 0
        } else {
            let varSegment = sampleVariance(segment)
            let varRest = sampleVariance(rest)
            pooled = sqrt((Double(nSegment - 1) * varSegment + Double(nRest - 1) * varRest) / Double(denominator))
        }
        let z = diff / (max(pooled, config.sdFloor) * sqrt(1 / Double(nSegment) + 1 / Double(nRest)))
        return MoodStats(
            nSegment: nSegment,
            nRest: nRest,
            meanSegment: meanSegment,
            meanRest: meanRest,
            diff: diff,
            z: z
        )
    }

    private func distinctDates(_ entries: [Entry]) -> Int {
        var dates: Set<LocalDate> = []
        for entry in entries {
            let zone = (try? ZoneClock.zone(entry.timeZoneID)) ?? InstantFormat.utc
            dates.insert(ZoneClock.localDate(entry.createdAt, zone: zone))
        }
        return dates.count
    }

    private func entryHasCore(_ entry: Entry, core: String) -> Bool {
        entry.tags.contains { $0 == core || $0.hasPrefix(core + ".") }
    }

    private func candidateMean(
        detector: Detector,
        ident: String,
        params: PatternParams,
        stats: MoodStats,
        supporting: [Entry],
        weekdayRules: Bool
    ) -> DetectedPattern? {
        let distinctPattern = !weekdayRules || distinctDates(supporting) >= config.weekdayMinDistinctDates
        let distinctHint = !weekdayRules || distinctDates(supporting) >= 1
        let patternN = weekdayRules ? config.weekdayMinSegment : config.patternMinSegment
        let patternDiff = weekdayRules ? config.weekdayMinAbsDiff : config.minAbsDiff
        let isPattern = stats.nSegment >= patternN
            && stats.nRest >= config.patternMinRest
            && abs(stats.diff) >= patternDiff
            && abs(stats.z) >= config.zPattern
            && distinctPattern
        let isHint = stats.nSegment >= config.minSegment
            && stats.nRest >= config.minRest
            && abs(stats.diff) >= config.minAbsDiff
            && abs(stats.z) >= config.zHint
            && distinctHint
        if !isPattern && !isHint { return nil }
        let confidence: Confidence = isPattern ? .pattern : .earlyHint
        let direction: Direction = stats.diff < 0 ? .lower : .higher
        let score = abs(stats.diff) * Double(min(stats.nSegment, 10)) / 10 + (confidence == .pattern ? 0.2 : 0)
        let id = ident.replacingOccurrences(of: "{dir}", with: direction.rawValue)
        return DetectedPattern(
            id: id,
            detector: detector,
            family: detector.family,
            direction: direction,
            params: params,
            nSegment: stats.nSegment,
            nRest: stats.nRest,
            meanSegment: stats.meanSegment,
            meanRest: stats.meanRest,
            diff: stats.diff,
            z: stats.z,
            score: score,
            confidence: confidence,
            supportingEntryIDs: supporting.map(\.id)
        )
    }

    private func candidateTag(
        detector: Detector,
        tag: String,
        params: PatternParams,
        count: Int,
        shareSegment: Double,
        shareRest: Double,
        nRest: Int,
        supporting: [Entry]
    ) -> DetectedPattern? {
        if count < config.tagMinCount { return nil }
        let confidence: Confidence = count >= config.tagPatternCount ? .pattern : .earlyHint
        let score = (shareSegment - shareRest) * Double(min(count, 10)) / 10 + (confidence == .pattern ? 0.2 : 0)
        var full = params
        full.tag = tag
        var id = "\(detector.rawValue).\(tag)"
        if let clusterID = params.clusterID {
            id += ".\(clusterID)"
        }
        return DetectedPattern(
            id: id,
            detector: detector,
            family: detector.family,
            direction: nil,
            params: full,
            nSegment: count,
            nRest: nRest,
            meanSegment: nil,
            meanRest: nil,
            diff: nil,
            z: nil,
            score: score,
            confidence: confidence,
            supportingEntryIDs: supporting.map(\.id)
        )
    }

    private func dominantTag(core: String, hits: [Entry]) -> String {
        if hits.isEmpty { return core }
        var counts: [String: Int] = [:]
        for entry in hits {
            for tag in entry.tags where tag.hasPrefix(core + ".") {
                counts[tag, default: 0] += 1
            }
        }
        if counts.isEmpty { return core }
        let best = counts.sorted { lhs, rhs in
            if lhs.value != rhs.value { return lhs.value > rhs.value }
            return lhs.key < rhs.key
        }[0]
        if Double(best.value) / Double(hits.count) >= config.fineTagDominance {
            return best.key
        }
        return core
    }
}
