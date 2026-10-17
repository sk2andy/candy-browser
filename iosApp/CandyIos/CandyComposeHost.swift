import CandyShared
import SwiftUI
import UIKit
import WebKit

struct CandyComposeHost: View {
    @ObservedObject var browser: BrowserViewModel

    var body: some View {
        ZStack(alignment: .top) {
            CandyComposeControllerHost(browser: browser)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .ignoresSafeArea(edges: composeIgnoredSafeAreaEdges)

            CandyNativeBrowserChrome(browser: browser)
                .zIndex(10)

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
        .preferredColorScheme(preferredColorScheme)
        .sheet(
            item: Binding(
                get: { browser.sharePayload },
                set: { if $0 == nil { browser.dismissShare() } }
            )
        ) { payload in
            BrowserActivitySurface(payload: payload)
        }
        .sheet(isPresented: $browser.isCustomSnoozeVisible) {
            BrowserSnoozeDatePicker(
                onCancel: { browser.isCustomSnoozeVisible = false },
                onConfirm: browser.applyCustomSnooze
            )
            .presentationDetents([.medium])
        }
    }

    private var preferredColorScheme: ColorScheme? {
        if browser.appearanceSettings.appearanceMode == .light { return .light }
        if browser.appearanceSettings.appearanceMode == .dark ||
            browser.appearanceSettings.appearanceMode == .amoled { return .dark }
        return nil
    }

    private var composeIgnoredSafeAreaEdges: Edge.Set {
        let usesBrowserSurface = !browser.isSettingsVisible &&
            !browser.isFavoritesVisible &&
            !browser.isHistoryVisible &&
            !browser.isSnoozedTabsVisible &&
            browser.readerSnapshot == nil &&
            browser.candyTrailTabId == nil
        return usesBrowserSurface ? [.top, .bottom] : .top
    }
}

/// Compose sizes this stable UIKit container; WebKit receives every initial/window bounds change.
@MainActor
private final class BrowserViewportContainer: UIView {
    private var viewport: UIView?
    private var updatingInsets = false

    init(view: UIView) {
        super.init(frame: .zero)
        clipsToBounds = true
        isAccessibilityElement = false
        isUserInteractionEnabled = true
        mount(view)
    }

    required init?(coder: NSCoder) {
        fatalError("BrowserViewportContainer requires its engine view")
    }

    func mount(_ view: UIView) {
        guard viewport !== view else { return }
        viewport?.removeFromSuperview()
        viewport = view
        view.frame = bounds
        view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        addSubview(view)
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if viewport?.frame != bounds { viewport?.frame = bounds }
        updateViewportInsets()
    }

    override func safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()
        updateViewportInsets()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window != nil else { return }
        setNeedsLayout()
        layoutIfNeeded()
    }

    private func updateViewportInsets() {
        guard !updatingInsets, let window, let webView = viewport as? WKWebView,
              webView.bounds.height > 0 else { return }
        updatingInsets = true
        defer { updatingInsets = false }

        let viewportFrame = webView.convert(webView.bounds, to: window)
        let topInset = CGFloat(BrowserViewportInsetsRules.topInset(
            windowSafeAreaTop: Double(window.safeAreaLayoutGuide.layoutFrame.minY),
            viewportTop: Double(viewportFrame.minY),
            viewportHeight: Double(webView.bounds.height)
        ))
        let scrollView = webView.scrollView
        let previousAdjustedTop = scrollView.adjustedContentInset.top
        let previousOffset = scrollView.contentOffset
        if #available(iOS 26.0, *) {
            // Tell WebKit this area is already consumed by browser UI. This also removes it
            // from CSS safe-area-inset-top, so viewport-fit=cover pages do not pad twice.
            var obscuredInsets = webView.obscuredContentInsets
            obscuredInsets.top = topInset
            if webView.obscuredContentInsets != obscuredInsets {
                webView.obscuredContentInsets = obscuredInsets
            }
        }
        if scrollView.contentInsetAdjustmentBehavior != .never {
            scrollView.contentInsetAdjustmentBehavior = .never
        }
        if scrollView.automaticallyAdjustsScrollIndicatorInsets {
            scrollView.automaticallyAdjustsScrollIndicatorInsets = false
        }
        var contentInsets = scrollView.contentInset
        contentInsets.top = topInset
        if scrollView.contentInset != contentInsets { scrollView.contentInset = contentInsets }
        var indicatorInsets = scrollView.verticalScrollIndicatorInsets
        indicatorInsets.top = topInset
        if scrollView.verticalScrollIndicatorInsets != indicatorInsets {
            scrollView.verticalScrollIndicatorInsets = indicatorInsets
        }
        guard !scrollView.isDragging, !scrollView.isDecelerating else { return }
        let offsetY = CGFloat(BrowserViewportInsetsRules.topAnchoredOffset(
            currentOffset: Double(previousOffset.y),
            previousAdjustedInset: Double(previousAdjustedTop),
            newInset: Double(topInset)
        ))
        if offsetY != scrollView.contentOffset.y {
            scrollView.setContentOffset(CGPoint(x: previousOffset.x, y: offsetY), animated: false)
        }
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
        context.coordinator.viewport.mount(browser.activeWebView)
        browser.viewportDidMount(tabId: browser.selectedTabId)
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
                    isPinned: tab.isPinned,
                    isPrivate: tab.isPrivate,
                    moveTargetProfileIds: browser.moveTargetProfileIds(tabId: tab.id),
                    canSnooze: browser.canSnooze(tabId: tab.id)
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
                        profile.linkedSyncDeviceId != nil,
                    canDelete: browser.canDeleteProfile(profileId: profile.id)
                )
            },
            profileIconEmojis: browser.profileIconEmojis,
            profileIsolationSupported: true,
            activeProfileId: browser.activeProfileId,
            isTabOverviewVisible: browser.isTabOverviewVisible,
            isSettingsVisible: browser.isSettingsVisible,
            isBrowserMenuVisible: browser.isBrowserMenuVisible,
            tabOverviewMode: browser.tabOverviewMode.sharedMode,
            tabListStartsAtBottom: browser.tabListStartsAtBottom,
            dismissResistancePercent: browser.dismissResistancePercent,
            appearanceSettings: browser.appearanceSettings,
            addressFocusRequest: browser.addressFocusRequest,
            searchEngine: browser.searchEngine,
            searxngInstanceUrl: browser.searxngInstanceUrl,
            translationProvider: browser.translationProvider,
            menuLayout: browser.menuLayout,
            menuConfigurableEntries: browser.menuConfigurableEntries,
            toppings: browser.toppings.map(\.sharedViewportTopping),
            reader: browser.readerSnapshot,
            library: browser.librarySnapshot,
            snooze: browser.snoozeSnapshot,
            snoozedTabCount: Int32(browser.snoozedTabs.count),
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
        let viewport: BrowserViewportContainer
        private let browser: BrowserViewModel

        init(browser: BrowserViewModel) {
            self.browser = browser
            let viewport = BrowserViewportContainer(view: browser.activeWebView)
            self.viewport = viewport
            state = IosBrowserViewportState(
                initialViewport: viewport,
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
                            isPinned: tab.isPinned,
                            isPrivate: tab.isPrivate,
                            moveTargetProfileIds: browser.moveTargetProfileIds(tabId: tab.id),
                            canSnooze: browser.canSnooze(tabId: tab.id)
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
                                profile.linkedSyncDeviceId != nil,
                            canDelete: browser.canDeleteProfile(profileId: profile.id)
                        )
                    },
                    profileIconEmojis: browser.profileIconEmojis,
                    profileIsolationSupported: true,
                    activeProfileId: browser.activeProfileId,
                    isTabOverviewVisible: browser.isTabOverviewVisible,
                    isSettingsVisible: browser.isSettingsVisible,
                    isBrowserMenuVisible: browser.isBrowserMenuVisible,
                    tabOverviewMode: browser.tabOverviewMode.sharedMode,
                    tabListStartsAtBottom: browser.tabListStartsAtBottom,
                    dismissResistancePercent: browser.dismissResistancePercent,
                    appearanceSettings: browser.appearanceSettings,
                    addressFocusRequest: browser.addressFocusRequest,
                    searchEngine: browser.searchEngine,
                    searxngInstanceUrl: browser.searxngInstanceUrl,
                    translationProvider: browser.translationProvider,
                    menuLayout: browser.menuLayout,
                    menuConfigurableEntries: browser.menuConfigurableEntries,
                    toppings: browser.toppings.map(\.sharedViewportTopping),
                    reader: browser.readerSnapshot,
                    library: browser.librarySnapshot,
                    snooze: browser.snoozeSnapshot,
                    snoozedTabCount: Int32(browser.snoozedTabs.count),
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

        func closeAllTabs() { browser.closeAllTabs() }

        func moveTabToProfile(tabId: String, profileId: String) {
            browser.moveTabToProfile(tabId: tabId, profileId: profileId)
        }

        func deleteProfile(profileId: String) { browser.deleteProfile(profileId: profileId) }

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

        func openExtensionStore(url: String) {
            browser.openSyncExtensionStore(url)
        }

        func shareExtensionStore(url: String) {
            browser.shareSyncExtensionStore(url)
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

        func dismissSnooze() { browser.dismissSnooze() }
        func applySnoozePreset(preset: SnoozePreset) { browser.applySnoozePreset(preset) }
        func customSnooze() { browser.customSnooze() }
        func rescheduleSnoozedTab(tabId: String) { browser.rescheduleSnoozedTab(tabId) }
        func openSnoozedTabNow(tabId: String) { browser.openSnoozedTabNow(tabId) }
        func deleteSnoozedTab(tabId: String) { browser.deleteSnoozedTab(tabId) }

        func changeDismissResistancePercent(value: Int32) {
            browser.updateDismissResistancePercent(value)
        }

        func changeAppearanceSettings(settings: AppearanceSettings) {
            browser.updateAppearanceSettings(settings)
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

        func changeBrowserMenuLocation(
            entry: BrowserMenuEntry,
            location: BrowserMenuLocation
        ) {
            browser.changeBrowserMenuLocation(entry: entry, location: location)
        }

        func saveTopping(id: String?, source: String) {
            browser.saveTopping(id: id, source: source)
        }

        func changeBrowserMenuLayout(layout: BrowserMenuLayout) {
            browser.changeBrowserMenuLayout(layout: layout)
        }

        func dismissBrowserMenu() {
            browser.dismissBrowserMenu()
        }

        func toppingSource(id: String) -> String? {
            browser.toppingSource(id: id)
        }

        func setToppingEnabled(id: String, enabled: Bool) {
            browser.setToppingEnabled(id: id, enabled: enabled)
        }

        func setToppingFrameScope(id: String, scope: ToppingFrameScope) {
            browser.setToppingFrameScope(id: id, scope: scope)
        }

        func deleteTopping(id: String) {
            browser.deleteTopping(id: id)
        }

        func dismissLibrary() {
            browser.dismissLibrary()
        }

        func openLibraryEntry(entry: BrowserLibraryEntry) {
            browser.openLibraryEntry(entry)
        }

        func removeLibraryEntry(entry: BrowserLibraryEntry) {
            browser.removeLibraryEntry(entry)
        }

        func restoreLibraryEntry(entry: BrowserLibraryEntry) {
            browser.restoreLibraryEntry(entry)
        }

        func clearLibraryHistory() {
            browser.clearLibraryHistory()
        }

        func createReaderLibrary() -> ReaderLibraryDataSource {
            browser.createReaderLibrary()
        }

        func createReaderSpeech() -> ReaderSpeech {
            browser.createReaderSpeech()
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
        let script = (parsed as? ToppingParseResultAccepted)?.script
        return BrowserViewportTopping(
            id: id,
            name: script?.name ?? id,
            enabled: enabled,
            declaredFrameScope: script?.declaredFrameScope ?? .top,
            allowedFrameScope: allowedFrameScope.sharedScope
        )
    }
}

private extension StoredToppingFrameScope {
    var sharedScope: ToppingFrameScope {
        switch self {
        case .top: .top
        case .sameOrigin: .sameorigin
        case .allMatching: .allmatching
        }
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
