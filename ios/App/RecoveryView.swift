import SwiftUI

struct RecoveryView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var confirmFresh = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.string("recovery.title"))
                    .font(.title)
                Text(L10n.string("recovery.body"))
                NavigationLink(L10n.string("recovery.import")) {
                    ImportView()
                }
                .buttonStyle(.borderedProminent)
                Button(L10n.string("recovery.fresh"), role: .destructive) {
                    confirmFresh = true
                }
                Spacer()
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .navigationTitle(L10n.string("recovery.title"))
            .confirmationDialog(L10n.string("recovery.fresh"), isPresented: $confirmFresh, titleVisibility: .visible) {
                Button(L10n.string("recovery.fresh"), role: .destructive) {
                    environment.startFresh()
                }
                Button(L10n.string("entry.cancel"), role: .cancel) {}
            } message: {
                Text(L10n.string("recovery.body"))
            }
        }
    }
}
