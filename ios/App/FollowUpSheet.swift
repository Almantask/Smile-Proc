import SwiftUI

struct FollowUpSheet: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(\.dismiss) private var dismiss
    let entryID: String
    @State private var tags: [String] = []
    @State private var comment = ""
    @State private var ready = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(L10n.string("today.saved"))
                        .font(.title)
                    Text(L10n.string("today.followup.prompt"))
                    TagEditor(selected: $tags)
                    TextField(L10n.string("tags.comment.placeholder"), text: $comment, axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(3...6)
                        .accessibilityLabel(L10n.string("entry.note"))
                }
                .padding()
            }
            .navigationTitle(L10n.string("today.saved"))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L10n.string("today.done")) { dismiss() }
                        .accessibilityIdentifier(L10n.string("today.done"))
                }
            }
        }
        .onAppear {
            if let entry = environment.entryList.first(where: { $0.id == entryID }) {
                tags = entry.tags
                comment = entry.comment ?? ""
            }
            ready = true
        }
        .onDisappear {
            guard ready else { return }
            environment.updateFollowUp(id: entryID, tags: tags, comment: comment)
        }
    }
}
