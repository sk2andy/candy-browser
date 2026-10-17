import CandyShared
import Foundation

enum BrowserAppearancePreference {
    private static let modeKey = "candy.appearance.mode"
    private static let animationsKey = "candy.appearance.animations"
    private static let pageSizeKey = "candy.appearance.page-size-percent"

    static func load(from preferences: UserDefaults) -> AppearanceSettings {
        AppearanceSettings.companion.fromCorePreferences(
            appearanceModeStableId: preferences.string(forKey: modeKey),
            animationsEnabled: preferences.object(forKey: animationsKey) == nil ||
                preferences.bool(forKey: animationsKey),
            webContentFontSizePercent: Int32(clamping:
                preferences.object(forKey: pageSizeKey) as? Int ??
                    Int(AppearanceSettings.companion.DEFAULT_WEB_CONTENT_FONT_SIZE_PERCENT)
            )
        )
    }

    static func save(_ settings: AppearanceSettings, to preferences: UserDefaults) {
        preferences.set(settings.appearanceMode.stableId, forKey: modeKey)
        preferences.set(settings.animationsEnabled, forKey: animationsKey)
        preferences.set(Int(settings.webContentFontSizePercent), forKey: pageSizeKey)
    }
}
