import UIKit
import WebKit

@MainActor
final class BrowserTabSnapshotStore {
    private var images: [String: UIImage] = [:]
    private var navigationRevisions: [String: UInt64] = [:]
    private var sessionIds: [String: UUID] = [:]
    private var latestRequestIds: [String: UUID] = [:]
    private var pageIdentities: [String: String] = [:]

    func attach(tabId: String, sessionId: UUID) {
        guard sessionIds[tabId] != sessionId else {
            return
        }
        sessionIds[tabId] = sessionId
        navigationRevisions[tabId] = 0
        latestRequestIds[tabId] = nil
        pageIdentities[tabId] = nil
        images[tabId] = nil
    }

    func navigationStarted(tabId: String) {
        guard sessionIds[tabId] != nil else {
            return
        }
        navigationRevisions[tabId, default: 0] &+= 1
        latestRequestIds[tabId] = nil
    }

    func pageStateChanged(tabId: String, pageIdentity: String?) {
        guard
            sessionIds[tabId] != nil,
            pageIdentities[tabId] != pageIdentity
        else {
            return
        }
        pageIdentities[tabId] = pageIdentity
        navigationStarted(tabId: tabId)
    }

    func invalidate(tabId: String) {
        navigationStarted(tabId: tabId)
    }

    func image(for tabId: String) -> UIImage? {
        images[tabId]
    }

    func removeTabs(notIn activeTabIds: Set<String>) {
        images = images.filter { activeTabIds.contains($0.key) }
        navigationRevisions = navigationRevisions.filter { activeTabIds.contains($0.key) }
        sessionIds = sessionIds.filter { activeTabIds.contains($0.key) }
        latestRequestIds = latestRequestIds.filter { activeTabIds.contains($0.key) }
        pageIdentities = pageIdentities.filter { activeTabIds.contains($0.key) }
    }

    func capture(
        tabId: String,
        webView: WKWebView,
        sessionId: UUID,
        activeTabIds: Set<String>,
        onUpdate: @escaping @MainActor () -> Void,
        onCompletion: @escaping @MainActor (Bool) -> Void = { _ in }
    ) {
        guard
            activeTabIds.contains(tabId),
            sessionIds[tabId] == sessionId,
            webView.bounds.width > 0,
            webView.bounds.height > 0
        else {
            onCompletion(false)
            return
        }

        let pageIdentity = normalizedPageIdentity(webView.url)
        pageStateChanged(tabId: tabId, pageIdentity: pageIdentity)
        let requestId = UUID()
        let request = BrowserTabSnapshotRequest(
            tabId: tabId,
            navigationRevision: navigationRevisions[tabId, default: 0],
            sessionId: sessionId,
            requestId: requestId,
            pageIdentity: pageIdentity
        )
        latestRequestIds[tabId] = requestId

        let configuration = WKSnapshotConfiguration()
        configuration.rect = webView.bounds
        configuration.snapshotWidth = NSNumber(
            value: min(max(webView.bounds.width, 244), 420)
        )
        webView.takeSnapshot(with: configuration) { [weak self] image, _ in
            guard let self, let image else {
                onCompletion(false)
                return
            }
            guard BrowserTabSnapshotAcceptanceRules.accepts(
                request,
                activeTabIds: Set(self.sessionIds.keys),
                navigationRevision: self.navigationRevisions[tabId],
                sessionId: self.sessionIds[tabId],
                latestRequestId: self.latestRequestIds[tabId],
                pageIdentity: self.normalizedPageIdentity(webView.url)
            ) else {
                onCompletion(false)
                return
            }
            self.images[tabId] = image
            onUpdate()
            onCompletion(true)
        }
    }

    private func normalizedPageIdentity(_ url: URL?) -> String? {
        let value = url?.absoluteString
        return value == "about:blank" ? nil : value
    }
}
