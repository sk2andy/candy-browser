import CandyShared
import CryptoKit
import Foundation
import WebKit

@MainActor
protocol BrowserEngineEventSink: AnyObject {
    func browserEngineDidEmit(_ event: BrowserEngineEvent)

    func browserEngineDidUpdateHistory(
        tabId: String,
        sessionId: UUID,
        snapshot: CandyTrailHistorySnapshot,
        title: String
    )

    func browserEngineDidRefineTrailTitle(
        tabId: String,
        sessionId: UUID,
        address: String,
        title: String
    )
}

enum WKHistoryNavigationResult {
    case started
    case alreadyCurrent
    case rejected
}

@MainActor
final class WKWebViewBrowserSessionAdapter: NSObject, @preconcurrency BrowserEngineSessionPort {
    let tabId: String
    let sessionId = UUID()
    let webView: WKWebView
    let allowsCandyTrailRecording: Bool
    let profileId: String
    let usesIsolatedProfileStorage: Bool
    weak var eventSink: BrowserEngineEventSink?
    private var stateObservations: [NSKeyValueObservation] = []
    private var blockingState = BlockingState.initializing
    private var pendingLoad: BrowserEngineCommand?
    private var activeNavigation: WKNavigation?
    private var activeNavigationIsReload = false

    init(
        tabId: String,
        shared: CandySharedFacade,
        profileId: String = "candy",
        profileIsolationEnabled: Bool = false,
        isPrivate: Bool = false,
        isSessionEphemeral: Bool = false
    ) {
        self.tabId = tabId
        self.profileId = profileId
        usesIsolatedProfileStorage = profileIsolationEnabled && !isPrivate && !isSessionEphemeral
        allowsCandyTrailRecording = !isPrivate && !isSessionEphemeral

        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = Self.websiteDataStore(
            profileId: profileId,
            isolationEnabled: profileIsolationEnabled,
            isEphemeral: isPrivate || isSessionEphemeral
        )
        ToppingRuntime.shared.attach(
            configuration.userContentController,
            tabId: tabId,
            isPrivate: isPrivate
        )
        webView = WKWebView(frame: .zero, configuration: configuration)

        super.init()
        ToppingRuntime.shared.bind(webView, tabId: tabId)
        webView.navigationDelegate = self
        stateObservations = [
            webView.observe(\.url, options: [.new]) { [weak self] _, _ in
                Task { @MainActor in self?.emit(type: .statechanged) }
            },
            webView.observe(\.title, options: [.new]) { [weak self] _, _ in
                Task { @MainActor in
                    self?.emit(type: .statechanged)
                    self?.emitTrailTitle()
                }
            },
            webView.observe(\.canGoBack, options: [.new]) { [weak self] _, _ in
                Task { @MainActor in self?.emit(type: .statechanged) }
            },
            webView.observe(\.canGoForward, options: [.new]) { [weak self] _, _ in
                Task { @MainActor in self?.emit(type: .statechanged) }
            },
        ]
        Task { [weak self, controller = configuration.userContentController] in
            do {
                _ = try await CandyBlockingRuntime.shared.activate(
                    controller: controller,
                    isPrivate: isPrivate
                )
                guard let self else {
                    return
                }
                blockingState = .ready
                if let pendingLoad {
                    self.pendingLoad = nil
                    execute(command: pendingLoad)
                }
            } catch {
                guard let self else {
                    return
                }
                blockingState = .failed(error.localizedDescription)
                pendingLoad = nil
                emit(
                    type: .navigationfailed,
                    failureDescription: "Candy Blocking konnte nicht aktiviert werden: \(error.localizedDescription)"
                )
            }
        }
    }

    private static func websiteDataStore(
        profileId: String,
        isolationEnabled: Bool,
        isEphemeral: Bool
    ) -> WKWebsiteDataStore {
        if isEphemeral {
            return .nonPersistent()
        }
        guard isolationEnabled else {
            return .default()
        }
        let identifier = UUID(uuidString: profileId) ?? stableStoreIdentifier(profileId: profileId)
        return WKWebsiteDataStore(forIdentifier: identifier)
    }

    private static func stableStoreIdentifier(profileId: String) -> UUID {
        var bytes = Array(SHA256.hash(data: Data("candy-profile/\(profileId)".utf8)).prefix(16))
        bytes[6] = (bytes[6] & 0x0f) | 0x50
        bytes[8] = (bytes[8] & 0x3f) | 0x80
        return UUID(uuid: (
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[8], bytes[9], bytes[10], bytes[11],
            bytes[12], bytes[13], bytes[14], bytes[15]
        ))
    }

    func execute(command: BrowserEngineCommand) {
        switch command.type {
        case .load_:
            switch blockingState {
            case .initializing:
                pendingLoad = command
                return
            case let .failed(message):
                emit(type: .navigationfailed, failureDescription: message)
                return
            case .ready:
                break
            }
            guard
                let address = command.address,
                let url = URL(string: address)
            else {
                return
            }
            activeNavigationIsReload = false
            activeNavigation = webView.load(URLRequest(url: url))
        case .back:
            activeNavigationIsReload = false
            activeNavigation = webView.goBack()
        case .forward:
            activeNavigationIsReload = false
            activeNavigation = webView.goForward()
        case .reload:
            activeNavigationIsReload = true
            activeNavigation = webView.reload()
        case .stop:
            activeNavigation = nil
            activeNavigationIsReload = false
            webView.stopLoading()
        case .close:
            activeNavigation = nil
            activeNavigationIsReload = false
            webView.stopLoading()
            webView.navigationDelegate = nil
            stateObservations.forEach { $0.invalidate() }
            stateObservations.removeAll()
            emit(type: .closed)
        default:
            assertionFailure("Unsupported browser engine command: \(command.type)")
        }
    }

    func navigateToHistoryIndex(_ index: Int32) -> WKHistoryNavigationResult {
        let items = historyItems()
        guard index >= 0, Int(index) < items.count else {
            return .rejected
        }
        let currentIndex = webView.backForwardList.backList.count
        if Int(index) == currentIndex {
            return .alreadyCurrent
        }
        activeNavigationIsReload = false
        activeNavigation = webView.go(to: items[Int(index)])
        return activeNavigation == nil ? .rejected : .started
    }

    private func emit(
        type: BrowserEngineEventType,
        failureDescription: String? = nil
    ) {
        let rawAddress = webView.url?.absoluteString
        let address = rawAddress == "about:blank" ? nil : rawAddress
        eventSink?.browserEngineDidEmit(
            BrowserEngineEvent(
                tabId: tabId,
                type: type,
                address: address,
                title: webView.title,
                canGoBack: webView.canGoBack,
                canGoForward: webView.canGoForward,
                failureDescription: failureDescription,
                isLoading: KotlinBoolean(bool: webView.isLoading)
            )
        )
    }

    private func emitHistory(isReload: Bool) {
        guard allowsCandyTrailRecording else {
            return
        }
        let items = historyItems()
        let currentIndex = webView.backForwardList.backList.count
        guard currentIndex >= 0, currentIndex < items.count else {
            return
        }
        eventSink?.browserEngineDidUpdateHistory(
            tabId: tabId,
            sessionId: sessionId,
            snapshot: CandyTrailHistorySnapshot(
                urls: items.map { $0.url.absoluteString },
                currentIndex: Int32(currentIndex),
                isReload: isReload
            ),
            title: webView.title ?? ""
        )
    }

    private func emitTrailTitle() {
        guard
            allowsCandyTrailRecording,
            let address = webView.url?.absoluteString,
            let title = webView.title,
            !title.isEmpty
        else {
            return
        }
        eventSink?.browserEngineDidRefineTrailTitle(
            tabId: tabId,
            sessionId: sessionId,
            address: address,
            title: title
        )
    }

    private func historyItems() -> [WKBackForwardListItem] {
        let history = webView.backForwardList
        return history.backList + [history.currentItem].compactMap { $0 } + history.forwardList
    }
}

private enum BlockingState {
    case initializing
    case ready
    case failed(String)
}

extension WKWebViewBrowserSessionAdapter: WKNavigationDelegate {
    func webView(
        _ webView: WKWebView,
        didStartProvisionalNavigation navigation: WKNavigation?
    ) {
        activeNavigation = navigation
        ToppingRuntime.shared.navigationStarted(tabId: tabId)
        emit(type: .navigationstarted)
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation?) {
        guard navigation === activeNavigation else {
            return
        }
        emit(type: .statechanged)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation?) {
        guard navigation === activeNavigation else {
            return
        }
        let wasReload = activeNavigationIsReload
        emit(type: .navigationcommitted)
        emitHistory(isReload: wasReload)
        activeNavigation = nil
        activeNavigationIsReload = false
    }

    func webView(
        _ webView: WKWebView,
        didFail navigation: WKNavigation?,
        withError error: Error
    ) {
        guard navigation === activeNavigation else {
            return
        }
        activeNavigation = nil
        activeNavigationIsReload = false
        emit(type: .navigationfailed, failureDescription: error.localizedDescription)
    }

    func webView(
        _ webView: WKWebView,
        didFailProvisionalNavigation navigation: WKNavigation?,
        withError error: Error
    ) {
        guard navigation === activeNavigation else {
            return
        }
        activeNavigation = nil
        activeNavigationIsReload = false
        emit(type: .navigationfailed, failureDescription: error.localizedDescription)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        emit(
            type: .crashed,
            failureDescription: "Der Web-Inhaltsprozess wurde beendet."
        )
    }
}
