import SmileCore
import SwiftUI
import UIKit

struct TodayView: View {
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Text(L10n.string("today.prompt"))
                        .font(.title2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    HStack(spacing: 12) {
                        ForEach(Mood.allCases, id: \.self) { mood in
                            MoodLogButton(mood: mood) {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                environment.log(mood: mood)
                            }
                        }
                    }
                    if environment.lowRunArmed {
                        LowRunCard()
                    }
                    if todayEntries.isEmpty {
                        Text(L10n.string("today.empty"))
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(todayEntries) { entry in
                            NavigationLink {
                                EntryDetailView(entryID: entry.id)
                            } label: {
                                HistoryRow(entry: entry)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    NavigationLink {
                        HistoryView()
                    } label: {
                        Text(L10n.string("today.allEntries"))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .accessibilityIdentifier(L10n.string("today.allEntries"))
                }
                .padding()
            }
            .navigationTitle(L10n.string("tab.today"))
            .sheet(
                isPresented: Binding(
                    get: { environment.followUpID != nil },
                    set: { if !$0 { environment.followUpID = nil } }
                )
            ) {
                if let id = environment.followUpID {
                    FollowUpSheet(entryID: id)
                }
            }
        }
    }

    private var todayEntries: [Entry] {
        environment.entryList
            .filter { Copy.isToday($0) }
            .sorted { lhs, rhs in
                if lhs.createdAt != rhs.createdAt { return lhs.createdAt > rhs.createdAt }
                return lhs.id > rhs.id
            }
    }
}

private struct MoodLogButton: View {
    var mood: Mood
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                FaceView(mood: mood)
                    .frame(height: 72)
                Text(Copy.moodName(mood))
                    .accessibilityHidden(true)
            }
            .frame(maxWidth: .infinity, minHeight: 96)
        }
        .buttonStyle(.bordered)
        .accessibilityLabel(Copy.moodLogLabel(mood))
        .accessibilityIdentifier(Copy.moodLogLabel(mood))
    }
}

private struct LowRunCard: View {
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.string("lowrun.body"))
            NavigationLink(L10n.string("lowrun.action")) {
                HelplinesView()
            }
            Button(L10n.string("lowrun.dismiss")) {
                environment.dismissLowRun()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.quaternary.opacity(0.35), in: RoundedRectangle(cornerRadius: 16))
    }
}
