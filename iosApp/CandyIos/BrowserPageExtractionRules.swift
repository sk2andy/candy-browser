import Foundation

struct BrowserPageDocument: Identifiable, Equatable {
    let id = UUID()
    let title: String
    let address: String
    let text: String
}

enum BrowserPageExtractionRules {
    static func document(
        from result: Any?,
        maximumCharacterCount: Int = 120_000
    ) -> BrowserPageDocument? {
        guard
            let values = result as? [String: Any],
            let rawText = values["text"] as? String
        else {
            return nil
        }
        let text = rawText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else {
            return nil
        }
        return BrowserPageDocument(
            title: normalized(values["title"] as? String, fallback: "Lesemodus"),
            address: normalized(values["address"] as? String, fallback: ""),
            text: String(text.prefix(max(1, maximumCharacterCount)))
        )
    }

    private static func normalized(_ value: String?, fallback: String) -> String {
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? fallback : trimmed
    }
}
