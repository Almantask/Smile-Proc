import Foundation

public enum LibraryMatcher {
    public static let coreIDs = ["happy", "calm", "sad", "anxious", "angry", "tired", "surprised"]
    public static let dayparts = ["morning", "afternoon", "evening", "night"]
    public static let weekdayDayparts = ["morning", "afternoon", "evening"]
    public static let placeKinds = ["home", "work", "other"]

    public static func core(of tag: String) -> String {
        tag.split(separator: ".", maxSplits: 1).first.map(String.init) ?? tag
    }

    public static func match(_ pattern: DetectedPattern, library: [LibraryEntry]) -> String? {
        match(
            detector: pattern.detector,
            direction: pattern.direction,
            segment: pattern.params.segment,
            weekday: pattern.params.weekday,
            placeKind: pattern.params.placeKind,
            tag: pattern.params.tag,
            library: library
        )
    }

    public static func match(
        detector: Detector,
        direction: Direction?,
        segment: String?,
        weekday: String?,
        placeKind: String?,
        tag: String?,
        library: [LibraryEntry]
    ) -> String? {
        var best: String?
        var bestKeys = -1
        for entry in library {
            let match = entry.match
            if match.detector != detector { continue }
            var ok = true
            if match.direction != nil && match.direction != direction { ok = false }
            if let expected = match.segment, expected != segment { ok = false }
            if let expected = match.weekday, expected != weekday { ok = false }
            if let expected = match.placeKind, expected != placeKind { ok = false }
            if let expected = match.tag {
                guard let tag else {
                    ok = false
                    continue
                }
                if expected != tag && expected != core(of: tag) { ok = false }
            }
            if !ok { continue }
            if match.keyCount > bestKeys {
                best = entry.id
                bestKeys = match.keyCount
            }
        }
        return best
    }

    public static func coverage(library: [LibraryEntry]) -> [CoverageRow] {
        var rows: [CoverageRow] = []
        func add(
            detector: Detector,
            directions: [Direction?],
            segments: [String],
            weekdays: [String],
            placeKinds: [String],
            tags: [String]
        ) {
            let segmentAxis: [String?] = segments.isEmpty ? [nil] : segments
            let weekdayAxis: [String?] = weekdays.isEmpty ? [nil] : weekdays
            let placeAxis: [String?] = placeKinds.isEmpty ? [nil] : placeKinds
            let tagAxis: [String?] = tags.isEmpty ? [nil] : tags
            for direction in directions {
                for segment in segmentAxis {
                    for weekday in weekdayAxis {
                        for placeKind in placeAxis {
                            for tag in tagAxis {
                                let libraryID = match(
                                    detector: detector,
                                    direction: direction,
                                    segment: segment,
                                    weekday: weekday,
                                    placeKind: placeKind,
                                    tag: tag,
                                    library: library
                                )
                                rows.append(
                                    CoverageRow(
                                        detector: detector.rawValue,
                                        direction: direction?.rawValue,
                                        segment: segment,
                                        weekday: weekday,
                                        placeKind: placeKind,
                                        tag: tag,
                                        libraryID: libraryID
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        let both: [Direction?] = [.lower, .higher]
        let noneDirection: [Direction?] = [nil]
        add(detector: .timeOfDay, directions: both, segments: dayparts, weekdays: [], placeKinds: [], tags: [])
        add(detector: .weekday, directions: both, segments: [], weekdays: (1...7).map { String($0) }, placeKinds: [], tags: [])
        add(detector: .weekdayDaypart, directions: both, segments: weekdayDayparts, weekdays: (1...7).map { String($0) }, placeKinds: [], tags: [])
        add(detector: .place, directions: both, segments: [], weekdays: [], placeKinds: placeKinds, tags: [])
        add(detector: .awayFromHome, directions: both, segments: [], weekdays: [], placeKinds: [], tags: [])
        add(detector: .tagOnLow, directions: noneDirection, segments: [], weekdays: [], placeKinds: [], tags: coreIDs)
        add(detector: .tagAtPlace, directions: noneDirection, segments: [], weekdays: [], placeKinds: [], tags: coreIDs)
        return rows
    }
}
