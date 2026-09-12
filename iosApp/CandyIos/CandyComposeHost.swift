import CandyShared
import SwiftUI
import UIKit

struct CandyComposeHost: View {
    @ObservedObject var browser: BrowserViewModel

    var body: some View {
        ZStack(alignment: .top) {
            CandyComposeControllerHost(browser: browser)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .ignoresSafeArea(edges: composeIgnoredSafeAreaEdges)

            CandyNativeBrowserChrome(browser: browser)
                .zIndex(10)

            if browser.isFavoritesVisible {
                BrowserFavoritesSurface(
                    items: browser.favoriteItems,
                    pendingRemoval: browser.pendingFavoriteRemoval,
                    onOpen: browser.openFavorite,
                    onDelete: browser.deleteFavorite,
                    onUndo: browser.undoFavoriteRemoval,
                    onDismissRemoval: browser.dismissFavoriteRemoval,
                    onDismiss: browser.dismissFavorites
                )
                .zIndex(30)
            }

            if let error = browser.errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                    .padding(.horizontal, 12)
            }

            if browser.isFindVisible {
                BrowserFindBar(
                    query: Binding(
                        get: { browser.findQuery },
                        set: { browser.updateFindQuery($0) }
                    ),
                    resultText: browser.findResultText,
                    onSearch: browser.searchInPage,
                    onDismiss: browser.dismissFind
                )
                .padding(.top, 8)
                .zIndex(20)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .sheet(
            item: Binding(
                get: { browser.sharePayload },
                set: { if $0 == nil { browser.dismissShare() } }
            )
        ) { payload in
            BrowserActivitySurface(payload: payload)
        }
    }

    private var composeIgnoredSafeAreaEdges: Edge.Set {
        let usesBrowserSurface = !browser.isSettingsVisible &&
            !browser.isFavoritesVisible &&
            browser.readerSnapshot == nil &&
            browser.candyTrailTabId == nil
        return usesBrowserSurface ? [.top, .bottom] : .top
    }
}

private struct CandyComposeControllerHost: UIViewControllerRepresentable {
    @ObservedObject var browser: BrowserViewModel

    func makeCoordinator() -> Coordinator {
        Coordinator(browser: browser)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        CandyComposeControllerFactory().create(
            state: context.coordinator.state,
            actionSink: context.coordinator
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        context.coordinator.state.updateViewport(value: browser.activeWebView)
        browser.tabCards.forEach { tab in
            context.coordinator.state.updatePreview(tabId: tab.id, value: tab.preview)
        }
        context.coordinator.state.retainPreviews(tabIds: browser.tabCards.map(\.id))
        context.coordinator.state.updateSnapshot(value: snapshot)
    }

    private var snapshot: BrowserViewportSnapshot {
        BrowserViewportSnapshot(
            address: browser.address,
            pageTitle: browser.pageTitle,
            tabCountLabel: browser.tabCountLabel,
            canGoBack: browser.canGoBack,
            canGoForward: browser.canGoForward,
            isLoading: browser.isLoading,
            menuItems: browser.menuItems,
            tabs: browser.tabCards
                .filter { $0.profileId == browser.activeProfileId }
                .map { tab in
                BrowserViewportTab(
                    id: tab.id,
                    title: tab.title,
                    address: tab.address,
                    isSelected: tab.isSelected,
                    isFavorite: tab.isFavorite,
                    isPinned: tab.isPinned
                )
            },
            profiles: browser.profiles.map { profile in
                BrowserViewportProfile(
                    id: profile.id,
                    emoji: profile.emoji,
                    isolationEnabled: profile.isolationEnabled,
                    displayName: profile.syncedDisplayName,
                    syncedIconEmoji: profile.syncedIconEmoji,
                    syncedIconAccentHue: profile.syncedIconAccentHue,
                    isSyncLinked: profile.syncedDeviceId != nil ||
                        profile.linkedSyncDeviceId != nil
                )
            },
            profileIconEmojis: browser.profileIconEmojis,
            profileIsolationSupported: true,
            activeProfileId: browser.activeProfileId,
            isTabOverviewVisible: browser.isTabOverviewVisible,
            isSettingsVisible: browser.isSettingsVisible,
            tabOverviewMode: browser.tabOverviewMode.sharedMode,
            tabListStartsAtBottom: browser.tabListStartsAtBottom,
            addressFocusRequest: browser.addressFocusRequest,
            searchEngine: browser.searchEngine,
            searxngInstanceUrl: browser.searxngInstanceUrl,
            translationProvider: browser.translationProvider,
            toppings: browser.toppings.map(\.sharedViewportTopping),
            reader: browser.readerSnapshot,
            candyTrail: candyTrailSnapshot,
            syncSettings: browser.syncSettingsUiState
        )
    }

    private var candyTrailSnapshot: BrowserCandyTrailSnapshot? {
        Self.candyTrailSnapshot(browser: browser)
    }

    private static func candyTrailSnapshot(
        browser: BrowserViewModel
    ) -> BrowserCandyTrailSnapshot? {
        guard
            let tabId = browser.candyTrailTabId,
            let trail = browser.candyTrail(tabId: tabId)
        else {
            return nil
        }
        return BrowserCandyTrailSnapshot(
            tabId: tabId,
            trail: trail
        )
    }

    @MainActor
    final class Coordinator: NSObject, @preconcurrency BrowserViewportActionSink {
        let state: IosBrowserViewportState
        private let browser: BrowserViewModel

        init(browser: BrowserViewModel) {
            self.browser = browser
            state = IosBrowserViewportState(
                initialViewport: browser.activeWebView,
                initialSnapshot: BrowserViewportSnapshot(
                    address: browser.address,
                    pageTitle: browser.pageTitle,
                    tabCountLabel: browser.tabCountLabel,
                    canGoBack: browser.canGoBack,
                    canGoForward: browser.canGoForward,
                    isLoading: browser.isLoading,
                    menuItems: browser.menuItems,
                    tabs: browser.tabCards
                        .filter { $0.profileId == browser.activeProfileId }
                        .map { tab in
                        BrowserViewportTab(
                            id: tab.id,
                            title: tab.title,
                            address: tab.address,
                            isSelected: tab.isSelected,
                            isFavorite: tab.isFavorite,
                            isPinned: tab.isPinned
                        )
                    },
                    profiles: browser.profiles.map { profile in
                        BrowserViewportProfile(
                            id: profile.id,
                            emoji: profile.emoji,
                            isolationEnabled: profile.isolationEnabled,
                            displayName: profile.syncedDisplayName,
                            syncedIconEmoji: profile.syncedIconEmoji,
                            syncedIconAccentHue: profile.syncedIconAccentHue,
                            isSyncLinked: profile.syncedDeviceId != nil ||
                                profile.linkedSyncDeviceId != nil
                        )
                    },
                    profileIconEmojis: browser.profileIconEmojis,
                    profileIsolationSupported: true,
                    activeProfileId: browser.activeProfileId,
                    isTabOverviewVisible: browser.isTabOverviewVisible,
                    isSettingsVisible: browser.isSettingsVisible,
                    tabOverviewMode: browser.tabOverviewMode.sharedMode,
                    tabListStartsAtBottom: browser.tabListStartsAtBottom,
                    addressFocusRequest: browser.addressFocusRequest,
                    searchEngine: browser.searchEngine,
                    searxngInstanceUrl: browser.searxngInstanceUrl,
                    translationProvider: browser.translationProvider,
                    toppings: browser.toppings.map(\.sharedViewportTopping),
                    reader: browser.readerSnapshot,
                    candyTrail: CandyComposeControllerHost.candyTrailSnapshot(browser: browser),
                    syncSettings: browser.syncSettingsUiState
                )
            )
        }

        func addressChanged(value: String) {
            browser.editAddress(value)
        }

        func perform(action: CandyBrowserUiAction) {
            if action == .navigate {
                browser.perform(.navigate)
            } else if action == .back {
                browser.perform(.back)
            } else if action == .forward {
                browser.perform(.forward)
            } else if action == .reload {
                browser.perform(.reload)
            } else if action == .stop {
                browser.perform(.stop)
            } else if action == .newtab {
                browser.performTabs(.newtab)
            } else if action == .showtabs {
                browser.performTabs(.showoverview)
            }
        }

        func performMenu(item: BrowserFeatureMenuItem) {
            browser.performMenu(item)
        }

        func performTabAction(tabId: String, action: BrowserFeatureMenuAction) {
            browser.performTabAction(tabId: tabId, action: action)
        }

        func addressDragged(
            horizontal: Double,
            vertical: Double,
            velocityX: Double,
            viewportWidth: Double,
            isAddressEditing: Bool
        ) {
            browser.handleAddressSwipe(
                horizontal: horizontal,
                vertical: vertical,
                velocityX: velocityX,
                viewportWidth: viewportWidth,
                isAddressEditing: isAddressEditing
            )
            browser.handleChromeDrag(
                horizontal: horizontal,
                vertical: vertical,
                isAddressEditing: isAddressEditing
            )
        }

        func selectTab(tabId: String) {
            browser.performTabs(.selecttab, tabId: tabId)
        }

        func selectTabInOverview(tabId: String) {
            browser.performTabs(.selecttabinoverview, tabId: tabId)
        }

        func closeTab(tabId: String) {
            browser.performTabs(.closetab, tabId: tabId)
        }

        func selectProfile(profileId: String) {
            browser.selectProfile(profileId)
        }

        func createProfile(emoji: String, isolationEnabled: Bool) {
            browser.createProfile(
                emoji: emoji,
                isolationEnabled: isolationEnabled
            )
        }

        func updateProfileEmoji(profileId: String, emoji: String) {
            browser.updateProfileEmoji(profileId: profileId, emoji: emoji)
        }

        func setProfileIsolation(profileId: String, enabled: Bool) {
            browser.setProfileIsolation(profileId: profileId, enabled: enabled)
        }

        func hideTabOverview() {
            browser.performTabs(.hideoverview)
        }

        func showSettings() {
            browser.showSettings()
        }

        func dismissSettings() {
            browser.dismissSettings()
        }

        func configure(settings: SyncConnectionSettings) -> Bool {
            browser.configureSync(settings)
        }

        func enroll(
            serverPassword: KotlinCharArray,
            passphrase: KotlinCharArray,
            onComplete: @escaping (SyncEnrollmentOutcome) -> Void
        ) {
            browser.enrollSync(
                serverPassword: serverPassword,
                passphrase: passphrase,
                completion: onComplete
            )
        }

        func refresh() {
            browser.refreshSync()
        }

        func changeTabOverviewMode(mode: TabOverviewMode) {
            if mode == .hero {
                browser.updateTabOverviewMode(.hero)
            } else if mode == .grid {
                browser.updateTabOverviewMode(.grid)
            } else {
                browser.updateTabOverviewMode(.list)
            }
        }

        func changeTabListStartsAtBottom(enabled: Bool) {
            browser.updateTabListStartsAtBottom(enabled)
        }

        func changeSearchEngine(searchEngine: SearchEngine) {
            browser.updateSearchEngine(searchEngine)
        }

        func changeSearxngInstanceUrl(value: String) {
            browser.updateSearxngInstanceUrl(value)
        }

        func changeTranslationProvider(provider: PageTranslationProvider) {
            browser.updateTranslationProvider(provider)
        }

        func saveTopping(id: String?, source: String) {
            browser.saveTopping(id: id, source: source)
        }

        func toppingSource(id: String) -> String? {
            browser.toppingSource(id: id)
        }

        func setToppingEnabled(id: String, enabled: Bool) {
            browser.setToppingEnabled(id: id, enabled: enabled)
        }

        func deleteTopping(id: String) {
            browser.deleteTopping(id: id)
        }

        func retryReader() {
            browser.retryReader()
        }

        func dismissReader() {
            browser.dismissReader()
        }

        func openReaderOriginal(url: String) {
            browser.openReaderOriginal(url)
        }

        func openReaderLink(url: String) {
            browser.openReaderLink(url)
        }

        func dismissCandyTrail() {
            browser.closeCandyTrail()
        }

        func selectCandyTrailNode(tabId: String, nodeId: String) -> Bool {
            browser.selectCandyTrailNode(tabId: tabId, nodeId: nodeId)
        }

        func forkCandyTrailNode(tabId: String, nodeId: String) -> String? {
            browser.forkCandyTrailNode(tabId: tabId, nodeId: nodeId)
        }

        func activateCandyTrailFork(tabId: String, forkId: String) -> String? {
            browser.activateCandyTrailFork(tabId: tabId, forkId: forkId)
        }
    }
}

private extension BrowserTabOverviewMode {
    var sharedMode: TabOverviewMode {
        switch self {
        case .hero: .hero
        case .grid: .grid
        case .list: .list
        }
    }
}

private extension StoredTopping {
    var sharedViewportTopping: BrowserViewportTopping {
        let parsed = ToppingRules.shared.parse(id: id, source: source, enabled: enabled)
        let name = (parsed as? ToppingParseResultAccepted)?.script.name ?? id
        return BrowserViewportTopping(
            id: id,
            name: name,
            enabled: enabled
        )
    }
}

extension BrowserPageDocument {
    func sharedReaderSnapshot(isPrivate: Bool) -> BrowserReaderSnapshot {
        let sourceUrl = address
        let document = ReaderDocument(
            title: title,
            sourceUrl: sourceUrl,
            siteName: URL(string: sourceUrl)?.host ?? "",
            blocks: [
                ReaderBlock(
                    kind: .paragraph,
                    text: text,
                    level: 0,
                    links: []
                )
            ]
        )
        return BrowserReaderSnapshot(
            result: ReaderExtractionResultSuccess(document: document),
            sourceUrl: sourceUrl,
            isPrivate: isPrivate
        )
    }
}
