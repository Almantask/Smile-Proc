import SwiftUI

struct PrivacyView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.string("privacy.body"))
                Text(L10n.string("privacy.exportHint"))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .navigationTitle(L10n.string("privacy.title"))
    }
}
