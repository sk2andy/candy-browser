import Foundation

@main
enum BrowserTabSnapshotRulesTests {
    static func main() {
        let tabId = "tab-1"
        let sessionId = UUID()
        let requestId = UUID()
        let request = BrowserTabSnapshotRequest(
            tabId: tabId,
            navigationRevision: 4,
            sessionId: sessionId,
            requestId: requestId,
            pageIdentity: "https://example.com/page"
        )

        expect(
            BrowserTabSnapshotAcceptanceRules.accepts(
                request,
                activeTabIds: [tabId],
                navigationRevision: 4,
                sessionId: sessionId,
                latestRequestId: requestId,
                pageIdentity: "https://example.com/page"
            ),
            "matching request is accepted"
        )
        expect(
            !BrowserTabSnapshotAcceptanceRules.accepts(
                request,
                activeTabIds: [],
                navigationRevision: 4,
                sessionId: sessionId,
                latestRequestId: requestId,
                pageIdentity: "https://example.com/page"
            ),
            "closed tab is rejected"
        )
        expect(
            !BrowserTabSnapshotAcceptanceRules.accepts(
                request,
                activeTabIds: [tabId],
                navigationRevision: 5,
                sessionId: sessionId,
                latestRequestId: requestId,
                pageIdentity: "https://example.com/page"
            ),
            "stale navigation is rejected"
        )
        expect(
            !BrowserTabSnapshotAcceptanceRules.accepts(
                request,
                activeTabIds: [tabId],
                navigationRevision: 4,
                sessionId: UUID(),
                latestRequestId: requestId,
                pageIdentity: "https://example.com/page"
            ),
            "replaced engine session is rejected"
        )
        expect(
            !BrowserTabSnapshotAcceptanceRules.accepts(
                request,
                activeTabIds: [tabId],
                navigationRevision: 4,
                sessionId: sessionId,
                latestRequestId: UUID(),
                pageIdentity: "https://example.com/page"
            ),
            "superseded request is rejected"
        )
        expect(
            !BrowserTabSnapshotAcceptanceRules.accepts(
                request,
                activeTabIds: [tabId],
                navigationRevision: 4,
                sessionId: sessionId,
                latestRequestId: requestId,
                pageIdentity: "https://example.com/other"
            ),
            "same-document page identity change is rejected"
        )
        print("BrowserTabSnapshotRulesTests: 6 passed")
    }

    private static func expect(_ condition: Bool, _ message: String) {
        guard condition else {
            fatalError("Failed: \(message)")
        }
    }
}
