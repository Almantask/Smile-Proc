import SmileCore
import SwiftUI
import WidgetKit

struct SmileEntry: TimelineEntry {
    var date: Date
    var phase: WidgetPhase
    var refreshAt: Date
}

struct SmileProvider: TimelineProvider {
    func placeholder(in context: Context) -> SmileEntry {
        SmileEntry(date: Date(), phase: .idle, refreshAt: Date().addingTimeInterval(900))
    }

    func getSnapshot(in context: Context, completion: @escaping (SmileEntry) -> Void) {
        completion(current())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SmileEntry>) -> Void) {
        let entry = current()
        completion(Timeline(entries: [entry], policy: .after(entry.refreshAt)))
    }

    private func current() -> SmileEntry {
        let now = Date()
        let phase = AppGroup.savedPhase(now: now)
        let refresh = phase == .idle ? now.addingTimeInterval(900) : now.addingTimeInterval(60)
        return SmileEntry(date: now, phase: phase, refreshAt: refresh)
    }
}

struct SmileWidgetView: View {
    @Environment(\.widgetFamily) private var family
    var entry: SmileEntry

    private var source: EntrySource {
        family == .accessoryRectangular ? .lockScreenWidget : .homeScreenWidget
    }

    var body: some View {
        Group {
            switch entry.phase {
            case .saved:
                Text("widget.saved")
                    .font(.headline)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .unlock:
                Text("widget.unlock")
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .idle:
                HStack(spacing: 12) {
                    moodButton(.low, key: "widget.a11y.low")
                    moodButton(.okay, key: "widget.a11y.okay")
                    moodButton(.good, key: "widget.a11y.good")
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .widgetURL(URL(string: "smileproc://entry/latest"))
        .containerBackground(.background, for: .widget)
    }

    private func moodButton(_ mood: Mood, key: LocalizedStringKey) -> some View {
        Button(intent: LogMoodIntent(mood: mood.rawValue, source: source)) {
            FaceView(mood: mood)
                .frame(width: family == .accessoryRectangular ? 28 : 44, height: family == .accessoryRectangular ? 28 : 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(key))
    }
}

struct SmileProcWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SmileProcCheckIn", provider: SmileProvider()) { entry in
            SmileWidgetView(entry: entry)
        }
        .configurationDisplayName(Text("app.name"))
        .description(Text("onboarding.widget.ios"))
        .supportedFamilies([.accessoryRectangular, .systemSmall])
    }
}

@main
struct SmileProcWidgetsBundle: WidgetBundle {
    var body: some Widget {
        SmileProcWidget()
    }
}
