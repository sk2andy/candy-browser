import Foundation

@main
enum WebKitBlockingTests {
    static func main() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".bundle")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        for name in CandyContentBlockerCompiler.hostResourceNames + [
            "easylist_allowed_host_pairs", "uassets_allowed_host_pairs", "uassets_blocked_host_pairs",
            "easylist_cosmetic_rules", "uassets_cosmetic_rules", "candy_default_rules",
        ] {
            try "".write(to: directory.appendingPathComponent(name + ".txt"), atomically: true, encoding: .utf8)
        }
        for name in ["easylist_cookie_banner", "cookie_banner_overrides"] {
            try "".write(to: directory.appendingPathComponent(name + ".css"), atomically: true, encoding: .utf8)
        }
        func record(_ action: String, _ host: String, _ selector: String) -> String {
            "\(action)\t\(host)\t-\t\(Data(selector.utf8).base64EncodedString())\n"
        }
        try (record("H", "*", ".ad_container") + record("H", "*", ".global-allow") +
            record("H", "*", ".generic") + record("A", "reverse.example", ".reverse"))
            .write(to: directory.appendingPathComponent("easylist_cosmetic_rules.txt"), atomically: true, encoding: .utf8)
        try (record("A", "addictinggames.com", ".ad_container") + record("A", "*", ".global-allow") +
            "D\tdisabled.example\t-\t-\n" + record("H", "*", ".reverse"))
            .write(to: directory.appendingPathComponent("uassets_cosmetic_rules.txt"), atomically: true, encoding: .utf8)
        let bundle = Bundle(path: directory.path)!
        let compilations = try CandyContentBlockerCompiler.compile(bundle: bundle, pausedDomains: ["paused.example"])
        let rules = try compilations.flatMap {
            try JSONSerialization.jsonObject(with: Data($0.json.utf8)) as! [[String: Any]]
        }
        func rule(_ selector: String) -> [String: Any] {
            rules.first { ($0["action"] as? [String: Any])?["selector"] as? String == selector }!
        }
        func exclusions(_ selector: String) -> [String] {
            (rule(selector)["trigger"] as! [String: Any])["unless-domain"] as? [String] ?? []
        }
        expect(exclusions(".ad_container").contains("*addictinggames.com"), "uAssets exception overrides EasyList hide")
        expect(exclusions(".reverse").contains("*reverse.example"), "EasyList exception overrides uAssets hide")
        expect(!rules.contains { ($0["action"] as? [String: Any])?["selector"] as? String == ".global-allow" }, "global cross-list exception suppresses hide")
        expect(exclusions(".generic").contains("*disabled.example"), "generic disable applies across lists")
        expect(exclusions(".ad_container").contains("*paused.example"), "paused domains stay excluded")
        expect(compilations.reduce(0) { $0 + $1.sourceRuleCount } == 8, "source records counted across lists")
        expect(compilations.allSatisfy { $0.identifier.contains(".v3.") }, "changed cosmetic semantics invalidate prior compiled lists")
        print("WebKitBlockingTests: 7 passed")
    }

    private static func expect(_ condition: Bool, _ message: String) {
        guard condition else { fatalError("Failed: \(message)") }
    }
}
