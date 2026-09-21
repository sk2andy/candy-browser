import Foundation

@main
enum ToppingFrameRulesTests {
    static func main() {
        expect(StoredToppingFrameScope.allMatching.restricted(to: .top) == .top, "top cap")
        expect(
            StoredToppingFrameScope.allMatching.restricted(to: .sameOrigin) == .sameOrigin,
            "same-origin cap"
        )
        expect(StoredToppingFrameScope.sameOrigin.isWithin(.allMatching), "narrow scope accepted")
        expect(!StoredToppingFrameScope.allMatching.isWithin(.sameOrigin), "broad scope rejected")

        let secureDefault = requireOrigin(URL(string: "https://example.com/page")!)
        let secureExplicit = requireOrigin(URL(string: "https://example.com:443/other")!)
        let insecure = requireOrigin(URL(string: "http://example.com/page")!)
        let customPort = requireOrigin(URL(string: "https://example.com:8443/page")!)
        expect(secureDefault == secureExplicit, "default ports normalize")
        expect(secureDefault != insecure, "scheme changes origin")
        expect(secureDefault != customPort, "custom port changes origin")

        expect(
            ToppingFrameAuthorizationRules.allows(
                scope: .top,
                isMainFrame: true,
                frameOrigin: secureDefault,
                topOrigin: secureDefault
            ),
            "top frame accepted"
        )
        expect(
            !ToppingFrameAuthorizationRules.allows(
                scope: .top,
                isMainFrame: false,
                frameOrigin: secureDefault,
                topOrigin: secureDefault
            ),
            "top scope rejects child"
        )
        expect(
            ToppingFrameAuthorizationRules.allows(
                scope: .sameOrigin,
                isMainFrame: false,
                frameOrigin: secureDefault,
                topOrigin: secureExplicit
            ),
            "same-origin child accepted"
        )
        expect(
            !ToppingFrameAuthorizationRules.allows(
                scope: .sameOrigin,
                isMainFrame: false,
                frameOrigin: insecure,
                topOrigin: secureDefault
            ),
            "cross-origin child rejected"
        )
        expect(
            ToppingFrameAuthorizationRules.allows(
                scope: .allMatching,
                isMainFrame: false,
                frameOrigin: customPort,
                topOrigin: secureDefault
            ),
            "all-matching child accepted"
        )

        expect(
            ToppingStoredScopeRules.scopeForSave(existing: nil, declared: .allMatching) == .allMatching,
            "new explicit scope follows declaration"
        )
        expect(
            ToppingStoredScopeRules.scopeForSave(existing: .top, declared: .allMatching) == .top,
            "update cannot expand existing permission"
        )
        expect(
            ToppingStoredScopeRules.scopeForSave(existing: .allMatching, declared: .sameOrigin) == .sameOrigin,
            "narrow declaration clamps permission"
        )
        print("ToppingFrameRulesTests: 15 passed")
    }

    private static func requireOrigin(_ url: URL) -> ToppingFrameOrigin {
        guard let origin = ToppingFrameOrigin(url: url) else {
            fatalError("Expected origin for \(url)")
        }
        return origin
    }

    private static func expect(_ condition: Bool, _ message: String) {
        guard condition else { fatalError("Failed: \(message)") }
    }
}
