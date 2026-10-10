import SmileCore
import SwiftUI

struct PlacesView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var naming: PlaceCluster?
    @State private var renaming: PlaceLabel?
    @State private var renameText = ""
    @State private var deletingLabel: PlaceLabel?
    @State private var confirmLocations = false

    var body: some View {
        List {
            if environment.assignment.clusters.isEmpty && environment.placeList.isEmpty {
                Text(L10n.string("place.empty"))
                    .foregroundStyle(.secondary)
            }
            ForEach(environment.assignment.clusters) { cluster in
                Section {
                    if let labelID = cluster.labelID, let label = environment.placeList.first(where: { $0.id == labelID }) {
                        named(label)
                    } else {
                        Text(Copy.fill("place.unnamed.description", [
                            "count": String(cluster.memberIDs.count),
                            "when": whenText(cluster),
                        ]))
                        Button(L10n.string("place.name.prompt")) { naming = cluster }
                    }
                }
            }
            ForEach(orphanLabels) { label in
                Section {
                    named(label)
                }
            }
            Section {
                Button(L10n.string("place.removeLocations"), role: .destructive) {
                    confirmLocations = true
                }
            }
        }
        .navigationTitle(L10n.string("settings.places"))
        .sheet(item: $naming) { cluster in
            NamePlaceSheet(cluster: cluster)
        }
        .alert(L10n.string("place.rename"), isPresented: renamePresented) {
            TextField(L10n.string("place.name.custom"), text: $renameText)
            Button(L10n.string("entry.save")) {
                if let renaming {
                    environment.renamePlace(id: renaming.id, name: renameText)
                }
            }
            Button(L10n.string("entry.cancel"), role: .cancel) {}
        }
        .confirmationDialog(
            L10n.string("place.deleteLabel.confirm"),
            isPresented: Binding(
                get: { deletingLabel != nil },
                set: { if !$0 { deletingLabel = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button(L10n.string("place.deleteLabel"), role: .destructive) {
                if let deletingLabel {
                    environment.deleteLabel(id: deletingLabel.id)
                }
                deletingLabel = nil
            }
            Button(L10n.string("entry.cancel"), role: .cancel) { deletingLabel = nil }
        }
        .confirmationDialog(
            L10n.string("place.removeLocations.confirm"),
            isPresented: $confirmLocations,
            titleVisibility: .visible
        ) {
            Button(L10n.string("place.removeLocations.action"), role: .destructive) {
                environment.removeAllLocations()
            }
            Button(L10n.string("entry.cancel"), role: .cancel) {}
        }
    }

    private func named(_ label: PlaceLabel) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label.name)
            Picker(L10n.string("place.kind"), selection: Binding(
                get: { label.kind },
                set: { environment.changeKind(id: label.id, kind: $0) }
            )) {
                ForEach(PlaceKind.allCases, id: \.self) { kind in
                    Text(Copy.kindName(kind)).tag(kind)
                }
            }
            Button(L10n.string("place.rename")) {
                renameText = label.name
                renaming = label
            }
            Button(L10n.string("place.deleteLabel"), role: .destructive) {
                deletingLabel = label
            }
        }
        .accessibilityElement(children: .contain)
    }

    private var orphanLabels: [PlaceLabel] {
        let attached = Set(environment.assignment.clusters.compactMap(\.labelID))
        return environment.placeList.filter { !attached.contains($0.id) }
    }

    private var renamePresented: Binding<Bool> {
        Binding(
            get: { renaming != nil },
            set: { if !$0 { renaming = nil } }
        )
    }

    private func whenText(_ cluster: PlaceCluster) -> String {
        guard let config = environment.config else {
            return Copy.fill("place.when.weekday", ["segment": Copy.daypartName("morning")])
        }
        let engine = PatternEngine(config: config)
        var counts: [String: Int] = [:]
        for id in cluster.memberIDs {
            guard let entry = environment.entryList.first(where: { $0.id == id }) else { continue }
            let weekend = engine.isoWeekday(of: entry) >= 6
            let part = engine.daypart(of: entry)
            let key = (weekend ? "weekend" : "weekday") + "\n" + part
            counts[key, default: 0] += 1
        }
        let best = counts.max { lhs, rhs in
            if lhs.value != rhs.value { return lhs.value < rhs.value }
            return lhs.key > rhs.key
        }
        let pieces = best?.key.split(separator: "\n").map(String.init) ?? ["weekday", "morning"]
        let bucket = pieces.first ?? "weekday"
        let segment = pieces.count > 1 ? pieces[1] : "morning"
        let key = bucket == "weekend" ? "place.when.weekend" : "place.when.weekday"
        return Copy.fill(key, ["segment": Copy.daypartName(segment)])
    }
}

struct NamePlaceSheet: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(\.dismiss) private var dismiss
    let cluster: PlaceCluster
    @State private var name = ""
    @State private var kind = PlaceKind.other
    @State private var declinedMerge = false

    var body: some View {
        NavigationStack {
            Form {
                if let nearest = environment.nearestLabel(for: cluster), !declinedMerge {
                    Section {
                        Text(Copy.fill("place.merge.ask", ["name": nearest.name]))
                        Button(L10n.string("place.merge.yes")) {
                            environment.namePlace(cluster: cluster, name: nearest.name, kind: nearest.kind, merge: true)
                            dismiss()
                        }
                        Button(L10n.string("place.merge.no")) { declinedMerge = true }
                    }
                } else {
                    Section {
                        TextField(L10n.string("place.name.custom"), text: $name)
                            .accessibilityLabel(L10n.string("place.name.custom"))
                        ForEach(PlaceKind.allCases, id: \.self) { item in
                            Button(Copy.kindName(item)) {
                                name = Copy.kindName(item)
                                kind = item
                            }
                        }
                        Picker(L10n.string("place.kind"), selection: $kind) {
                            ForEach(PlaceKind.allCases, id: \.self) { item in
                                Text(Copy.kindName(item)).tag(item)
                            }
                        }
                    }
                    Section {
                        Button(L10n.string("entry.save")) {
                            environment.namePlace(cluster: cluster, name: name, kind: kind, merge: false)
                            dismiss()
                        }
                        .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            }
            .navigationTitle(L10n.string("place.name.prompt"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.string("common.close")) { dismiss() }
                }
            }
        }
    }
}
