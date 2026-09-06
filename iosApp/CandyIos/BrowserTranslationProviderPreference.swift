import Foundation

enum BrowserTranslationProviderPreference {
    static let key = "page_translation_provider"
    static let defaultStableId = "google"

    static func loadStableId(from preferences: UserDefaults) -> String? {
        preferences.string(forKey: key) ?? defaultStableId
    }

    static func save(
        stableId: String,
        to preferences: UserDefaults
    ) {
        preferences.set(stableId, forKey: key)
    }
}

enum BrowserSearchSettingsPreference {
    static let engineKey = "search_engine"
    static let searxngInstanceKey = "searxng_instance_url"

    static func loadEngineStableId(from preferences: UserDefaults) -> String? {
        preferences.string(forKey: engineKey)
    }

    static func loadSearxngInstanceUrl(from preferences: UserDefaults) -> String {
        preferences.string(forKey: searxngInstanceKey) ?? ""
    }

    static func save(
        engineStableId: String,
        searxngInstanceUrl: String,
        to preferences: UserDefaults
    ) {
        preferences.set(engineStableId, forKey: engineKey)
        preferences.set(searxngInstanceUrl, forKey: searxngInstanceKey)
    }
}
