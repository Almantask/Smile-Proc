import Foundation

public struct PatternConfig: Sendable, Equatable {
    public static let requiredKeys: [String] = [
        "version",
        "minSegment",
        "minRest",
        "patternMinSegment",
        "patternMinRest",
        "minAbsDiff",
        "zHint",
        "zPattern",
        "sdFloor",
        "weekdayMinSegment",
        "weekdayMinDistinctDates",
        "weekdayMinAbsDiff",
        "weekdayMinDataDays",
        "tagMinLowTagged",
        "tagMinCount",
        "tagMinShare",
        "tagMinShareGap",
        "tagPatternCount",
        "tagAtPlaceMinShare",
        "fineTagDominance",
        "shortWeekThreshold",
        "maxItems",
        "maxItemsLowWeek",
        "notEnoughDataBelow",
        "firstWeekDays",
        "firstCardMinEntries",
        "lowWeekMinEntries",
        "lowWeekShare",
        "lowRunLength",
        "lowRunMinDays",
        "placeRadiusMeters",
        "placeMaxAccuracyMeters",
    ]

    public var version: Int
    public var minSegment: Int
    public var minRest: Int
    public var patternMinSegment: Int
    public var patternMinRest: Int
    public var minAbsDiff: Double
    public var zHint: Double
    public var zPattern: Double
    public var sdFloor: Double
    public var weekdayMinSegment: Int
    public var weekdayMinDistinctDates: Int
    public var weekdayMinAbsDiff: Double
    public var weekdayMinDataDays: Double
    public var tagMinLowTagged: Int
    public var tagMinCount: Int
    public var tagMinShare: Double
    public var tagMinShareGap: Double
    public var tagPatternCount: Int
    public var tagAtPlaceMinShare: Double
    public var fineTagDominance: Double
    public var shortWeekThreshold: Int
    public var maxItems: Int
    public var maxItemsLowWeek: Int
    public var notEnoughDataBelow: Int
    public var firstWeekDays: Double
    public var firstCardMinEntries: Int
    public var lowWeekMinEntries: Int
    public var lowWeekShare: Double
    public var lowRunLength: Int
    public var lowRunMinDays: Int
    public var placeRadiusMeters: Double
    public var placeMaxAccuracyMeters: Double
    public var consumedKeys: [String]

    public init(
        version: Int,
        minSegment: Int,
        minRest: Int,
        patternMinSegment: Int,
        patternMinRest: Int,
        minAbsDiff: Double,
        zHint: Double,
        zPattern: Double,
        sdFloor: Double,
        weekdayMinSegment: Int,
        weekdayMinDistinctDates: Int,
        weekdayMinAbsDiff: Double,
        weekdayMinDataDays: Double,
        tagMinLowTagged: Int,
        tagMinCount: Int,
        tagMinShare: Double,
        tagMinShareGap: Double,
        tagPatternCount: Int,
        tagAtPlaceMinShare: Double,
        fineTagDominance: Double,
        shortWeekThreshold: Int,
        maxItems: Int,
        maxItemsLowWeek: Int,
        notEnoughDataBelow: Int,
        firstWeekDays: Double,
        firstCardMinEntries: Int,
        lowWeekMinEntries: Int,
        lowWeekShare: Double,
        lowRunLength: Int,
        lowRunMinDays: Int,
        placeRadiusMeters: Double,
        placeMaxAccuracyMeters: Double,
        consumedKeys: [String]
    ) {
        self.version = version
        self.minSegment = minSegment
        self.minRest = minRest
        self.patternMinSegment = patternMinSegment
        self.patternMinRest = patternMinRest
        self.minAbsDiff = minAbsDiff
        self.zHint = zHint
        self.zPattern = zPattern
        self.sdFloor = sdFloor
        self.weekdayMinSegment = weekdayMinSegment
        self.weekdayMinDistinctDates = weekdayMinDistinctDates
        self.weekdayMinAbsDiff = weekdayMinAbsDiff
        self.weekdayMinDataDays = weekdayMinDataDays
        self.tagMinLowTagged = tagMinLowTagged
        self.tagMinCount = tagMinCount
        self.tagMinShare = tagMinShare
        self.tagMinShareGap = tagMinShareGap
        self.tagPatternCount = tagPatternCount
        self.tagAtPlaceMinShare = tagAtPlaceMinShare
        self.fineTagDominance = fineTagDominance
        self.shortWeekThreshold = shortWeekThreshold
        self.maxItems = maxItems
        self.maxItemsLowWeek = maxItemsLowWeek
        self.notEnoughDataBelow = notEnoughDataBelow
        self.firstWeekDays = firstWeekDays
        self.firstCardMinEntries = firstCardMinEntries
        self.lowWeekMinEntries = lowWeekMinEntries
        self.lowWeekShare = lowWeekShare
        self.lowRunLength = lowRunLength
        self.lowRunMinDays = lowRunMinDays
        self.placeRadiusMeters = placeRadiusMeters
        self.placeMaxAccuracyMeters = placeMaxAccuracyMeters
        self.consumedKeys = consumedKeys
    }
}
