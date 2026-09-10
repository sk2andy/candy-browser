import Foundation

@main
enum BrowserFavoritesRulesTests {
    static func main() {
        testItemsAreUniqueAndSortedByHost()
        testSearchMatchesHostAndFullAddress()
        testBlankSearchReturnsAllItems()
        testDisplayTitleFallsBackForInvalidAddress()
        print("BrowserFavoritesRulesTests: 4 passed")
    }

    private static func testItemsAreUniqueAndSortedByHost() {
        let items = BrowserFavoritesRules.items(
            from: [
                "https://www.zeta.example/news",
                "https://alpha.example/start",
                "https://www.zeta.example/news",
            ]
        )

        expect(
            items.map(\.title) == ["alpha.example", "zeta.example"],
            "set-backed favorites remain unique and sort by display host"
        )
    }

    private static func testSearchMatchesHostAndFullAddress() {
        let items = BrowserFavoritesRules.items(
            from: [
                "https://candy.example/ReaderStudio",
                "https://other.example/start",
            ]
        )

        expect(
            BrowserFavoritesRules.filtered(items, query: "CANDY").map(\.address) == [
                "https://candy.example/ReaderStudio",
            ],
            "search matches host case-insensitively"
        )
        expect(
            BrowserFavoritesRules.filtered(items, query: "readerstudio").map(\.address) == [
                "https://candy.example/ReaderStudio",
            ],
            "search matches the full address case-insensitively"
        )
    }

    private static func testBlankSearchReturnsAllItems() {
        let items = BrowserFavoritesRules.items(
            from: ["https://alpha.example", "https://beta.example"]
        )

        expect(
            BrowserFavoritesRules.filtered(items, query: "  \n") == items,
            "blank query keeps all favorites visible"
        )
    }

    private static func testDisplayTitleFallsBackForInvalidAddress() {
        expect(
            BrowserFavoritesRules.displayTitle(for: "not a url") == "not a url",
            "invalid address remains readable"
        )
    }

    private static func expect(_ condition: Bool, _ message: String) {
        guard condition else {
            fatalError("Failed: \(message)")
        }
    }
}
