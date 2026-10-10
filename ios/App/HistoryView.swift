import SmileCore
import SwiftUI

struct HistoryView: View {
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        Group {
            if environment.entryList.isEmpty {
                Text(L10n.string("history.empty"))
                    .foregroundStyle(.secondary)
                    .padding()
            } else {
                List {
                    ForEach(groups) { group in
                        Section(group.title) {
                            ForEach(group.entries) { entry in
                                NavigationLink {
                                    EntryDetailView(entryID: entry.id)
                                } label: {
                                    HistoryRow(entry: entry)
                                }
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(L10n.string("history.title"))
    }

    private var groups: [DayGroup] {
        let sorted = environment.entryList.sorted { lhs, rhs in
            if lhs.createdAt != rhs.createdAt { return lhs.createdAt > rhs.createdAt }
            return lhs.id > rhs.id
        }
        var order: [String] = []
        var buckets: [String: [Entry]] = [:]
        var titles: [String: String] = [:]
        for entry in sorted {
            let key = Copy.dayID(entry)
            if buckets[key] == nil {
                order.append(key)
                titles[key] = Copy.entryDayTitle(entry)
            }
            buckets[key, default: []].append(entry)
        }
        return order.map { key in
            DayGroup(id: key, title: titles[key] ?? key, entries: buckets[key] ?? [])
        }
    }
}

struct DayGroup: Identifiable {
    var id: String
    var title: String
    var entries: [Entry]
}

struct HistoryRow: View {
    @Environment(AppEnvironment.self) private var environment
    var entry: Entry

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            FaceView(mood: entry.mood)
                .frame(width: 36, height: 36)
            VStack(alignment: .leading, spacing: 4) {
                Text(Copy.moodName(entry.mood))
                Text(Copy.entryClock(entry))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                if !entry.tags.isEmpty {
                    Text(entry.tags.map(Copy.tagName).joined(separator: ", "))
                        .font(.subheadline)
                }
                if let place = environment.placeName(for: entry.id) {
                    Text(place)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                if let comment = entry.comment, !comment.isEmpty {
                    Text(comment)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }
}
