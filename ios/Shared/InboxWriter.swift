import Foundation
import SmileCore
import WidgetKit

public enum LockedCheckIn {
    public static let widgetTimeout: TimeInterval = 4
    public static let appTimeout: TimeInterval = 10

    @discardableResult
    public static func save(moodValue: Int, source: EntrySource, timeout: TimeInterval) async -> Bool {
        guard let mood = Mood(raw: moodValue) else {
            markFailed()
            return false
        }
        let defaults = AppGroup.defaults()
        do {
            let writer = try InboxWriter(directory: try AppGroup.inboxURL())
            let now = Date()
            let id = Identifiers.newID()
            try writer.writeEntry(
                InboxEntryFile(
                    id: id,
                    createdAt: now,
                    timeZoneID: TimeZone.current.identifier,
                    mood: mood,
                    source: source
                )
            )
            if defaults.bool(forKey: AppGroup.Key.captureLocation) {
                let sample = await QuickLocation.capture(timeout: timeout, enabled: true, context: .widget)
                if let sample {
                    try? writer.writeLocation(InboxLocationFile(id: id, location: sample))
                }
            }
            defaults.set(now.timeIntervalSince1970, forKey: AppGroup.Key.lastSavedAt)
            defaults.removeObject(forKey: AppGroup.Key.lastSaveFailedAt)
            WidgetCenter.shared.reloadAllTimelines()
            return true
        } catch {
            markFailed()
            return false
        }
    }

    public static func markFailed() {
        let defaults = AppGroup.defaults()
        defaults.set(Date().timeIntervalSince1970, forKey: AppGroup.Key.lastSaveFailedAt)
        WidgetCenter.shared.reloadAllTimelines()
    }
}
