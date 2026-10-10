import Combine
import SmileCore
import SwiftUI
import UIKit

struct RootView: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            phaseContent
            if environment.isLocked && (environment.phase == .ready || environment.phase == .onboarding) {
                LockView()
            }
            if environment.showPrivacyCover {
                Rectangle()
                    .fill(.background)
                    .ignoresSafeArea()
                    .accessibilityHidden(true)
            }
        }
        .onAppear { environment.bootstrap() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                environment.sceneBecameActive()
            } else {
                environment.sceneBecameInactive()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.protectedDataDidBecomeAvailableNotification)) { _ in
            environment.protectedDataBecameAvailable()
        }
        .alert(
            L10n.string("app.name"),
            isPresented: Binding(
                get: { environment.statusMessage != nil },
                set: { if !$0 { environment.statusMessage = nil } }
            )
        ) {
            Button(L10n.string("common.ok"), role: .cancel) {}
        } message: {
            Text(environment.statusMessage ?? "")
        }
        .fullScreenCover(
            isPresented: Binding(
                get: { environment.openLatest && !environment.isLocked && environment.phase == .ready },
                set: { if !$0 { environment.openLatest = false } }
            )
        ) {
            LatestEntryCover()
                .environment(environment)
        }
    }

    @ViewBuilder
    private var phaseContent: some View {
        switch environment.phase {
        case .loading, .waitingForData:
            ProgressView()
                .accessibilityLabel(L10n.string("app.name"))
        case .onboarding:
            OnboardingView()
        case .recovery:
            RecoveryView()
        case .ready:
            MainTabs()
        }
    }
}

private struct MainTabs: View {
    var body: some View {
        TabView {
            TodayView()
                .tabItem { Text(L10n.string("tab.today")) }
            WeekView()
                .tabItem { Text(L10n.string("tab.week")) }
            SettingsView()
                .tabItem { Text(L10n.string("tab.settings")) }
        }
    }
}

private struct LatestEntryCover: View {
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        NavigationStack {
            Group {
                if let entry = latest {
                    EntryDetailView(entryID: entry.id)
                } else {
                    HistoryView()
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.string("common.close")) { environment.openLatest = false }
                }
            }
        }
    }

    private var latest: Entry? {
        environment.entryList.max { lhs, rhs in
            if lhs.createdAt != rhs.createdAt { return lhs.createdAt < rhs.createdAt }
            return lhs.id < rhs.id
        }
    }
}
