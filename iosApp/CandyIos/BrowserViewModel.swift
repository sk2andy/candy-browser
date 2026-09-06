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
    let profileId: String
}

enum BrowserCandyTrailAction {
    case open(tabId: String)
    case close
    case selectNode(tabId: String, nodeId: String)
    case forkNode(tabId: String, nodeId: String)
    case activateFork(tabId: String, forkId: String)
}

enum BrowserCandyTrailActionResult {
    case opened(CandyTrail)
    case closed
    case selected
    case forked(destinationTabId: String)
    case activated(destinationTabId: String)
    case rejected
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
    @Published private(set) var profiles: [BrowserProfile] = []
    @Published private(set) var activeProfileId = ""
    @Published private(set) var tabOverviewMode: BrowserTabOverviewMode
    @Published private(set) var searchEngine: SearchEngine
    @Published private(set) var searxngInstanceUrl: String
    @Published private(set) var translationProvider: PageTranslationProvider
    @Published private(set) var menuItems: [BrowserFeatureMenuItem] = []
    @Published private(set) var isTabOverviewVisible = false
    @Published private(set) var isSettingsVisible = false
    @Published private(set) var addressFocusRequest: Int64 = 0
    @Published var findQuery = ""
    @Published private(set) var findResultText = ""
    @Published private(set) var isFindVisible = false
    @Published private(set) var readerSnapshot: BrowserReaderSnapshot?
    @Published private(set) var sharePayload: BrowserSharePayload?
    @Published private(set) var candyTrailTabId: String? = nil
    @Published private(set) var candyTrailRevision: Int64 = 0
    @Published private(set) var toppings: [StoredTopping] = []
    @Published private(set) var syncState: SyncRepositoryState
    let profileIconEmojis: [String]
    let syncIconCatalog: SyncDeviceIconCatalog

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
    private let syncRepository: CandySyncRepository
    private var sessions: [String: WKWebViewBrowserSessionAdapter]
    private var syncObservation: KotlinAutoCloseable?
    private var favoriteAddresses: Set<String> = []
    private var pinnedTabIds: Set<String> = []
    private var candyTrails: [String: CandyTrail] = [:]
    private var candyTrailHistoryBindings: [String: CandyTrailHistoryBinding] = [:]
    private var pendingCandyTrailTargets: [String: String] = [:]
    private var pageExtractionRequestId: UUID?
    private var findRequestId: UUID?
    private var findTabId: String?
    private var findSessionId: UUID?

    private static let candyTrailProfileId = "ios-default"
    private static let localProfilesPreferenceKey = "ios.browser.local-profiles.v1"
    private static let activeProfilePreferenceKey = "ios.browser.active-profile.v1"

    override init() {
        let shared = CandySharedFacade()
        let chrome = BrowserChromeController()
        let sessionController = BrowserSessionController()
        let snapshotStore = BrowserTabSnapshotStore()
        let preferences = UserDefaults.standard
        let profileIconEmojis = Self.loadProfileIconEmojis()
        let syncIconCatalog = Self.loadSyncIconCatalog()
        let syncRepository = CandySyncRepository(
            settingsStore: IosSyncSettingsStore(preferences: preferences),
            vaultStore: IosSyncVaultStore(),
            cacheStore: IosSyncCacheStore(),
            iconCatalog: syncIconCatalog,
            transportFactory: { endpoint in IosSyncTransport(endpoint: endpoint) },
            crypto: IosSyncCryptoProvider(),
            recoveryKeyDeriver: IosSyncRecoveryKeyDeriver(),
            dispatch: IosSyncDispatch()
        )
        Self.restoreLocalProfiles(
            in: sessionController,
            from: preferences,
            allowedEmojis: profileIconEmojis
        )
        let initialSearchSettings = sessionController.updateSearchSettings(
            searchEngine: SearchEngine.companion.fromStableId(
                stableId: BrowserSearchSettingsPreference.loadEngineStableId(
                    from: preferences
                )
            ),
            searxngInstanceUrl: BrowserSearchSettingsPreference.loadSearxngInstanceUrl(
                from: preferences
            )
        )
        let initialState = sessionController.state
        guard let initialTab = initialState.tabs.first(
            where: { $0.id == initialState.selectedTabId }
        ) else {
            preconditionFailure("Shared tab state must contain its selected tab")
        }
        let initialSession = WKWebViewBrowserSessionAdapter(
            tabId: initialTab.id,
            shared: shared,
            profileId: initialTab.profileId,
            profileIsolationEnabled: initialState.profiles
                .first(where: { $0.id == initialTab.profileId })?
                .isolationEnabled ?? false
        )

        self.shared = shared
        self.chrome = chrome
        self.sessionController = sessionController
        self.snapshotStore = snapshotStore
        self.preferences = preferences
        self.profileIconEmojis = profileIconEmojis
        self.syncIconCatalog = syncIconCatalog
        self.syncRepository = syncRepository
        syncState = syncRepository.currentState()
        sessions = [initialTab.id: initialSession]
        profiles = initialState.profiles
        activeProfileId = initialState.activeProfileId
        address = initialTab.address
        selectedTabId = initialTab.id
        tabCountLabel = initialState.countLabel
        tabOverviewMode = BrowserTabOverviewModePreference.load(from: preferences)
        searchEngine = initialSearchSettings.searchEngine
        searxngInstanceUrl = initialSearchSettings.searxngInstanceUrl
        translationProvider = PageTranslationProvider.companion.fromStableId(
            stableId: BrowserTranslationProviderPreference.loadStableId(from: preferences)
        )

        super.init()

        ToppingRuntime.shared.delegate = self
        publishToppings()
        initialSession.eventSink = self
        snapshotStore.attach(
            tabId: initialTab.id,
            sessionId: initialSession.sessionId
        )
        precondition(sessionController.attach(session: initialSession))
        synchronizeTabs()
        syncObservation = syncRepository.observe { [weak self] state in
            DispatchQueue.main.async {
                self?.applySyncRepositoryState(state)
            }
        }
        syncRepository.startRealtime()
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
        let closingSyncTab = intent == .closetab
            ? sessionController.state.tabs.first(where: { $0.id == closingTabId })
            : nil
        if intent == .closetab && pinnedTabIds.contains(closingTabId) {
            errorMessage = "Angehefteten Tab zuerst lösen."
            return
        }
        if isFindVisible && intent != .hideoverview {
            dismissFind()
        }
        pageExtractionRequestId = nil
        if intent == .showoverview {
            let sourceTabId = selectedTabId
            captureSnapshot(tabId: sourceTabId) { [weak self] _ in
                guard
                    let self,
                    self.selectedTabId == sourceTabId,
                    !self.isTabOverviewVisible
                else {
                    return
                }
                self.sessionController.dispatchTabs(intent: intent, tabId: tabId)
                self.synchronizeTabs()
            }
            return
        }
        if intent == .newtab ||
            ((intent == .selecttab || intent == .selecttabinoverview) &&
                tabId != selectedTabId) {
            captureSnapshot(tabId: selectedTabId)
        }
        sessionController.dispatchTabs(intent: intent, tabId: tabId)
        synchronizeTabs()
        if let closingSyncTab {
            enqueueSyncedTabClose(closingSyncTab)
        }
    }

    func selectProfile(_ profileId: String) {
        guard profileId != activeProfileId else {
            return
        }
        captureSnapshot(tabId: selectedTabId)
        sessionController.selectProfile(profileId: profileId)
        synchronizeTabs()
        persistLocalProfiles()
    }

    func createProfile(emoji: String, isolationEnabled: Bool) {
        let previousProfileCount = sessionController.state.profiles.count
        sessionController.createProfile(
            profileId: UUID().uuidString.lowercased(),
            emoji: emoji,
            isolationEnabled: isolationEnabled,
            isolationSupported: true
        )
        synchronizeTabs()
        persistLocalProfiles()
        if sessionController.state.profiles.count == previousProfileCount {
            errorMessage = "Das Profil konnte nicht erstellt werden."
        }
    }

    func updateProfileEmoji(profileId: String, emoji: String) {
        sessionController.updateProfileEmoji(profileId: profileId, emoji: emoji)
        synchronizeTabs()
        persistLocalProfiles()
    }

    func setProfileIsolation(profileId: String, enabled: Bool) {
        guard let currentProfile = sessionController.state.profiles.first(
            where: { $0.id == profileId }
        ), currentProfile.isolationEnabled != enabled else {
            return
        }
        sessionController.updateProfileIsolation(
            profileId: profileId,
            enabled: enabled,
            isolationSupported: true
        )
        guard sessionController.state.profiles.first(
            where: { $0.id == profileId }
        )?.isolationEnabled == enabled else {
            return
        }
        let affectedTabIds = sessionController.state.tabs
            .filter { $0.profileId == profileId }
            .map(\.id)
        affectedTabIds.forEach { tabId in
            _ = sessionController.detachSession(tabId: tabId)
            sessions.removeValue(forKey: tabId)?.eventSink = nil
            snapshotStore.invalidate(tabId: tabId)
        }
        synchronizeTabs()
        persistLocalProfiles()
    }

    func showSettings() {
        cancelFeaturePresentations()
        isSettingsVisible = true
    }

    func dismissSettings() {
        isSettingsVisible = false
    }

    var syncSettingsUiState: SyncSettingsUiState {
        SyncSettingsUiState(
            settings: syncState.settings,
            status: syncState.status,
            profiles: syncState.profiles,
            pendingCount: syncState.pendingCount,
            lastSuccessAt: syncState.lastSuccessAt,
            icons: syncIconCatalog.icons,
            localProfiles: profiles.filter { $0.syncedDeviceId == nil }.map { profile in
                SyncLocalProfileOption(
                    id: profile.id,
                    emoji: profile.emoji,
                    isCurrent: profile.id == activeProfileId
                )
            },
            isBusy: syncState.status == .enrolling || syncState.status == .syncing
        )
    }

    func configureSync(_ settings: SyncConnectionSettings) -> Bool {
        syncRepository.configure(settings: settings)
    }

    func enrollSync(
        serverPassword: KotlinCharArray,
        passphrase: KotlinCharArray,
        completion: @escaping (SyncEnrollmentOutcome) -> Void
    ) {
        syncRepository.enroll(
            serverPassword: serverPassword,
            passphrase: passphrase
        ) { outcome in
            DispatchQueue.main.async {
                completion(outcome)
                if outcome is SyncEnrollmentOutcomeEnrolled {
                    self.syncRepository.refresh { _ in }
                }
            }
        }
    }

    func refreshSync() {
        syncRepository.refresh { _ in }
    }

    @discardableResult
    func performCandyTrail(_ action: BrowserCandyTrailAction) -> BrowserCandyTrailActionResult {
        switch action {
        case let .open(tabId):
            guard let trail = openCandyTrail(tabId: tabId) else {
                return .rejected
            }
            return .opened(trail)
        case .close:
            closeCandyTrail()
            return .closed
        case let .selectNode(tabId, nodeId):
            return selectCandyTrailNode(tabId: tabId, nodeId: nodeId) ? .selected : .rejected
        case let .forkNode(tabId, nodeId):
            guard let destinationTabId = forkCandyTrailNode(tabId: tabId, nodeId: nodeId) else {
                return .rejected
            }
            return .forked(destinationTabId: destinationTabId)
        case let .activateFork(tabId, forkId):
            guard let destinationTabId = activateCandyTrailFork(tabId: tabId, forkId: forkId) else {
                return .rejected
            }
            return .activated(destinationTabId: destinationTabId)
        }
    }

    func candyTrail(tabId: String) -> CandyTrail? {
        guard sessions[tabId]?.allowsCandyTrailRecording == true else {
            return nil
        }
        return candyTrails[tabId]
    }

    @discardableResult
    func openCandyTrail(tabId: String) -> CandyTrail? {
        guard
            let trail = candyTrail(tabId: tabId),
            !trail.nodes.isEmpty
        else {
            return nil
        }
        candyTrailTabId = tabId
        return trail
    }

    func closeCandyTrail() {
        candyTrailTabId = nil
    }

    @discardableResult
    func selectCandyTrailNode(tabId: String, nodeId: String) -> Bool {
        guard
            let session = eligibleCandyTrailSession(tabId: tabId),
            let trail = candyTrails[tabId],
            let node = trail.nodes.first(where: { $0.id == nodeId }),
            let selectedTrail = CandyTrailRules.shared.selectNode(
                trail: trail,
                nodeId: nodeId,
                visitedAt: currentTimeMillis
            )
        else {
            return false
        }

        setCandyTrail(selectedTrail, tabId: tabId)
        pendingCandyTrailTargets[tabId] = nodeId
        performTabs(.selecttab, tabId: tabId)
        let binding = candyTrailHistoryBindings[tabId] ?? emptyCandyTrailHistoryBinding
        if let historyIndex = CandyTrailHistoryReconciler.shared
            .indexOfNode(binding: binding, nodeId: nodeId)?.int32Value {
            switch session.navigateToHistoryIndex(historyIndex) {
            case .started:
                return true
            case .alreadyCurrent where session.webView.url?.absoluteString == node.url:
                pendingCandyTrailTargets.removeValue(forKey: tabId)
                return true
            case .alreadyCurrent, .rejected:
                break
            }
        }
        session.execute(command: BrowserEngineCommands.shared.load(address: node.url))
        return true
    }

    @discardableResult
    func forkCandyTrailNode(tabId: String, nodeId: String) -> String? {
        guard
            eligibleCandyTrailSession(tabId: tabId) != nil,
            let trail = candyTrails[tabId],
            let node = trail.nodes.first(where: { $0.id == nodeId })
        else {
            return nil
        }
        let originTabId = tabId
        performTabs(.newtab)
        let destinationTabId = selectedTabId
        guard
            destinationTabId != originTabId,
            let destinationSession = eligibleCandyTrailSession(tabId: destinationTabId),
            let forkedTrail = CandyTrailForkRules.shared.create(
                trail: trail,
                originTab: candyTrailForkTab(id: originTabId),
                originNodeId: nodeId,
                destinationTab: candyTrailForkTab(id: destinationTabId),
                createdAt: currentTimeMillis,
                maxForks: CandyTrailForkRules.shared.MAX_FORKS
            )
        else {
            performTabs(.closetab, tabId: destinationTabId)
            performTabs(.selecttab, tabId: originTabId)
            return nil
        }
        setCandyTrail(forkedTrail, tabId: originTabId)
        destinationSession.execute(command: BrowserEngineCommands.shared.load(address: node.url))
        return destinationTabId
    }

    @discardableResult
    func activateCandyTrailFork(tabId: String, forkId: String) -> String? {
        guard
            eligibleCandyTrailSession(tabId: tabId) != nil,
            let trail = candyTrails[tabId],
            let fork = trail.forks.first(where: { $0.id == forkId })
        else {
            return nil
        }
        if
            let destinationTabId = fork.destinationTabId,
            eligibleCandyTrailSession(tabId: destinationTabId) != nil
        {
            performTabs(.selecttab, tabId: destinationTabId)
            return destinationTabId
        }

        let originTabId = tabId
        performTabs(.newtab)
        let destinationTabId = selectedTabId
        guard
            destinationTabId != originTabId,
            let destinationSession = eligibleCandyTrailSession(tabId: destinationTabId),
            let reopenedTrail = CandyTrailForkRules.shared.reopen(
                trail: trail,
                forkId: forkId,
                originTab: candyTrailForkTab(id: originTabId),
                destinationTab: candyTrailForkTab(id: destinationTabId),
                reopenedAt: currentTimeMillis
            )
        else {
            performTabs(.closetab, tabId: destinationTabId)
            performTabs(.selecttab, tabId: originTabId)
            return nil
        }
        setCandyTrail(reopenedTrail, tabId: originTabId)
        destinationSession.execute(command: BrowserEngineCommands.shared.load(address: fork.url))
        return destinationTabId
    }

    func performMenu(_ item: BrowserFeatureMenuItem) {
        if item.action == .invoketoppingcommand {
            guard let scriptId = item.toppingScriptId, let commandId = item.toppingCommandId else {
                errorMessage = "Dieser Topping-Befehl ist nicht mehr verfügbar."
                return
            }
            ToppingRuntime.shared.invoke(
                tabId: selectedTabId,
                scriptId: scriptId,
                commandId: commandId
            )
            return
        }
        performMenuAction(item.action)
    }

    func performTabAction(tabId: String, action: BrowserFeatureMenuAction) {
        guard sessionController.state.tabs.contains(where: { $0.id == tabId }) else {
            return
        }
        if action == .togglefavorite {
            toggleFavorite(tabId: tabId)
        } else if action == .togglepinned {
            togglePinned(tabId: tabId)
        } else if action == .share {
            cancelFeaturePresentations()
            sharePage(tabId: tabId)
        } else if action == .openexternal {
            cancelFeaturePresentations()
            openPageExternally(tabId: tabId)
        } else if action == .print {
            cancelFeaturePresentations()
            printPage(tabId: tabId)
        } else if action == .opencandytrail {
            cancelFeaturePresentations()
            if openCandyTrail(tabId: tabId) == nil {
                errorMessage = "Für diesen Tab ist noch kein Candy Trail verfügbar."
            }
        }
    }

    private func performMenuAction(_ action: BrowserFeatureMenuAction) {
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
            presentReader()
        } else if action == .opencandytrail {
            cancelFeaturePresentations()
            if openCandyTrail(tabId: selectedTabId) == nil {
                errorMessage = "Für diesen Tab ist noch kein Candy Trail verfügbar."
            }
        } else if action == .translatepage {
            translateSelectedPage()
        } else if action == .opensettings {
            showSettings()
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
        pageExtractionRequestId = nil
        readerSnapshot = nil
    }

    func retryReader() {
        guard readerSnapshot != nil else {
            return
        }
        presentReader()
    }

    func openReaderOriginal(_ url: String) {
        dismissReader()
        navigateReaderTarget(url)
    }

    func openReaderLink(_ url: String) {
        dismissReader()
        navigateReaderTarget(url)
    }

    func dismissShare() {
        sharePayload = nil
    }

    func updateTabOverviewMode(_ mode: BrowserTabOverviewMode) {
        tabOverviewMode = mode
        BrowserTabOverviewModePreference.save(mode, to: preferences)
    }

    func updateSearchEngine(_ engine: SearchEngine) {
        publishSearchSettings(
            sessionController.updateSearchSettings(
                searchEngine: engine,
                searxngInstanceUrl: searxngInstanceUrl
            )
        )
    }

    func updateSearxngInstanceUrl(_ value: String) {
        publishSearchSettings(
            sessionController.updateSearchSettings(
                searchEngine: searchEngine,
                searxngInstanceUrl: value
            )
        )
    }

    func updateTranslationProvider(_ provider: PageTranslationProvider) {
        translationProvider = provider
        BrowserTranslationProviderPreference.save(
            stableId: provider.stableId,
            to: preferences
        )
    }

    private func publishSearchSettings(_ settings: SearchSettings) {
        searchEngine = settings.searchEngine
        searxngInstanceUrl = settings.searxngInstanceUrl
        BrowserSearchSettingsPreference.save(
            engineStableId: settings.searchEngine.stableId,
            searxngInstanceUrl: settings.searxngInstanceUrl,
            to: preferences
        )
    }

    func saveTopping(id: String?, source: String) {
        let toppingId = id ?? "ios-\(UUID().uuidString.lowercased())"
        let enabled = id.flatMap { existingId in
            toppings.first(where: { $0.id == existingId })?.enabled
        } ?? true
        Task { @MainActor [weak self] in
            do {
                _ = try await ToppingRuntime.shared.resolveAndSave(
                    id: toppingId,
                    source: source,
                    enabled: enabled
                )
                self?.errorMessage = nil
                self?.publishToppings()
            } catch {
                self?.errorMessage = error.localizedDescription
            }
        }
    }

    func toppingSource(id: String) -> String? {
        toppings.first(where: { $0.id == id })?.source
    }

    func setToppingEnabled(id: String, enabled: Bool) {
        do {
            try ToppingRuntime.shared.setEnabled(enabled, id: id)
            errorMessage = nil
            publishToppings()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deleteTopping(id: String) {
        do {
            try ToppingRuntime.shared.delete(id: id)
            errorMessage = nil
            publishToppings()
        } catch {
            errorMessage = error.localizedDescription
        }
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
        let closedTabIds = Set(sessions.keys).subtracting(activeIds)

        for tab in state.tabs where sessions[tab.id] == nil {
            guard let profile = state.profiles.first(where: { $0.id == tab.profileId }) else {
                preconditionFailure("Every tab must reference an existing profile")
            }
            let session = WKWebViewBrowserSessionAdapter(
                tabId: tab.id,
                shared: shared,
                profileId: profile.id,
                profileIsolationEnabled: profile.isolationEnabled
            )
            session.eventSink = self
            sessions[tab.id] = session
            snapshotStore.attach(tabId: tab.id, sessionId: session.sessionId)
            precondition(sessionController.attach(session: session))
        }
        sessions = sessions.filter { activeIds.contains($0.key) }
        closeCandyTrailForkDestinations(tabIds: closedTabIds)
        candyTrails = candyTrails.filter { activeIds.contains($0.key) }
        candyTrailHistoryBindings = candyTrailHistoryBindings.filter { activeIds.contains($0.key) }
        pendingCandyTrailTargets = pendingCandyTrailTargets.filter { activeIds.contains($0.key) }
        if let candyTrailTabId, !activeIds.contains(candyTrailTabId) {
            closeCandyTrail()
        }
        pinnedTabIds.formIntersection(activeIds)
        snapshotStore.removeTabs(notIn: activeIds)

        selectedTabId = state.selectedTabId
        profiles = state.profiles
        activeProfileId = state.activeProfileId
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

    private func publishToppings() {
        toppings = ToppingRuntime.shared.records()
    }

    private func eligibleCandyTrailSession(tabId: String) -> WKWebViewBrowserSessionAdapter? {
        guard
            sessionController.state.tabs.contains(where: { $0.id == tabId }),
            let session = sessions[tabId],
            session.allowsCandyTrailRecording
        else {
            return nil
        }
        return session
    }

    private var emptyCandyTrailHistoryBinding: CandyTrailHistoryBinding {
        CandyTrailHistoryBinding(entries: [], currentIndex: -1)
    }

    private var currentTimeMillis: Int64 {
        Int64(Date().timeIntervalSince1970 * 1_000)
    }

    private func candyTrailForkTab(id: String) -> CandyTrailForkTab {
        CandyTrailForkTab(
            id: id,
            profileId: Self.candyTrailProfileId,
            isIncognito: false
        )
    }

    private func closeCandyTrailForkDestinations(tabIds: Set<String>) {
        guard !tabIds.isEmpty else {
            return
        }
        let closedAt = currentTimeMillis
        var updatedTrails = candyTrails
        for (originTabId, trail) in candyTrails {
            guard sessions[originTabId]?.allowsCandyTrailRecording == true else {
                continue
            }
            updatedTrails[originTabId] = tabIds.reduce(trail) { current, destinationTabId in
                CandyTrailForkRules.shared.closeDestination(
                    trail: current,
                    destinationTabId: destinationTabId,
                    closedAt: closedAt
                )
            }
        }
        candyTrails = updatedTrails
        candyTrailRevision &+= 1
    }

    private func setCandyTrail(_ trail: CandyTrail, tabId: String) {
        candyTrails[tabId] = trail
        candyTrailRevision &+= 1
    }

    private func publishTabCards(state: BrowserTabsState) {
        tabCards = state.tabs.map { tab in
            BrowserTabCard(
                id: tab.id,
                title: tab.title,
                address: tab.address,
                isSelected: tab.id == state.selectedTabId,
                isFavorite: favoriteAddresses.contains(tab.address),
                isPinned: tab.isPinned || pinnedTabIds.contains(tab.id),
                preview: snapshotStore.image(for: tab.id),
                profileId: tab.profileId
            )
        }
        tabCountLabel = state.countLabel
    }

    private func persistLocalProfiles() {
        let localProfiles = profiles
            .filter { $0.syncedDeviceId == nil }
            .map { profile -> [String: Any] in
                [
                    "id": profile.id,
                    "emoji": profile.emoji,
                    "isolationEnabled": profile.isolationEnabled
                ]
            }
        preferences.set(localProfiles, forKey: Self.localProfilesPreferenceKey)
        let persistentActiveProfileId = if localProfiles.contains(
            where: { $0["id"] as? String == activeProfileId }
        ) {
            activeProfileId
        } else {
            localProfiles.first?["id"] as? String
        }
        preferences.set(
            persistentActiveProfileId,
            forKey: Self.activeProfilePreferenceKey
        )
    }

    private static func restoreLocalProfiles(
        in controller: BrowserSessionController,
        from preferences: UserDefaults,
        allowedEmojis: [String]
    ) {
        let fallbackEmoji = allowedEmojis.first ?? "🍬"
        let storedProfiles = preferences.array(forKey: localProfilesPreferenceKey)
            as? [[String: Any]] ?? []
        storedProfiles.forEach { profile in
            guard
                let id = profile["id"] as? String,
                let emoji = profile["emoji"] as? String,
                id != "candy"
            else {
                return
            }
            controller.createProfile(
                profileId: id,
                emoji: allowedEmojis.contains(emoji) ? emoji : fallbackEmoji,
                isolationEnabled: profile["isolationEnabled"] as? Bool ?? false,
                isolationSupported: true
            )
        }
        if let activeProfileId = preferences.string(forKey: activeProfilePreferenceKey) {
            controller.selectProfile(profileId: activeProfileId)
        }
    }

    private static func loadProfileIconEmojis() -> [String] {
        struct Catalog: Decodable {
            struct Icon: Decodable {
                let emoji: String
            }

            let icons: [Icon]
        }

        guard
            let url = Bundle.main.url(
                forResource: "device-icons-v1",
                withExtension: "json"
            ),
            let data = try? Data(contentsOf: url),
            let catalog = try? JSONDecoder().decode(Catalog.self, from: data),
            !catalog.icons.isEmpty
        else {
            assertionFailure("Candy profile icon catalog is missing")
            return ["🍬", "⭐", "💼", "🏠", "✈️", "📚"]
        }
        return catalog.icons.map(\.emoji)
    }

    private static func loadSyncIconCatalog() -> SyncDeviceIconCatalog {
        guard
            let url = Bundle.main.url(forResource: "device-icons-v1", withExtension: "json"),
            let raw = try? String(contentsOf: url, encoding: .utf8),
            let catalog = try? SyncDeviceIconCatalog.companion.decode(raw: raw)
        else {
            return SyncDeviceIconCatalog(icons: [
                SyncDeviceIconDefinition(id: "candy", emoji: "🍬", label: "Candy"),
                SyncDeviceIconDefinition(id: "phone", emoji: "📱", label: "Phone"),
                SyncDeviceIconDefinition(id: "browser", emoji: "🌐", label: "Browser"),
            ])
        }
        return catalog
    }

    private func applySyncRepositoryState(_ state: SyncRepositoryState) {
        syncState = state
        sessionController.reconcileSyncProfiles(
            syncedProfiles: state.profiles,
            currentDeviceId: state.currentDeviceId,
            localProfileId: state.settings?.localProfileId,
            icons: syncIconCatalog.icons
        )
        publishUntrackedLinkedTabs()
        synchronizeTabs()
    }

    private func publishUntrackedLinkedTabs() {
        sessionController.state.tabs.filter { tab in
            tab.syncCandyId == nil && !tab.isPrivate &&
                syncTargetDeviceId(profileId: tab.profileId) != nil
        }.forEach { tab in
            sessionController.assignSyncCandyId(
                tabId: tab.id,
                candyId: UUID().uuidString.lowercased()
            )
            enqueueSyncedTab(tabId: tab.id)
        }
    }

    private func syncTargetDeviceId(profileId: String) -> String? {
        let profile = sessionController.state.profiles.first { $0.id == profileId }
        return profile?.syncedDeviceId ?? profile?.linkedSyncDeviceId
    }

    private func enqueueSyncedTab(tabId: String) {
        guard
            let tab = sessionController.state.tabs.first(where: { $0.id == tabId }),
            let targetDeviceId = syncTargetDeviceId(profileId: tab.profileId),
            let candyId = tab.syncCandyId
        else {
            return
        }
        let profileTabs = sessionController.state.tabs.filter {
            $0.profileId == tab.profileId && !$0.isPrivate
        }
        guard let index = profileTabs.firstIndex(where: { $0.id == tab.id }) else { return }
        let candidate = SyncTab(
            candyId: candyId,
            windowId: 0,
            index: Int32(index),
            groupId: nil,
            active: tab.id == selectedTabId,
            pinned: tab.isPinned || pinnedTabIds.contains(tab.id),
            title: tab.title,
            url: tab.address
        )
        guard let outbound = SyncTabRules.shared.outboundTab(tab: candidate, isPrivate: tab.isPrivate) else {
            return
        }
        let existing = syncState.profiles
            .first(where: { $0.deviceId == targetDeviceId })?
            .tabs.first(where: { $0.candyId == candyId })
        let mutation: SyncPendingMutation
        if existing == nil {
            mutation = SyncPendingMutationOpen(
                mutationId: UUID().uuidString.lowercased(),
                targetDeviceId: targetDeviceId,
                tab: outbound,
                isPrivate: false
            )
        } else if existing?.url != outbound.url || existing?.title != outbound.title {
            mutation = SyncPendingMutationNavigate(
                mutationId: UUID().uuidString.lowercased(),
                targetDeviceId: targetDeviceId,
                candyId: candyId,
                title: outbound.title,
                url: outbound.url
            )
        } else {
            return
        }
        syncRepository.mutate(mutation: mutation) { _ in }
    }

    private func enqueueSyncedTabClose(_ tab: BrowserTabState) {
        guard
            let candyId = tab.syncCandyId,
            let targetDeviceId = syncTargetDeviceId(profileId: tab.profileId)
        else {
            return
        }
        syncRepository.mutate(
            mutation: SyncPendingMutationClose(
                mutationId: UUID().uuidString.lowercased(),
                targetDeviceId: targetDeviceId,
                candyId: candyId
            )
        ) { _ in }
    }

    private func enqueueSyncedTabPinned(tabId: String, pinned: Bool) {
        guard
            let tab = sessionController.state.tabs.first(where: { $0.id == tabId }),
            let candyId = tab.syncCandyId,
            let targetDeviceId = syncTargetDeviceId(profileId: tab.profileId)
        else {
            return
        }
        syncRepository.mutate(
            mutation: SyncPendingMutationSetPinned(
                mutationId: UUID().uuidString.lowercased(),
                targetDeviceId: targetDeviceId,
                candyId: candyId,
                pinned: pinned
            )
        ) { _ in }
    }

    private func captureSnapshot(
        tabId: String,
        completion: @escaping @MainActor (Bool) -> Void = { _ in }
    ) {
        let state = sessionController.state
        guard
            let tab = state.tabs.first(where: { $0.id == tabId }),
            !tab.address.isEmpty,
            let session = sessions[tabId]
        else {
            completion(false)
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
        } onCompletion: { accepted in
            completion(accepted)
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
            toppingCommands: ToppingRuntime.shared.commands(tabId: tab.id).map { command in
                BrowserToppingMenuCommand(
                    scriptId: command.scriptId,
                    commandId: command.commandId,
                    caption: command.caption,
                    scriptName: command.scriptName
                )
            }
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
            action == .opencandytrail ||
            action == .translatepage ||
            action == .findinpage ||
            action == .share ||
            action == .openexternal ||
            action == .print ||
            action == .opensettings ||
            action == .invoketoppingcommand
    }

    private func toggleFavorite() {
        toggleFavorite(tabId: selectedTabId)
    }

    private func toggleFavorite(tabId: String) {
        let value = sessionController.state.tabs
            .first(where: { $0.id == tabId })?
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
        togglePinned(tabId: selectedTabId)
    }

    private func togglePinned(tabId: String) {
        let pinned: Bool
        if pinnedTabIds.contains(tabId) {
            pinnedTabIds.remove(tabId)
            pinned = false
        } else {
            pinnedTabIds.insert(tabId)
            pinned = true
        }
        synchronizeTabs()
        enqueueSyncedTabPinned(tabId: tabId, pinned: pinned)
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
        readerSnapshot = nil
        sharePayload = nil
    }

    private func presentReader() {
        cancelFeaturePresentations()
        let sourceUrl = sessionController.state.tabs
            .first(where: { $0.id == selectedTabId })?
            .address ?? ""
        readerSnapshot = BrowserReaderSnapshot(
            result: nil,
            sourceUrl: sourceUrl,
            isPrivate: false
        )
        extractCurrentPage { [weak self] document in
            guard let self, self.readerSnapshot != nil else {
                return
            }
            self.errorMessage = nil
            if let document {
                self.readerSnapshot = document.sharedReaderSnapshot(isPrivate: false)
            } else {
                self.readerSnapshot = BrowserReaderSnapshot(
                    result: ReaderExtractionResultFailure(reason: .emptyarticle),
                    sourceUrl: sourceUrl,
                    isPrivate: false
                )
            }
        }
    }

    private func navigateReaderTarget(_ url: String) {
        let resolution = sessionController.navigateSelected(input: url)
        guard let value = resolution.url?.value else {
            errorMessage = "Dieser Reader-Link kann nicht geöffnet werden."
            return
        }
        address = value
        errorMessage = nil
        synchronizeTabs()
    }

    private func translateSelectedPage() {
        let sourceUrl = sessionController.state.tabs
            .first(where: { $0.id == selectedTabId })?
            .address
        let targetLanguage = Locale.current.language.languageCode?.identifier ?? "en"
        guard let translationUrl = shared.pageTranslationUrl(
            provider: translationProvider,
            sourceUrl: sourceUrl,
            targetLanguage: targetLanguage
        ) else {
            errorMessage = "Diese Seite kann nicht übersetzt werden."
            return
        }

        cancelFeaturePresentations()
        let resolution = sessionController.navigateSelected(input: translationUrl)
        guard let value = resolution.url?.value else {
            errorMessage = "Der Übersetzungsdienst kann nicht geöffnet werden."
            return
        }
        address = value
        errorMessage = nil
        synchronizeTabs()
    }

    private func shareCurrentPage() {
        sharePage(tabId: selectedTabId)
    }

    private func sharePage(tabId: String) {
        guard let url = sessions[tabId]?.webView.url else {
            errorMessage = "Diese Seite kann noch nicht geteilt werden."
            return
        }
        errorMessage = nil
        let title = sessionController.state.tabs
            .first(where: { $0.id == tabId })?
            .title ?? "Candy"
        sharePayload = BrowserSharePayload(title: title, url: url)
    }

    private func openCurrentPageExternally() {
        openPageExternally(tabId: selectedTabId)
    }

    private func openPageExternally(tabId: String) {
        guard let url = sessions[tabId]?.webView.url else {
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
        printPage(tabId: selectedTabId)
    }

    private func printPage(tabId: String) {
        guard let webView = sessions[tabId]?.webView else {
            errorMessage = "Diese Seite kann noch nicht gedruckt werden."
            return
        }
        guard UIPrintInteractionController.isPrintingAvailable else {
            errorMessage = "Drucken ist auf diesem Gerät nicht verfügbar."
            return
        }
        let controller = UIPrintInteractionController.shared
        controller.printInfo = UIPrintInfo(dictionary: nil)
        controller.printInfo?.jobName = sessionController.state.tabs
            .first(where: { $0.id == tabId })?
            .title ?? "Candy"
        controller.printFormatter = webView.viewPrintFormatter()
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
            let tabId = event.tabId
            if sessionController.state.tabs.first(where: { $0.id == tabId })?.syncCandyId == nil,
               syncTargetDeviceId(
                   profileId: sessionController.state.tabs.first(where: { $0.id == tabId })?.profileId ?? ""
               ) != nil {
                sessionController.assignSyncCandyId(
                    tabId: tabId,
                    candyId: UUID().uuidString.lowercased()
                )
            }
            enqueueSyncedTab(tabId: tabId)
            let sessionId = sessions[tabId]?.sessionId
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 150_000_000)
                guard
                    let self,
                    self.sessions[tabId]?.sessionId == sessionId
                else {
                    return
                }
                self.captureSnapshot(tabId: tabId)
            }
        }
    }

    func browserEngineDidUpdateHistory(
        tabId: String,
        sessionId: UUID,
        snapshot: CandyTrailHistorySnapshot,
        title: String
    ) {
        guard
            let session = sessions[tabId],
            session.sessionId == sessionId
        else {
            return
        }
        guard session.allowsCandyTrailRecording else {
            candyTrails.removeValue(forKey: tabId)
            candyTrailHistoryBindings.removeValue(forKey: tabId)
            pendingCandyTrailTargets.removeValue(forKey: tabId)
            return
        }
        let currentIndex = Int(snapshot.currentIndex)
        guard currentIndex >= 0, currentIndex < snapshot.urls.count else {
            return
        }

        let currentUrl = snapshot.urls[currentIndex]
        let trail = candyTrails[tabId]
        let pendingTargetNodeId = pendingCandyTrailTargets[tabId].flatMap { targetNodeId in
            trail?.nodes.contains(where: { node in
                node.id == targetNodeId && node.url == currentUrl
            }) == true ? targetNodeId : nil
        }
        if pendingTargetNodeId != nil {
            pendingCandyTrailTargets.removeValue(forKey: tabId)
        }
        let fallbackTitle = sessionController.state.tabs
            .first(where: { $0.id == tabId })?
            .title ?? ""
        let result = CandyTrailHistoryReconciler.shared.reconcile(
            trail: trail,
            tabId: tabId,
            previous: candyTrailHistoryBindings[tabId] ?? emptyCandyTrailHistoryBinding,
            snapshot: snapshot,
            title: title.isEmpty ? fallbackTitle : title,
            visitedAt: currentTimeMillis,
            pendingTargetNodeId: pendingTargetNodeId
        )
        candyTrailHistoryBindings[tabId] = result.binding
        setCandyTrail(result.trail, tabId: tabId)
    }

    func browserEngineDidRefineTrailTitle(
        tabId: String,
        sessionId: UUID,
        address: String,
        title: String
    ) {
        guard
            let session = eligibleCandyTrailSession(tabId: tabId),
            session.sessionId == sessionId,
            let trail = candyTrails[tabId]
        else {
            return
        }
        let refined = CandyTrailHistoryReconciler.shared.refineCurrentTitle(
            trail: trail,
            url: address,
            title: title,
            visitedAt: currentTimeMillis
        )
        setCandyTrail(refined, tabId: tabId)
    }
}

extension BrowserViewModel: ToppingRuntimeDelegate {
    func toppingRuntimeDidChangeCommands(tabId: String, commands: [ToppingMenuCommand]) {
        if tabId == selectedTabId {
            synchronizeTabs()
        }
    }

    func toppingRuntimeOpenTab(sourceTabId: String, url: URL, active: Bool) {
        guard sessions[sourceTabId] != nil else { return }
        let previouslySelected = selectedTabId
        performTabs(.newtab)
        let openedTabId = selectedTabId
        editAddress(url.absoluteString)
        navigate()
        if !active, previouslySelected != openedTabId {
            performTabs(.selecttab, tabId: previouslySelected)
        }
    }
}
