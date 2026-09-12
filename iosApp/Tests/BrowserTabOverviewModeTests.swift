import Foundation

@main
enum BrowserTabOverviewModeTests {
    static func main() {
        let suiteName = "BrowserTabOverviewModeTests.\(UUID().uuidString)"
        guard let defaults = UserDefaults(suiteName: suiteName) else {
            fatalError("Could not create isolated defaults suite")
        }
        defer { defaults.removePersistentDomain(forName: suiteName) }

        expect(
            BrowserTabOverviewModePreference.load(from: defaults) == .hero,
            "missing preference falls back to hero"
        )

        BrowserTabOverviewModePreference.save(.grid, to: defaults)
        expect(
            BrowserTabOverviewModePreference.load(from: defaults) == .grid,
            "saved grid mode is restored"
        )

        defaults.set("unknown", forKey: BrowserTabOverviewModePreference.key)
        expect(
            BrowserTabOverviewModePreference.load(from: defaults) == .hero,
            "unknown wire value falls back to hero"
        )

        expect(
            !BrowserTabOverviewStartsAtBottomPreference.load(from: defaults),
            "missing bottom-start preference falls back to disabled"
        )
        BrowserTabOverviewStartsAtBottomPreference.save(true, to: defaults)
        expect(
            BrowserTabOverviewStartsAtBottomPreference.load(from: defaults),
            "saved bottom-start preference is restored"
        )
        print("BrowserTabOverviewModeTests: 5 passed")
    }

    private static func expect(_ condition: Bool, _ message: String) {
        guard condition else {
            fatalError("Failed: \(message)")
        }
    }
}
