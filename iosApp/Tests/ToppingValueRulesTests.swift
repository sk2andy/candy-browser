import Foundation

@main
enum ToppingValueRulesTests {
    static func main() {
        expect(ToppingValueRules.isValidKey("theme"), "normal key")
        expect(!ToppingValueRules.isValidKey(""), "empty key")
        expect(!ToppingValueRules.isValidKey("bad\nkey"), "control character")
        expect(ToppingValueRules.isValidEncodedValue("{\"dark\":true}"), "JSON object")
        expect(ToppingValueRules.isValidEncodedValue("42"), "JSON fragment")
        expect(!ToppingValueRules.isValidEncodedValue("undefined"), "non JSON")
        expect(
            ToppingValueRules.snapshot(["b": "2", "a": "1"]) == "{\"a\":\"1\",\"b\":\"2\"}",
            "canonical sorted snapshot"
        )
        let full = Dictionary(uniqueKeysWithValues: (0..<129).map { ("key-\($0)", "0") })
        expect(ToppingValueRules.snapshot(full) == nil, "value-count bound")
        print("ToppingValueRulesTests: 8 passed")
    }

    private static func expect(_ condition: Bool, _ message: String) {
        guard condition else { fatalError("Failed: \(message)") }
    }
}
