import CandyShared
import Foundation
import WebKit

@MainActor
protocol BrowserEngineEventSink: AnyObject {
    func browserEngineDidEmit(_ event: BrowserEngineEvent)
}

@MainActor
final class WKWebViewBrowserSessionAdapter: NSObject, @preconcurrency BrowserEngineSessionPort {
    let tabId: String
    let sessionId = UUID()
    let webView: WKWebView
    weak var eventSink: BrowserEngineEventSink?
    private var stateObservations: [NSKeyValueObservation] = []
    private var blockingState = BlockingState.initializing
    private var pendingLoad: BrowserEngineCommand?
    private var activeNavigation: WKNavigation?

    init(tabId: String, shared: CandySharedFacade, isPrivate: Bool = false) {
        self.tabId = tabId

        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = isPrivate ? .nonPersistent() : .default()
        ToppingRuntime.shared.attach(configuration.userContentController, isPrivate: isPrivate)
        webView = WKWebView(frame: .zero, configuration: configuration)

        super.init()
        webView.navigationDelegate = self
        stateObservations = [
            webView.observe(\.url, options: [.new]) { [weak self] _, _ in
                Task { @MainActor in self?.emit(type: .statechanged) }
            },
            webView.observe(\.title, options: [.new]) { [weak self] _, _ in
                Task { @MainActor in self?.emit(type: .statechanged) }
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
            activeNavigation = webView.load(URLRequest(url: url))
        case .back:
            activeNavigation = webView.goBack()
        case .forward:
            activeNavigation = webView.goForward()
        case .reload:
            activeNavigation = webView.reload()
        case .stop:
            activeNavigation = nil
            webView.stopLoading()
        case .close:
            activeNavigation = nil
            webView.stopLoading()
            webView.navigationDelegate = nil
            stateObservations.forEach { $0.invalidate() }
            stateObservations.removeAll()
            emit(type: .closed)
        default:
            assertionFailure("Unsupported browser engine command: \(command.type)")
        }
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
                failureDescription: failureDescription
            )
        )
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
        emit(type: .navigationcommitted)
        activeNavigation = nil
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
        emit(type: .navigationfailed, failureDescription: error.localizedDescription)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        emit(
            type: .crashed,
            failureDescription: "Der Web-Inhaltsprozess wurde beendet."
        )
    }
}
