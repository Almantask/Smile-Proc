import SmileCore
import SwiftUI
import UserNotifications

struct OnboardingView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var page = 0
    @State private var capture = false
    @State private var lock = true
    @State private var reminders: [Reminder] = []
    @State private var reminderTime = OnboardingView.defaultTime
    @State private var notice: String?

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $page) {
                privacyPage.tag(0)
                locationPage.tag(1)
                reminderPage.tag(2)
                widgetPage.tag(3)
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
        }
        .onAppear {
            if !environment.canUseAppLock() { lock = false }
        }
    }

    private var privacyPage: some View {
        pageStack(title: "onboarding.privacy.title", body: "onboarding.privacy.body") {
            Button(L10n.string("onboarding.privacy.next")) { page = 1 }
                .buttonStyle(.borderedProminent)
        }
    }

    private var locationPage: some View {
        pageStack(title: "onboarding.location.title", body: "onboarding.location.body") {
            if let notice, page == 1 {
                Text(notice)
                    .font(.footnote)
            }
            Button(L10n.string("onboarding.location.allow")) {
                capture = true
                if LocationPermission.shared.requestWhenInUse() {
                    page = 2
                } else {
                    notice = L10n.string("error.locationDenied")
                }
            }
            .buttonStyle(.borderedProminent)
            Button(L10n.string("onboarding.location.skip")) {
                capture = false
                notice = nil
                page = 2
            }
        }
    }

    private var reminderPage: some View {
        pageStack(title: "onboarding.reminders.title", body: "onboarding.reminders.body") {
            DatePicker(
                L10n.string("reminders.time"),
                selection: $reminderTime,
                displayedComponents: .hourAndMinute
            )
            if let notice, page == 2 {
                Text(notice)
                    .font(.footnote)
            }
            Button(L10n.string("onboarding.reminders.allow")) {
                Task { await allowReminders() }
            }
            .buttonStyle(.borderedProminent)
            Button(L10n.string("onboarding.reminders.skip")) {
                reminders = []
                page = 3
            }
        }
    }

    private var widgetPage: some View {
        pageStack(title: "onboarding.widget.title", body: "onboarding.widget.ios") {
            Toggle(L10n.string("onboarding.widget.lock"), isOn: $lock)
                .disabled(!environment.canUseAppLock())
            if !environment.canUseAppLock() {
                Text(L10n.string("settings.appLock.unavailable"))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if let notice {
                Text(notice)
                    .font(.footnote)
            }
            Button(L10n.string("onboarding.finish")) {
                environment.finishOnboarding(
                    capture: capture,
                    lock: lock && environment.canUseAppLock(),
                    reminders: reminders
                )
            }
            .buttonStyle(.borderedProminent)
        }
    }

    private func pageStack(title: String, body: String, @ViewBuilder actions: () -> some View) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.string(title))
                    .font(.title)
                Text(L10n.string(body))
                actions()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
    }

    private func allowReminders() async {
        let granted = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
        if granted != true {
            notice = L10n.string("error.notificationsDenied")
        }
        let parts = Calendar.current.dateComponents([.hour, .minute], from: reminderTime)
        reminders = [
            Reminder(
                id: Identifiers.newID(),
                hour: parts.hour ?? 20,
                minute: parts.minute ?? 0,
                weekdays: [1, 2, 3, 4, 5, 6, 7]
            ),
        ]
        page = 3
    }

    private static var defaultTime: Date {
        var parts = DateComponents()
        parts.hour = 20
        parts.minute = 0
        return Calendar.current.date(from: parts) ?? Date()
    }
}
