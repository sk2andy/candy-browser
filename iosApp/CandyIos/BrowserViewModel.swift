import CandyShared
import Foundation
import UIKit
import WebKit

struct BrowserTabCard: Identifiable {
    let id: String
    let title: String
    let address: String
    let isSelected: Bool
    let isFavorite: Bool
    let isPinned: Bool
    let preview: UIImage?
}

@MainActor
final class BrowserViewModel: NSObject, ObservableObject {
    @Published private(set) var address: String
    @Published private(set) var canGoBack = false
    @Published private(set) var canGoForward = false
    @Published private(set) var isLoading = false
    @Published private(set) var pageTitle = "Candy"
    @Published private(set) var errorMessage: String?
    @Published private(set) var selectedTabId: String
    @Published private(set) var tabCountLabel: String
    @Published private(set) var tabCards: [BrowserTabCard] = []
    @Published private(set) var tabOverviewMode: BrowserTabOverviewMode
    @Published private(set) var menuItems: [BrowserFeatureMenuItem] = []
    @Published private(set) var isTabOverviewVisible = false
    @Published private(set) var addressFocusRequest: Int64 = 0
    @Published var findQuery = ""
    @Published private(set) var findResultText = ""
    @Published private(set) var isFindVisible = false
    @Published private(set) var readerDocument: BrowserPageDocument?
    @Published private(set) var translationDocument: BrowserPageDocument?
    @Published private(set) var sharePayload: BrowserSharePayload?

    var activeWebView: WKWebView {
        guard let session = sessions[selectedTabId] else {
            preconditionFailure("Selected tab must own a WKWebView")
        }
        return session.webView
    }

    private let shared: CandySharedFacade
    private let chrome: BrowserChromeController
    private let sessionController: BrowserSessionController
    private let snapshotStore: BrowserTabSnapshotStore
    private let preferences: UserDefaults
    private var sessions: [String: WKWebViewBrowserSessionAdapter]
    private var favoriteAddresses: Set<String> = []
    private var pinnedTabIds: Set<String> = []
    private var pageExtractionRequestId: UUID?
    private var findRequestId: UUID?
    private var findTabId: String?
    private var findSessionId: UUID?

    override init() {
        let shared = CandySharedFacade()
        let chrome = BrowserChromeController()
        let sessionController = BrowserSessionController()
        let snapshotStore = BrowserTabSnapshotStore()
        let preferences = UserDefaults.standard
        let initialState = sessionController.state
        guard let initialTab = initialState.tabs.first else {
            preconditionFailure("Shared tab state must contain one tab")
        }
        let initialSession = WKWebViewBrowserSessionAdapter(
            tabId: initialTab.id,
            shared: shared
        )

        self.shared = shared
        self.chrome = chrome
        self.sessionController = sessionController
        self.snapshotStore = snapshotStore
        self.preferences = preferences
        sessions = [initialTab.id: initialSession]
        address = initialTab.address
        selectedTabId = initialTab.id
        tabCountLabel = initialState.countLabel
        tabOverviewMode = BrowserTabOverviewModePreference.load(from: preferences)

        super.init()

        initialSession.eventSink = self
        snapshotStore.attach(
            tabId: initialTab.id,
            sessionId: initialSession.sessionId
        )
        precondition(sessionController.attach(session: initialSession))
        synchronizeTabs()
    }

    func editAddress(_ value: String) {
        address = value
        chrome.editAddress(value: value)
    }

    func perform(_ action: BrowserChromeAction) {
        if action == .navigate {
            navigate()
        } else if action == .back {
            sessionController.executeSelected(command: BrowserEngineCommands.shared.back())
        } else if action == .forward {
            sessionController.executeSelected(command: BrowserEngineCommands.shared.forward())
        } else if action == .stop {
            sessionController.executeSelected(command: BrowserEngineCommands.shared.stop())
            synchronizeTabs()
        } else if action == .reload {
            sessionController.executeSelected(command: BrowserEngineCommands.shared.reload())
        }
    }

    func performTabs(_ intent: BrowserTabsIntent, tabId: String? = nil) {
        let closingTabId = tabId ?? selectedTabId
        if intent == .closetab && pinnedTabIds.contains(closingTabId) {
            errorMessage = "Angehefteten Tab zuerst lösen."
            return
        }
        if isFindVisible && intent != .hideoverview {
            dismissFind()
        }
        pageExtractionRequestId = nil
        if intent == .showoverview ||
            intent == .newtab ||
            (intent == .selecttab && tabId != selectedTabId) {
            captureSnapshot(tabId: selectedTabId)
        }
        sessionController.dispatchTabs(intent: intent, tabId: tabId)
        synchronizeTabs()
    }

    func performMenu(_ action: BrowserFeatureMenuAction) {
        if action == .back {
            perform(.back)
        } else if action == .forward {
            perform(.forward)
        } else if action == .reload {
            perform(.reload)
        } else if action == .stop {
            perform(.stop)
        } else if action == .newtab {
            performTabs(.newtab)
        } else if action == .closetab {
            performTabs(.closetab, tabId: selectedTabId)
        } else if action == .showtabs {
            performTabs(.showoverview)
        } else if action == .togglefavorite {
            toggleFavorite()
        } else if action == .togglepinned {
            togglePinned()
        } else if action == .findinpage {
            cancelFeaturePresentations()
            showFind()
        } else if action == .share {
            cancelFeaturePresentations()
            shareCurrentPage()
        } else if action == .openexternal {
            cancelFeaturePresentations()
            openCurrentPageExternally()
        } else if action == .print {
            cancelFeaturePresentations()
            printCurrentPage()
        } else if action == .openreader {
            cancelFeaturePresentations()
            extractCurrentPage { [weak self] document in
                guard let self else {
                    return
                }
                guard let document else {
                    self.errorMessage = "Diese Seite enthält keinen lesbaren Text."
                    return
                }
                self.errorMessage = nil
                self.readerDocument = document
            }
        } else if action == .translatepage {
            cancelFeaturePresentations()
            extractCurrentPage(maximumCharacterCount: 20_000) { [weak self] document in
                guard let self else {
                    return
                }
                guard let document else {
                    self.errorMessage = "Diese Seite enthält keinen übersetzbaren Text."
                    return
                }
                self.errorMessage = nil
                self.translationDocument = document
            }
        }
    }

    func searchInPage(backwards: Bool) {
        let query = findQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else {
            findResultText = ""
            return
        }
        guard
            let tabId = findTabId,
            let sessionId = findSessionId,
            tabId == selectedTabId,
            let session = sessions[tabId],
            session.sessionId == sessionId
        else {
            dismissFind()
            return
        }
        let requestId = UUID()
        findRequestId = requestId
        let configuration = WKFindConfiguration()
        configuration.backwards = backwards
        configuration.wraps = true
        session.webView.find(query, configuration: configuration) { [weak self] result in
            guard
                let self,
                self.findRequestId == requestId,
                self.findTabId == tabId,
                self.findSessionId == sessionId,
                self.selectedTabId == tabId,
                self.sessions[tabId]?.sessionId == sessionId
            else {
                return
            }
            self.findResultText = result.matchFound ? "Treffer" : "0 Treffer"
        }
    }

    func updateFindQuery(_ value: String) {
        findQuery = value
        findRequestId = nil
        findResultText = ""
    }

    func dismissFind() {
        let sourceSession = findTabId.flatMap { sessions[$0] }
        isFindVisible = false
        findResultText = ""
        findQuery = ""
        findRequestId = nil
        findTabId = nil
        findSessionId = nil
        sourceSession?.webView.find("", configuration: WKFindConfiguration()) { _ in }
    }

    func dismissReader() {
        readerDocument = nil
    }

    func dismissTranslation() {
        translationDocument = nil
    }

    func dismissShare() {
        sharePayload = nil
    }

    func updateTabOverviewMode(_ mode: BrowserTabOverviewMode) {
        tabOverviewMode = mode
        BrowserTabOverviewModePreference.save(mode, to: preferences)
    }

    func handleAddressSwipe(
        horizontal: Double,
        vertical: Double,
        velocityX: Double,
        viewportWidth: Double,
        isAddressEditing: Bool
    ) {
        if isFindVisible {
            dismissFind()
        }
        if !isAddressEditing {
            captureSnapshot(tabId: selectedTabId)
        }
        sessionController.dispatchTabSwitchGesture(
            dragX: horizontal,
            dragY: vertical,
            velocityX: velocityX,
            viewportWidth: viewportWidth,
            isAddressEditing: isAddressEditing
        )
        synchronizeTabs()
    }

    func handleChromeDrag(
        horizontal: Double,
        vertical: Double,
        isAddressEditing: Bool
    ) {
        guard shared.shouldOpenTabOverview(
            dragX: horizontal,
            dragY: vertical,
            touchSlop: 8,
            isAddressEditing: isAddressEditing
        ) else {
            return
        }
        performTabs(.showoverview)
    }

    private func navigate() {
        if isFindVisible {
            dismissFind()
        }
        pageExtractionRequestId = nil
        let resolution = sessionController.navigateSelected(input: address)
        guard let value = resolution.url?.value else {
            errorMessage = "Diese Adresse kann nicht geöffnet werden."
            return
        }

        address = value
        errorMessage = nil
        synchronizeTabs()
    }

    private func synchronizeTabs() {
        let state = sessionController.state
        let activeIds = Set(state.tabs.map(\.id))

        for tab in state.tabs where sessions[tab.id] == nil {
            let session = WKWebViewBrowserSessionAdapter(
                tabId: tab.id,
                shared: shared
            )
            session.eventSink = self
            sessions[tab.id] = session
            snapshotStore.attach(tabId: tab.id, sessionId: session.sessionId)
            precondition(sessionController.attach(session: session))
        }
        sessions = sessions.filter { activeIds.contains($0.key) }
        pinnedTabIds.formIntersection(activeIds)
        snapshotStore.removeTabs(notIn: activeIds)

        selectedTabId = state.selectedTabId
        tabCountLabel = state.countLabel
        isTabOverviewVisible = state.isOverviewVisible
        addressFocusRequest = state.addressFocusRequest
        publishTabCards(state: state)

        guard let selectedTab = state.tabs.first(where: { $0.id == state.selectedTabId }) else {
            return
        }
        address = selectedTab.address
        canGoBack = selectedTab.canGoBack
        canGoForward = selectedTab.canGoForward
        isLoading = selectedTab.isLoading
        pageTitle = selectedTab.title.isEmpty ? "Candy" : selectedTab.title
        menuItems = featureMenuItems(for: selectedTab)
        chrome.navigationFinished(
            address: selectedTab.address,
            pageTitle: selectedTab.title,
            canGoBack: selectedTab.canGoBack,
            canGoForward: selectedTab.canGoForward
        )
        if selectedTab.isLoading {
            chrome.navigationStarted()
        }
    }

    private func publishTabCards(state: BrowserTabsState) {
        tabCards = state.tabs.map { tab in
            BrowserTabCard(
                id: tab.id,
                title: tab.title,
                address: tab.address,
                isSelected: tab.id == state.selectedTabId,
                isFavorite: favoriteAddresses.contains(tab.address),
                isPinned: pinnedTabIds.contains(tab.id),
                preview: snapshotStore.image(for: tab.id)
            )
        }
    }

    private func captureSnapshot(tabId: String) {
        let state = sessionController.state
        guard
            let tab = state.tabs.first(where: { $0.id == tabId }),
            !tab.address.isEmpty,
            let session = sessions[tabId]
        else {
            return
        }
        let activeTabIds = Set(state.tabs.map(\.id))
        snapshotStore.capture(
            tabId: tabId,
            webView: session.webView,
            sessionId: session.sessionId,
            activeTabIds: activeTabIds
        ) { [weak self] in
            guard let self else {
                return
            }
            self.publishTabCards(state: self.sessionController.state)
        }
    }

    private func featureMenuItems(for tab: BrowserTabState) -> [BrowserFeatureMenuItem] {
        let hasPage = !tab.address.isEmpty
        let state = BrowserFeatureMenuState(
            canGoBack: tab.canGoBack,
            canGoForward: tab.canGoForward,
            isLoading: tab.isLoading,
            hasPage: hasPage,
            canCloseTab: !pinnedTabIds.contains(tab.id),
            canToggleFavorite: hasPage,
            isFavorite: favoriteAddresses.contains(tab.address),
            isPinned: pinnedTabIds.contains(tab.id),
            canOpenReader: hasPage,
            canTranslatePage: hasPage,
            canUseDocumentActions: hasPage,
            canToggleCookieBannerRemoval: false,
            isCookieBannerRemovalEnabled: false,
            canToggleForceVerticalScrolling: false,
            isForceVerticalScrollingEnabled: false,
            canToggleForcePageZooming: false,
            isForcePageZoomingEnabled: false,
            canToggleForceSafeArea: false,
            isForceSafeAreaEnabled: false,
            canToggleAlwaysBlockPopups: false,
            isAlwaysBlockPopupsEnabled: false,
            canToggleDesktopView: false,
            isDesktopView: false,
            canToggleDomainMute: false,
            isDomainMuted: false,
            canAddSiteCapsule: false,
            canSnooze: false,
            canDockAddressBar: false,
            overflowPageActions: [.showtabs, .newtab, .closetab],
            toppingCommands: []
        )
        return BrowserFeatureMenuRules.shared.items(
            state: state,
            capabilities: BrowserFeatureMenuCapabilities(supportsFirefoxExtensions: false)
        ).filter { item in
            isImplementedFeatureAction(item.action)
        }
    }

    private func isImplementedFeatureAction(_ action: BrowserFeatureMenuAction) -> Bool {
        action == .back ||
            action == .forward ||
            action == .reload ||
            action == .stop ||
            action == .togglefavorite ||
            action == .togglepinned ||
            action == .showtabs ||
            action == .newtab ||
            action == .closetab ||
            action == .openreader ||
            action == .translatepage ||
            action == .findinpage ||
            action == .share ||
            action == .openexternal ||
            action == .print
    }

    private func toggleFavorite() {
        let value = sessionController.state.tabs
            .first(where: { $0.id == selectedTabId })?
            .address ?? ""
        guard !value.isEmpty else {
            return
        }
        if favoriteAddresses.contains(value) {
            favoriteAddresses.remove(value)
        } else {
            favoriteAddresses.insert(value)
        }
        synchronizeTabs()
    }

    private func togglePinned() {
        if pinnedTabIds.contains(selectedTabId) {
            pinnedTabIds.remove(selectedTabId)
        } else {
            pinnedTabIds.insert(selectedTabId)
        }
        synchronizeTabs()
    }

    private func showFind() {
        let session = sessions[selectedTabId]
        findTabId = selectedTabId
        findSessionId = session?.sessionId
        findRequestId = nil
        findResultText = ""
        isFindVisible = session != nil
    }

    private func cancelFeaturePresentations() {
        pageExtractionRequestId = nil
        readerDocument = nil
        translationDocument = nil
        sharePayload = nil
    }

    private func shareCurrentPage() {
        guard let url = activeWebView.url else {
            errorMessage = "Diese Seite kann noch nicht geteilt werden."
            return
        }
        errorMessage = nil
        sharePayload = BrowserSharePayload(title: pageTitle, url: url)
    }

    private func openCurrentPageExternally() {
        guard let url = activeWebView.url else {
            errorMessage = "Diese Seite kann noch nicht extern geöffnet werden."
            return
        }
        UIApplication.shared.open(url) { [weak self] didOpen in
            if !didOpen {
                self?.errorMessage = "Für diese Adresse ist keine externe App verfügbar."
            }
        }
    }

    private func printCurrentPage() {
        guard UIPrintInteractionController.isPrintingAvailable else {
            errorMessage = "Drucken ist auf diesem Gerät nicht verfügbar."
            return
        }
        let controller = UIPrintInteractionController.shared
        controller.printInfo = UIPrintInfo(dictionary: nil)
        controller.printInfo?.jobName = pageTitle
        controller.printFormatter = activeWebView.viewPrintFormatter()
        if controller.present(animated: true) {
            errorMessage = nil
        } else {
            errorMessage = "Der Druckdialog konnte nicht geöffnet werden."
        }
    }

    private func extractCurrentPage(
        maximumCharacterCount: Int = 120_000,
        completion: @escaping @MainActor (BrowserPageDocument?) -> Void
    ) {
        let tabId = selectedTabId
        guard let session = sessions[tabId] else {
            completion(nil)
            return
        }
        let sessionId = session.sessionId
        let pageIdentity = session.webView.url?.absoluteString
        let requestId = UUID()
        pageExtractionRequestId = requestId
        let safeMaximum = max(1, maximumCharacterCount)
        let script = """
        (() => {
          const content = document.querySelector('article, main, [role="main"]') || document.body;
          const text = (content && content.innerText ? content.innerText : '').trim();
          return {
            title: document.title || '',
            address: location.href || '',
            text: text.slice(0, \(safeMaximum))
          };
        })()
        """
        session.webView.evaluateJavaScript(script) { [weak self, weak session] result, _ in
            guard
                let self,
                let session,
                self.pageExtractionRequestId == requestId,
                self.selectedTabId == tabId,
                self.sessions[tabId] === session,
                session.sessionId == sessionId,
                session.webView.url?.absoluteString == pageIdentity
            else {
                return
            }
            self.pageExtractionRequestId = nil
            completion(
                BrowserPageExtractionRules.document(
                    from: result,
                    maximumCharacterCount: safeMaximum
                )
            )
        }
    }
}

extension BrowserViewModel: BrowserEngineEventSink {
    func browserEngineDidEmit(_ event: BrowserEngineEvent) {
        if event.type == .navigationstarted {
            if event.tabId == findTabId {
                dismissFind()
            }
            pageExtractionRequestId = nil
            snapshotStore.navigationStarted(tabId: event.tabId)
        } else if event.type == .statechanged {
            snapshotStore.pageStateChanged(
                tabId: event.tabId,
                pageIdentity: event.address
            )
        } else if event.type == .crashed {
            snapshotStore.invalidate(tabId: event.tabId)
            sessions.removeValue(forKey: event.tabId)?.eventSink = nil
        }
        if event.tabId == selectedTabId {
            if event.type == .navigationstarted {
                errorMessage = nil
            } else if event.type == .navigationfailed {
                errorMessage = event.failureDescription
            }
        }
        sessionController.onEngineEvent(event: event)
        synchronizeTabs()
        if event.type == .navigationcommitted {
            captureSnapshot(tabId: event.tabId)
        }
    }
}
