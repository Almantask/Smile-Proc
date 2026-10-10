import Foundation
import SmileCore
import UIKit
import UserNotifications

enum CheckInNotifications {
    static func registerCategories() {
        let actions = [
            UNNotificationAction(identifier: "mood.1", title: L10n.string("mood.low"), options: []),
            UNNotificationAction(identifier: "mood.2", title: L10n.string("mood.okay"), options: []),
            UNNotificationAction(identifier: "mood.3", title: L10n.string("mood.good"), options: []),
        ]
        let category = UNNotificationCategory(identifier: "CHECKIN", actions: actions, intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    static func reschedule(reminders: [Reminder], weekly: WeeklyCardSchedule) async {
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests()
        let ours = pending.map(\.identifier).filter { $0.hasPrefix("reminder.") || $0 == "weekly.card" }
        center.removePendingNotificationRequests(withIdentifiers: ours)
        for reminder in reminders.prefix(6) {
            for day in reminder.weekdays where (1...7).contains(day) {
                let content = UNMutableNotificationContent()
                content.title = L10n.string("notification.checkin.title")
                content.body = L10n.string("notification.checkin.body")
                content.categoryIdentifier = "CHECKIN"
                var components = DateComponents()
                components.hour = reminder.hour
                components.minute = reminder.minute
                components.weekday = ZoneClock.swiftWeekday(iso: day)
                let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
                let request = UNNotificationRequest(
                    identifier: "reminder.\(reminder.id).\(day)",
                    content: content,
                    trigger: trigger
                )
                try? await center.add(request)
            }
        }
        let weeklyContent = UNMutableNotificationContent()
        weeklyContent.title = L10n.string("notification.weekly.title")
        weeklyContent.body = L10n.string("notification.weekly.body")
        var weeklyComponents = DateComponents()
        weeklyComponents.hour = weekly.hour
        weeklyComponents.minute = weekly.minute
        weeklyComponents.weekday = ZoneClock.swiftWeekday(iso: weekly.weekday)
        let weeklyTrigger = UNCalendarNotificationTrigger(dateMatching: weeklyComponents, repeats: true)
        try? await UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: "weekly.card", content: weeklyContent, trigger: weeklyTrigger)
        )
    }

    static func handle(_ response: UNNotificationResponse) async {
        let identifier = response.actionIdentifier
        guard identifier.hasPrefix("mood."), let mood = Int(identifier.dropFirst("mood.".count)) else { return }
        await LockedCheckIn.save(moodValue: mood, source: .notification, timeout: LockedCheckIn.widgetTimeout)
    }

    static func removeAll() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
    }
}

final class NotificationRouter: NSObject, UNUserNotificationCenterDelegate, @unchecked Sendable {
    static let shared = NotificationRouter()

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        await CheckInNotifications.handle(response)
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = NotificationRouter.shared
        CheckInNotifications.registerCategories()
        return true
    }
}
