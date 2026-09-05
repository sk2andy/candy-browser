import SwiftUI

@main
struct CandyIosApp: App {
    @StateObject private var browser = BrowserViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView(browser: browser)
        }
    }
}
