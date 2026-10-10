import AppIntents
import Foundation
import SmileCore

public struct LogMoodIntent: AppIntent {
    public static var title: LocalizedStringResource = "entry.mood"
    public static var openAppWhenRun = false
    public static var authenticationPolicy: IntentAuthenticationPolicy = .alwaysAllowed
    public static var isDiscoverable = false

    @Parameter(title: "Mood")
    public var mood: Int

    @Parameter(title: "Source")
    public var source: String

    public init() {
        self.mood = Mood.okay.rawValue
        self.source = EntrySource.homeScreenWidget.rawValue
    }

    public init(mood: Int, source: EntrySource) {
        self.mood = mood
        self.source = source.rawValue
    }

    public func perform() async throws -> some IntentResult {
        let parsed = EntrySource(rawValue: source) ?? .homeScreenWidget
        await LockedCheckIn.save(moodValue: mood, source: parsed, timeout: LockedCheckIn.widgetTimeout)
        return .result()
    }
}
