import Foundation

struct BrowserTabSnapshotRequest: Equatable {
    let tabId: String
    let navigationRevision: UInt64
    let sessionId: UUID
    let requestId: UUID
    let pageIdentity: String?
}

enum BrowserTabSnapshotAcceptanceRules {
    static func accepts(
        _ request: BrowserTabSnapshotRequest,
        activeTabIds: Set<String>,
        navigationRevision: UInt64?,
        sessionId: UUID?,
        latestRequestId: UUID?,
        pageIdentity: String?
    ) -> Bool {
        activeTabIds.contains(request.tabId) &&
            navigationRevision == request.navigationRevision &&
            sessionId == request.sessionId &&
            latestRequestId == request.requestId &&
            pageIdentity == request.pageIdentity
    }
}
