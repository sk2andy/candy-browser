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
