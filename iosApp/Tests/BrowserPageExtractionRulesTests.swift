import Foundation

@main
enum BrowserPageExtractionRulesTests {
    static func main() {
        let document = BrowserPageExtractionRules.document(
            from: [
                "title": "  Candy Reader  ",
                "address": " https://example.com/article ",
                "text": "  First paragraph.\n\nSecond paragraph.  ",
            ]
        )
        expect(document?.title == "Candy Reader", "title is normalized")
        expect(document?.address == "https://example.com/article", "address is normalized")
        expect(
            document?.text == "First paragraph.\n\nSecond paragraph.",
            "reader text is normalized"
        )
        expect(
            BrowserPageExtractionRules.document(from: ["text": " \n "]) == nil,
            "empty page is rejected"
        )
        expect(
            BrowserPageExtractionRules.document(
                from: ["text": "123456"],
                maximumCharacterCount: 4
            )?.text == "1234",
            "oversized page is bounded"
        )
        print("BrowserPageExtractionRulesTests: 5 passed")
    }

    private static func expect(_ condition: Bool, _ message: String) {
        guard condition else {
            fatalError("Failed: \(message)")
        }
    }
}
