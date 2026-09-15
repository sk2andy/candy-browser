import Foundation

@main
enum BrowserMenuLayoutPreferenceTests {
    static func main() {
        let suiteName = "BrowserMenuLayoutPreferenceTests"
        guard let preferences = UserDefaults(suiteName: suiteName) else {
            fatalError("Could not create isolated preferences")
        }
        preferences.removePersistentDomain(forName: suiteName)

        expect(
            BrowserMenuLayoutPreference.loadWireValues(from: preferences).isEmpty,
            "missing preference loads the shared default override map"
        )

        let values = [
            "favorite": "both",
            "open_settings": "tab",
            "unknown-future-entry": "unknown-future-location",
        ]
        BrowserMenuLayoutPreference.save(wireValues: values, to: preferences)
        expect(
            BrowserMenuLayoutPreference.loadWireValues(from: preferences) == values,
            "stable wire values round trip without native menu policy"
        )

        preferences.set(
            [
                "favorite": "tab_switcher",
                "invalid": 4,
            ],
            forKey: BrowserMenuLayoutPreference.key
        )
        expect(
            BrowserMenuLayoutPreference.loadWireValues(from: preferences) == [
                "favorite": "tab_switcher",
            ],
            "malformed native values are discarded at the platform boundary"
        )

        preferences.removePersistentDomain(forName: suiteName)
        print("BrowserMenuLayoutPreferenceTests: 3 passed")
    }

    private static func expect(_ condition: Bool, _ message: String) {
        guard condition else {
            fatalError("Failed: \(message)")
        }
    }
}
