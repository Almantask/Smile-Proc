import SmileCore
import SwiftUI

#if DEBUG
struct DebugView: View {
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        List {
            Button(L10n.string("debug.seed")) { environment.seedDemoData() }
            Section(L10n.string("debug.inbox")) {
                Text(String(environment.inboxFileCount()))
                Text(String(environment.corruptDeleted))
            }
            if let outcome = environment.lastLocation {
                Section {
                    Text(outcome.kind)
                    Text(Copy.number(outcome.accuracy))
                    Text(Copy.number(outcome.age))
                }
            }
            Section(L10n.string("debug.patterns")) {
                if let detection = environment.debugDetection() {
                    Text(detection.windowKind.rawValue)
                    ForEach(detection.candidates) { pattern in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(pattern.id)
                            Text("nSeg \(pattern.nSegment)")
                            Text("nRest \(pattern.nRest)")
                            if let value = pattern.meanSegment { Text("meanSegment \(Copy.number(value))") }
                            if let value = pattern.meanRest { Text("meanRest \(Copy.number(value))") }
                            if let value = pattern.diff { Text("diff \(Copy.number(value))") }
                            if let value = pattern.z { Text("z \(Copy.number(value))") }
                            Text("score \(Copy.number(pattern.score))")
                            Text(pattern.confidence.rawValue)
                        }
                        .font(.caption)
                    }
                }
            }
            Section(L10n.string("debug.spikes")) {
                Text(L10n.string("debug.spikeNotes"))
            }
        }
        .navigationTitle(L10n.string("debug.title"))
    }
}
#endif
