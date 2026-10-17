import SwiftUI

@main
struct CandyIosApp: App {
    @StateObject private var browser = BrowserViewModel()
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Register before launch callbacks; the browser binds after its shared state restores.
        _ = BrowserSnoozeNotifications.shared
    }

    var body: some Scene {
        WindowGroup {
            CandyComposeHost(browser: browser)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { browser.resumeSnoozedTabs() }
                }
        }
    }
}
