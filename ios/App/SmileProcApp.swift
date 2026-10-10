import SwiftUI

@main
struct SmileProcApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var environment = AppEnvironment()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(environment)
                .onOpenURL { url in
                    environment.handleURL(url)
                }
        }
    }
}
