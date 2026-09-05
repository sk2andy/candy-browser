import CandyShared
import Foundation
import WebKit

@MainActor
final class BrowserViewModel: NSObject, ObservableObject {
    @Published var address = "https://www.mozilla.org" {
        didSet {
            chrome.editAddress(value: address)
        }
    }
    @Published private(set) var canGoBack = false
    @Published private(set) var canGoForward = false
    @Published private(set) var isLoading = false
    @Published private(set) var pageTitle = "Candy"
    @Published private(set) var errorMessage: String?

    let webView: WKWebView

    private let shared = CandySharedFacade()
    private let chrome = BrowserChromeController()

    override init() {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        ToppingInstaller.install(
            CandySharedFacade().bundledDemoTopping(),
            into: configuration.userContentController
        )
        webView = WKWebView(frame: .zero, configuration: configuration)

        super.init()

        webView.navigationDelegate = self
        navigate()
    }

    func perform(_ action: BrowserChromeAction) {
        if action == .navigate {
            navigate()
        } else if action == .back {
            webView.goBack()
        } else if action == .forward {
            webView.goForward()
        } else if action == .stop {
            webView.stopLoading()
        } else if action == .reload {
            webView.reload()
        }
    }

    private func navigate() {
        let resolution = shared.resolveAddress(input: address)
        guard
            let value = resolution.url?.value,
            let url = URL(string: value)
        else {
            errorMessage = "Diese Adresse kann nicht geöffnet werden."
            return
        }

        address = value
        errorMessage = nil
        webView.load(URLRequest(url: url))
    }

    func performReloadAction() {
        perform(chrome.reloadAction())
    }

    private func synchronizeChrome() {
        let state = chrome.navigationFinished(
            address: webView.url?.absoluteString ?? address,
            pageTitle: webView.title ?? "",
            canGoBack: webView.canGoBack,
            canGoForward: webView.canGoForward
        )
        address = state.address
        canGoBack = state.canGoBack
        canGoForward = state.canGoForward
        isLoading = state.isLoading
        pageTitle = state.pageTitle
    }
}

extension BrowserViewModel: WKNavigationDelegate {
    func webView(
        _ webView: WKWebView,
        didStartProvisionalNavigation navigation: WKNavigation?
    ) {
        synchronizeChrome()
        let state = chrome.navigationStarted()
        isLoading = state.isLoading
        errorMessage = nil
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation?) {
        isLoading = false
        synchronizeChrome()
    }

    func webView(
        _ webView: WKWebView,
        didFail navigation: WKNavigation?,
        withError error: Error
    ) {
        isLoading = false
        errorMessage = error.localizedDescription
        synchronizeChrome()
    }

    func webView(
        _ webView: WKWebView,
        didFailProvisionalNavigation navigation: WKNavigation?,
        withError error: Error
    ) {
        isLoading = false
        errorMessage = error.localizedDescription
        synchronizeChrome()
    }
}
