import Foundation

public struct LibraryMatch: Sendable, Equatable {
    public var detector: Detector
    public var direction: Direction?
    public var segment: String?
    public var weekday: String?
    public var placeKind: String?
    public var tag: String?
    public var keyCount: Int

    public init(
        detector: Detector,
        direction: Direction?,
        segment: String?,
        weekday: String?,
        placeKind: String?,
        tag: String?,
        keyCount: Int
    ) {
        self.detector = detector
        self.direction = direction
        self.segment = segment
        self.weekday = weekday
        self.placeKind = placeKind
        self.tag = tag
        self.keyCount = keyCount
    }
}

public struct LibraryEntry: Identifiable, Sendable, Equatable {
    public var id: String
    public var match: LibraryMatch
    public var title: String
    public var common: String
    public var tip: String
    public var question: String

    public init(id: String, match: LibraryMatch, title: String, common: String, tip: String, question: String) {
        self.id = id
        self.match = match
        self.title = title
        self.common = common
        self.tip = tip
        self.question = question
    }
}

public struct CoverageRow: Sendable, Equatable {
    public var detector: String
    public var direction: String?
    public var segment: String?
    public var weekday: String?
    public var placeKind: String?
    public var tag: String?
    public var libraryID: String?

    public init(
        detector: String,
        direction: String?,
        segment: String?,
        weekday: String?,
        placeKind: String?,
        tag: String?,
        libraryID: String?
    ) {
        self.detector = detector
        self.direction = direction
        self.segment = segment
        self.weekday = weekday
        self.placeKind = placeKind
        self.tag = tag
        self.libraryID = libraryID
    }
}

public enum PatternText {
    public static func substitute(
        _ template: String,
        segment: String?,
        weekday: String?,
        tag: String?,
        place: String?
    ) -> String {
        var text = template
        if let segment {
            text = text.replacingOccurrences(of: "{segment}", with: segment)
        }
        if let weekday {
            text = text.replacingOccurrences(of: "{weekday}", with: weekday)
        }
        if let tag {
            text = text.replacingOccurrences(of: "{tag}", with: tag)
        }
        if let place {
            text = text.replacingOccurrences(of: "{place}", with: place)
        }
        return text
    }
}
