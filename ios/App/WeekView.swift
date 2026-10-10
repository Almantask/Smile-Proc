import SmileCore
import SwiftUI

struct WeekView: View {
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    if let latest = environment.cardList.first {
                        CardPage(card: latest)
                        if environment.cardList.count > 1 {
                            Text(L10n.string("week.past"))
                                .font(.title3)
                            ForEach(Array(environment.cardList.dropFirst()), id: \.periodStart) { card in
                                NavigationLink(Copy.range(start: card.periodStart, end: card.periodEnd)) {
                                    ScrollView {
                                        CardPage(card: card)
                                            .padding()
                                    }
                                    .navigationTitle(L10n.string("week.title"))
                                }
                            }
                        }
                    } else {
                        Text(L10n.string("week.title"))
                            .font(.title2)
                        Text(emptyText)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .navigationTitle(L10n.string("tab.week"))
        }
    }

    private var emptyText: String {
        let schedule = environment.weeklySchedule
        return Copy.fill("week.empty", [
            "weekday": Copy.weekdayName(schedule.weekday),
            "time": Copy.clock(hour: schedule.hour, minute: schedule.minute),
        ])
    }
}

private struct CardPage: View {
    @Environment(AppEnvironment.self) private var environment
    var card: WeeklyCard
    @State private var naming: PlaceCluster?

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text(Copy.range(start: card.periodStart, end: card.periodEnd))
                .font(.headline)
            WeekStrip(days: card.days)
            if card.variant == .notEnoughData {
                Text(L10n.string("week.notEnough"))
            } else if card.variant == .firstWeek {
                Text(L10n.string("week.firstWeek"))
            }
            if card.isLowWeek {
                VStack(alignment: .leading, spacing: 8) {
                    Text(L10n.string("week.lowWeek.body"))
                    Text(L10n.string("week.lowWeek.support"))
                    NavigationLink(L10n.string("week.lowWeek.action")) {
                        HelplinesView()
                    }
                }
            }
            ForEach(card.items) { item in
                itemBlock(item)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .sheet(item: $naming) { cluster in
            NamePlaceSheet(cluster: cluster)
        }
    }

    @ViewBuilder
    private func itemBlock(_ item: CardItem) -> some View {
        let pattern = item.pattern
        VStack(alignment: .leading, spacing: 8) {
            if let library = environment.library.first(where: { $0.id == item.libraryID }) {
                Text(libraryText(library.title, pattern: pattern))
                    .font(.headline)
                if pattern.confidence == .earlyHint {
                    Text(L10n.string("week.earlyHint"))
                        .font(.subheadline)
                }
                Text(observation(pattern))
                Text(L10n.string("week.common.label"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(libraryText(library.common, pattern: pattern))
                Text(libraryText(library.tip, pattern: pattern))
                Text(libraryText(library.question, pattern: pattern))
            }
            if let clusterID = pattern.params.clusterID,
               let cluster = environment.assignment.cluster(id: clusterID),
               cluster.labelID == nil {
                Button(L10n.string("week.namePlace")) { naming = cluster }
            }
        }
    }

    private func observation(_ pattern: DetectedPattern) -> String {
        let key: String
        switch pattern.detector {
        case .tagOnLow:
            key = "week.observation.tagOnLow"
        case .tagAtPlace:
            key = "week.observation.tagAtPlace"
        default:
            let direction = pattern.direction?.rawValue ?? "lower"
            key = "week.observation.\(pattern.detector.rawValue).\(direction)"
        }
        let weekday = pattern.params.weekday.flatMap(Int.init).map(Copy.weekdayName) ?? ""
        return Copy.fill(key, [
            "segment": pattern.params.segment.map(Copy.daypartName) ?? "",
            "weekday": weekday,
            "place": environment.libraryPlaceName(clusterID: pattern.params.clusterID),
            "tag": pattern.params.tag.map { Copy.tagName($0).lowercased() } ?? "",
            "count": String(pattern.nSegment),
        ])
    }

    private func libraryText(_ template: String, pattern: DetectedPattern) -> String {
        PatternText.substitute(
            template,
            segment: pattern.params.segment.map(Copy.daypartName),
            weekday: pattern.params.weekday.flatMap(Int.init).map(Copy.weekdayName),
            tag: pattern.params.tag.map { Copy.tagName($0).lowercased() },
            place: environment.libraryPlaceName(clusterID: pattern.params.clusterID)
        )
    }
}

private struct WeekStrip: View {
    var days: [[Int]]

    var body: some View {
        HStack(alignment: .top, spacing: 4) {
            ForEach(0..<7, id: \.self) { index in
                let counts = index < days.count ? days[index] : []
                VStack(spacing: 4) {
                    Text(Copy.weekdayName(index + 1))
                        .font(.caption2)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                    countLine(.low, counts: counts, slot: 0)
                    countLine(.okay, counts: counts, slot: 1)
                    countLine(.good, counts: counts, slot: 2)
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(label(index: index, counts: counts))
            }
        }
    }

    private func countLine(_ mood: Mood, counts: [Int], slot: Int) -> some View {
        let value = slot < counts.count ? counts[slot] : 0
        return HStack(spacing: 2) {
            FaceView(mood: mood)
                .frame(width: 14, height: 14)
            Text(String(value))
                .font(.caption2)
                .accessibilityHidden(true)
        }
    }

    private func label(index: Int, counts: [Int]) -> String {
        let low = 0 < counts.count ? counts[0] : 0
        let okay = 1 < counts.count ? counts[1] : 0
        let good = 2 < counts.count ? counts[2] : 0
        return "\(Copy.weekdayName(index + 1)), \(Copy.moodName(.low)) \(low), \(Copy.moodName(.okay)) \(okay), \(Copy.moodName(.good)) \(good)"
    }
}
