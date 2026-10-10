import Foundation

public enum Detector: String, Sendable, Equatable, CaseIterable {
    case timeOfDay
    case weekday
    case weekdayDaypart
    case place
    case awayFromHome
    case tagOnLow
    case tagAtPlace

    public var family: Family {
        switch self {
        case .timeOfDay: return .time
        case .weekday, .weekdayDaypart: return .weekday
        case .place, .awayFromHome: return .place
        case .tagOnLow, .tagAtPlace: return .tag
        }
    }
}

public enum Family: String, Sendable, Equatable {
    case time
    case weekday
    case place
    case tag
}

public enum Direction: String, Sendable, Equatable {
    case lower
    case higher
}

public enum Confidence: String, Sendable, Equatable {
    case pattern
    case earlyHint
}

public struct PatternParams: Sendable, Equatable {
    public var segment: String?
    public var weekday: String?
    public var clusterID: String?
    public var placeKind: String?
    public var tag: String?

    public init(
        segment: String? = nil,
        weekday: String? = nil,
        clusterID: String? = nil,
        placeKind: String? = nil,
        tag: String? = nil
    ) {
        self.segment = segment
        self.weekday = weekday
        self.clusterID = clusterID
        self.placeKind = placeKind
        self.tag = tag
    }
}

public struct DetectedPattern: Identifiable, Sendable, Equatable {
    public var id: String
    public var detector: Detector
    public var family: Family
    public var direction: Direction?
    public var params: PatternParams
    public var nSegment: Int
    public var nRest: Int
    public var meanSegment: Double?
    public var meanRest: Double?
    public var diff: Double?
    public var z: Double?
    public var score: Double
    public var confidence: Confidence
    public var supportingEntryIDs: [String]

    public init(
        id: String,
        detector: Detector,
        family: Family,
        direction: Direction?,
        params: PatternParams,
        nSegment: Int,
        nRest: Int,
        meanSegment: Double?,
        meanRest: Double?,
        diff: Double?,
        z: Double?,
        score: Double,
        confidence: Confidence,
        supportingEntryIDs: [String]
    ) {
        self.id = id
        self.detector = detector
        self.family = family
        self.direction = direction
        self.params = params
        self.nSegment = nSegment
        self.nRest = nRest
        self.meanSegment = meanSegment
        self.meanRest = meanRest
        self.diff = diff
        self.z = z
        self.score = score
        self.confidence = confidence
        self.supportingEntryIDs = supportingEntryIDs
    }

    public func rounded() -> DetectedPattern {
        var copy = self
        copy.meanSegment = meanSegment.map(Rounding.r6)
        copy.meanRest = meanRest.map(Rounding.r6)
        copy.diff = diff.map(Rounding.r6)
        copy.z = z.map(Rounding.r6)
        copy.score = Rounding.r6(score)
        return copy
    }
}

public enum WindowKind: String, Sendable, Equatable {
    case oneWeek
    case twoWeeks
}

public enum CardVariant: String, Sendable, Equatable {
    case notEnoughData
    case firstWeek
    case normal
}

public struct CardItem: Sendable, Equatable, Identifiable {
    public var patternID: String
    public var libraryID: String
    public var pattern: DetectedPattern

    public var id: String { patternID }

    public init(patternID: String, libraryID: String, pattern: DetectedPattern) {
        self.patternID = patternID
        self.libraryID = libraryID
        self.pattern = pattern
    }
}

public struct WeeklyCard: Sendable, Equatable {
    public var periodStart: Date
    public var periodEnd: Date
    public var windowKind: WindowKind
    public var entryCount: Int
    public var moodCounts: [Int]
    public var days: [[Int]]
    public var isLowWeek: Bool
    public var variant: CardVariant
    public var maxItemCount: Int
    public var items: [CardItem]
    public var candidates: [DetectedPattern]
    public var dayparts: [String: String]
    public var places: PlaceAssignment

    public init(
        periodStart: Date,
        periodEnd: Date,
        windowKind: WindowKind,
        entryCount: Int,
        moodCounts: [Int],
        days: [[Int]],
        isLowWeek: Bool,
        variant: CardVariant,
        maxItemCount: Int,
        items: [CardItem],
        candidates: [DetectedPattern],
        dayparts: [String: String],
        places: PlaceAssignment
    ) {
        self.periodStart = periodStart
        self.periodEnd = periodEnd
        self.windowKind = windowKind
        self.entryCount = entryCount
        self.moodCounts = moodCounts
        self.days = days
        self.isLowWeek = isLowWeek
        self.variant = variant
        self.maxItemCount = maxItemCount
        self.items = items
        self.candidates = candidates
        self.dayparts = dayparts
        self.places = places
    }
}

public struct Detection: Sendable {
    public var candidates: [DetectedPattern]
    public var windowKind: WindowKind
    public var periodEntries: [Entry]
    public var cardEntries: [Entry]
    public var places: PlaceAssignment

    public init(
        candidates: [DetectedPattern],
        windowKind: WindowKind,
        periodEntries: [Entry],
        cardEntries: [Entry],
        places: PlaceAssignment
    ) {
        self.candidates = candidates
        self.windowKind = windowKind
        self.periodEntries = periodEntries
        self.cardEntries = cardEntries
        self.places = places
    }
}
