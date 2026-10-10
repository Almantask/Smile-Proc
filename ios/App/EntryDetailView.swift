import SmileCore
import SwiftUI

struct EntryDetailView: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(\.dismiss) private var dismiss
    let entryID: String
    @State private var mood = Mood.okay
    @State private var tags: [String] = []
    @State private var comment = ""
    @State private var loaded = false
    @State private var removed = false
    @State private var confirmDelete = false
    @State private var naming: PlaceCluster?

    var body: some View {
        Form {
            Section(L10n.string("entry.mood")) {
                HStack {
                    ForEach(Mood.allCases, id: \.self) { item in
                        Button {
                            mood = item
                        } label: {
                            FaceView(mood: item)
                                .frame(height: 44)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityLabel(Copy.moodName(item))
                        .accessibilityAddTraits(mood == item ? .isSelected : [])
                    }
                }
            }
            Section(L10n.string("entry.tags")) {
                TagEditor(selected: $tags)
            }
            Section(L10n.string("entry.note")) {
                TextField(L10n.string("tags.comment.placeholder"), text: $comment, axis: .vertical)
                    .lineLimit(3...8)
            }
            if let cluster = currentCluster {
                Section(L10n.string("entry.place")) {
                    if cluster.labelID == nil {
                        Button(L10n.string("entry.namePlace")) { naming = cluster }
                    } else if let name = environment.placeName(for: entryID) {
                        Text(name)
                    }
                }
            }
            Section {
                Button(L10n.string("entry.delete"), role: .destructive) {
                    confirmDelete = true
                }
            }
        }
        .navigationTitle(Copy.moodName(mood))
        .onAppear(perform: load)
        .onDisappear(perform: persist)
        .confirmationDialog(L10n.string("entry.delete.confirm"), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(L10n.string("entry.delete.action"), role: .destructive) {
                removed = true
                environment.deleteEntry(id: entryID)
                dismiss()
            }
            Button(L10n.string("entry.cancel"), role: .cancel) {}
        }
        .sheet(item: $naming) { cluster in
            NamePlaceSheet(cluster: cluster)
        }
    }

    private var currentCluster: PlaceCluster? {
        guard let clusterID = environment.assignment.entryClusters[entryID] else { return nil }
        return environment.assignment.cluster(id: clusterID)
    }

    private func load() {
        guard !loaded, let entry = environment.entryList.first(where: { $0.id == entryID }) else { return }
        mood = entry.mood
        tags = entry.tags
        comment = entry.comment ?? ""
        loaded = true
    }

    private func persist() {
        guard loaded, !removed, var entry = environment.entryList.first(where: { $0.id == entryID }) else { return }
        let newTags = EntryValidator.normalizedTags(tags, allowed: environment.tags.allowedIDs)
        let newComment = EntryValidator.normalizedComment(comment)
        guard entry.mood != mood || entry.tags != newTags || entry.comment != newComment else { return }
        entry.mood = mood
        entry.tags = newTags
        entry.comment = newComment
        entry.updatedAt = Date()
        environment.updateEntry(entry)
    }
}
