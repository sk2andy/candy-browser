import Foundation

enum BrowserTabOverviewMode: String, CaseIterable, Identifiable {
    case hero
    case grid
    case list

    var id: String { rawValue }

    var title: String {
        switch self {
        case .hero: "Hero"
        case .grid: "Raster"
        case .list: "Liste"
        }
    }

    var systemImage: String {
        switch self {
        case .hero: "rectangle.portrait.on.rectangle.portrait"
        case .grid: "square.grid.2x2"
        case .list: "list.bullet"
        }
    }
}

enum BrowserTabOverviewModePreference {
    static let key = "candy.tabs.overview.mode"

    static func load(from defaults: UserDefaults) -> BrowserTabOverviewMode {
        BrowserTabOverviewMode(rawValue: defaults.string(forKey: key) ?? "") ?? .hero
    }

    static func save(_ mode: BrowserTabOverviewMode, to defaults: UserDefaults) {
        defaults.set(mode.rawValue, forKey: key)
    }
}

enum BrowserTabOverviewStartsAtBottomPreference {
    static let key = "candy.tabs.overview.starts-at-bottom"

    static func load(from defaults: UserDefaults) -> Bool {
        defaults.bool(forKey: key)
    }

    static func save(_ enabled: Bool, to defaults: UserDefaults) {
        defaults.set(enabled, forKey: key)
    }
}

enum BrowserTabDismissResistancePreference {
    static let key = "candy.tabs.dismiss-resistance"

    static func loadValue(from defaults: UserDefaults, defaultValue: Int32) -> Int32 {
        guard let raw = defaults.object(forKey: key) as? Int else { return defaultValue }
        return Int32(clamping: raw)
    }

    static func saveValue(_ value: Int32, to defaults: UserDefaults) {
        defaults.set(Int(value), forKey: key)
    }
}
