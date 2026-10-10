import SwiftUI

struct LockView: View {
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        VStack(spacing: 24) {
            Text(L10n.string("lock.title"))
                .font(.title2)
                .multilineTextAlignment(.center)
            Button(L10n.string("lock.action")) {
                Task { await environment.unlock() }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.background)
        .onAppear {
            Task { await environment.unlock() }
        }
    }
}
