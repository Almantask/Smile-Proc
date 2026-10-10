import Foundation
import SmileCore

public enum AppGroup {
    public static let identifier = "group.com.example.smileproc"
    public static let inboxFolder = "Inbox"

    public enum Key {
        public static let lastSavedAt = "lastSavedAt"
        public static let lastSaveFailedAt = "lastSaveFailedAt"
        public static let captureLocation = "captureLocation"
        public static let onboardingComplete = "onboardingComplete"
        public static let appLockEnabled = "appLockEnabled"
        public static let tagPickerMode = "tagPickerMode"
        public static let reminders = "reminders"
        public static let weeklyWeekday = "weeklyWeekday"
        public static let weeklyHour = "weeklyHour"
        public static let weeklyMinute = "weeklyMinute"
        public static let lowRunDismissed = "lowRunDismissed"
        public static let corruptDeleted = "corruptDeleted"
    }

    public static func containerURL() -> URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }

    public static func inboxURL() throws -> URL {
        guard let base = containerURL() else { throw SmileError.inboxUnreadable }
        return base.appendingPathComponent(inboxFolder, isDirectory: true)
    }

    public static func defaults() -> UserDefaults {
        UserDefaults(suiteName: identifier) ?? .standard
    }

    public static func savedPhase(now: Date = Date()) -> WidgetPhase {
        let defaults = defaults()
        let saved = defaults.object(forKey: Key.lastSavedAt) as? Double
        let failed = defaults.object(forKey: Key.lastSaveFailedAt) as? Double
        let savedAge = saved.map { now.timeIntervalSince1970 - $0 }
        let failedAge = failed.map { now.timeIntervalSince1970 - $0 }
        let savedRecent = (savedAge ?? 61) < 60
        let failedRecent = (failedAge ?? 61) < 60
        if savedRecent && failedRecent {
            return (saved ?? 0) >= (failed ?? 0) ? .saved : .unlock
        }
        if savedRecent { return .saved }
        if failedRecent { return .unlock }
        return .idle
    }
}

public enum WidgetPhase: Sendable {
    case idle
    case saved
    case unlock
}
