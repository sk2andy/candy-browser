import Foundation

@main
enum BrowserTranslationProviderPreferenceTests {
    static func main() {
        let suiteName = "BrowserTranslationProviderPreferenceTests"
        guard let preferences = UserDefaults(suiteName: suiteName) else {
            fatalError("Could not create isolated preferences")
        }
        preferences.removePersistentDomain(forName: suiteName)

        expect(
            BrowserTranslationProviderPreference.loadStableId(from: preferences) == "google",
            "iOS defaults to the requested Google translation flow"
        )

        BrowserTranslationProviderPreference.save(stableId: "google", to: preferences)
        expect(
            BrowserTranslationProviderPreference.loadStableId(from: preferences) == "google",
            "selected provider round trips"
        )

        preferences.set("unknown", forKey: BrowserTranslationProviderPreference.key)
        expect(
            BrowserTranslationProviderPreference.loadStableId(from: preferences) == "unknown",
            "stored value crosses adapter without platform policy"
        )

        preferences.removePersistentDomain(forName: suiteName)
        print("BrowserTranslationProviderPreferenceTests: 3 passed")
    }

    private static func expect(_ condition: Bool, _ message: String) {
        guard condition else {
            fatalError("Failed: \(message)")
        }
    }
}
