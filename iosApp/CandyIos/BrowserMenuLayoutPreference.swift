import Foundation

enum BrowserMenuLayoutPreference {
    static let key = "candy.browser.menu-layout.v1"

    static func loadWireValues(from preferences: UserDefaults) -> [String: String] {
        guard let stored = preferences.dictionary(forKey: key) else {
            return [:]
        }
        return stored.reduce(into: [:]) { result, element in
            guard let value = element.value as? String else {
                return
            }
            result[element.key] = value
        }
    }

    static func save(
        wireValues: [String: String],
        to preferences: UserDefaults
    ) {
        preferences.set(wireValues, forKey: key)
    }
}
