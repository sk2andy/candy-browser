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
    let isPrivate: Bool
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
    @Published private(set) var tabListStartsAtBottom: Bool
    @Published private(set) var dismissResistancePercent: Int32
    @Published private(set) var appearanceSettings: AppearanceSettings
    @Published private(set) var isAddressBarCompact = false
    @Published private(set) var addressBarDockPlacement: AddressBarDockPlacement?
    private(set) var lastAddressBarDockPlacement: AddressBarDockPlacement
    @Published private(set) var searchEngine: SearchEngine
    @Published private(set) var searxngInstanceUrl: String
    @Published private(set) var translationProvider: PageTranslationProvider
    @Published private(set) var menuLayout: BrowserMenuLayout
    @Published private(set) var menuItems: [BrowserFeatureMenuItem] = []
    @Published private(set) var isBrowserMenuVisible = false
    @Published private(set) var isTabOverviewVisible = false
    @Published private(set) var isSettingsVisible = false
    @Published private(set) var isFavoritesVisible = false
    @Published private(set) var isHistoryVisible = false
    @Published private(set) var snoozedTabs: [SnoozedTab] = []
    @Published private(set) var snoozeTab: BrowserTab?
    @Published private(set) var isSnoozedTabsVisible = false
    @Published var isCustomSnoozeVisible = false
    private var snoozeRescheduleId: String?
    private lazy var snoozeNotifications: BrowserSnoozeNotifications = {
        let notifications = BrowserSnoozeNotifications.shared
        notifications.bind(
            onOpen: { [weak self] tabId in self?.openSnoozedTabNow(tabId) },
            onWake: { [weak self] in self?.resumeSnoozedTabs() }
        )
        return notifications
    }()
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
    let menuConfigurableEntries: [BrowserMenuEntry] = [
        .back,
        .forward,
        .reload,
        .favorite,
        .pin,
        .showtabs,
        .newtab,
        .closetab,
        .duplicatetab,
        .reader,
        .translate,
        .findinpage,
        .share,
        .openexternal,
        .print,
        .desktopview,
        .toppingcommands,
        .candytrail,
        .openfavorites,
        .openhistory,
        .snooze,
        .opensnoozedtabs,
        .opensettings,
        .addressbardocking,
    ]

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
    private let addressBarScroll = BrowserChromeScrollController()
    private var addressBarScrollSessionId: UUID?
    private var isAddressBarEditing = false
    private let localStateStore: BrowserLocalStateStore
    private let libraryController: BrowserLibraryController
    private let sitePreferences: BrowserSitePreferenceController
    private let syncRepository: CandySyncRepository
    private var sessions: [String: WKWebViewBrowserSessionAdapter]
    private var syncObservation: KotlinAutoCloseable?
    private var candyTrails: [String: CandyTrail] = [:]
    private var candyTrailHistoryBindings: [String: CandyTrailHistoryBinding] = [:]
    private var pendingCandyTrailTargets: [String: String] = [:]
    private var pageExtractionRequestId: UUID?
    private var findRequestId: UUID?
    private var findTabId: String?
    private var findSessionId: UUID?
    private var readerSpeechAdapter: BrowserReaderSpeechAdapter?
    private var deletingProfileId: String?
    private var profileCleanupAfterViewportMount: (() -> Void)?
    private lazy var readerLibraryAdapter = BrowserReaderLibraryAdapter(
        profileId: { [weak self] in self?.activeProfileId },
        onFailure: { [weak self] in
            self?.errorMessage = "Reader-Bibliothek konnte nicht gespeichert werden."
        }
    )

    private static let candyTrailProfileId = "ios-default"
    private static let localProfilesPreferenceKey = "ios.browser.local-profiles.v1"
    private static let activeProfilePreferenceKey = "ios.browser.active-profile.v1"

    override init() {
        let shared = CandySharedFacade()
        let chrome = BrowserChromeController()
        let sessionController = BrowserSessionController()
        let snapshotStore = BrowserTabSnapshotStore()
        let preferences = UserDefaults.standard
        let localStateStore = BrowserLocalStateStore()
        let libraryController = BrowserLibraryController()
        let sitePreferences = BrowserSitePreferenceController()
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
        if let stored = localStateStore.load() {
            _ = sessionController.restoreSession(snapshot: stored.sharedSession)
            sitePreferences.restore(entries: (stored.desktopPreferences ?? []).map(\.sharedPreference))
            libraryController.restore(
                favorites: stored.favorites.map(\.sharedEntry),
                history: stored.history.map(\.sharedEntry)
            )
        }
        _ = sessionController.restoreDueSnoozedTabs(nowMillis: Int64(Date().timeIntervalSince1970 * 1_000))
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
        let initialMenuLayout = BrowserMenuLayoutRules.shared.fromWireValues(
            values: BrowserMenuLayoutPreference.loadWireValues(from: preferences)
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
                .isolationEnabled ?? false,
            isPrivate: initialTab.isPrivate
        )

        self.shared = shared
        self.chrome = chrome
        self.sessionController = sessionController
        self.snapshotStore = snapshotStore
        self.preferences = preferences
        let rememberedPlacement = BrowserAddressBarPreference.rememberedPlacement(from: preferences)
        lastAddressBarDockPlacement = rememberedPlacement
        addressBarDockPlacement = BrowserAddressBarPreference.isDocked(in: preferences)
            ? rememberedPlacement : nil
        self.localStateStore = localStateStore
        self.libraryController = libraryController
        self.sitePreferences = sitePreferences
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
        tabListStartsAtBottom = BrowserTabOverviewStartsAtBottomPreference.load(
            from: preferences
        )
        dismissResistancePercent = TabDismissPhysics.shared.resistancePercent(
            percent: BrowserTabDismissResistancePreference.loadValue(
                from: preferences,
                defaultValue: TabDismissPhysics.shared.DEFAULT_RESISTANCE_PERCENT
            )
        )
        appearanceSettings = BrowserAppearancePreference.load(from: preferences)
        searchEngine = initialSearchSettings.searchEngine
        searxngInstanceUrl = initialSearchSettings.searxngInstanceUrl
        translationProvider = PageTranslationProvider.companion.fromStableId(
            stableId: BrowserTranslationProviderPreference.loadStableId(from: preferences)
        )
        menuLayout = initialMenuLayout

        super.init()

        _ = BrowserDownloadRuntime.shared
        ToppingRuntime.shared.delegate = self
        publishToppings()
        initialSession.eventSink = self
        bindSitePreferences(to: initialSession)
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

    var canParkAddressBar: Bool {
        sessionController.state.tabs.first(where: { $0.id == selectedTabId })?.address.isEmpty == false
    }

    func parkAddressBar(onRight: Bool = false) {
        guard canParkAddressBar else { return }
        let placement = AddressBarDockPlacement(
            edge: onRight ? .right : lastAddressBarDockPlacement.edge,
            verticalFraction: lastAddressBarDockPlacement.verticalFraction
        )
        updateAddressBarDockPlacement(placement)
    }

    func updateAddressBarDockPlacement(_ placement: AddressBarDockPlacement) {
        guard canParkAddressBar else { return }
        let normalized = placement.normalized()
        lastAddressBarDockPlacement = normalized
        addressBarDockPlacement = normalized
        BrowserAddressBarPreference.save(placement: normalized, remembered: normalized, to: preferences)
    }

    func restoreAddressBar() {
        addressBarDockPlacement = nil
        resetAddressBarScroll()
        BrowserAddressBarPreference.save(
            placement: nil,
            remembered: lastAddressBarDockPlacement,
            to: preferences
        )
    }

    func setAddressBarEditing(_ editing: Bool) {
        isAddressBarEditing = editing
        resetAddressBarScroll()
    }

    private func resetAddressBarScroll() {
        let scrollView = sessions[selectedTabId]?.webView.scrollView
        addressBarScroll.reset(scrollY: scrollView.map {
            Double($0.contentOffset.y + $0.adjustedContentInset.top)
        } ?? 0)
        isAddressBarCompact = false
    }

    func perform(_ action: BrowserChromeAction) {
        guard deletingProfileId == nil else { return }
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
        guard deletingProfileId == nil else { return }
        let closingTabId = tabId ?? selectedTabId
        let closingSyncTab = intent == .closetab
            ? sessionController.state.tabs.first(where: { $0.id == closingTabId })
            : nil
        if intent == .closetab,
           sessionController.state.tabs.first(where: { $0.id == closingTabId })?.isPinned == true {
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
        if intent == .newtab || intent == .duplicatetab ||
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

    var snoozeSnapshot: BrowserSnoozeSnapshot? {
        guard snoozeTab != nil || isSnoozedTabsVisible else { return nil }
        return BrowserSnoozeSnapshot(
            tab: isCustomSnoozeVisible ? nil : snoozeTab, tabs: snoozedTabs, profiles: profiles,
            isLibraryVisible: isSnoozedTabsVisible,
            wakeTimeLabels: Dictionary(uniqueKeysWithValues: snoozedTabs.map { record in
                (record.tab.id, Date(timeIntervalSince1970: Double(record.wakeAtMillis) / 1_000)
                    .formatted(date: .abbreviated, time: .shortened))
            })
        )
    }

    func canSnooze(tabId: String) -> Bool {
        deletingProfileId == nil && sessionController.canSnoozeTab(tabId: tabId)
    }

    func showSnooze(tabId: String) {
        guard deletingProfileId == nil else { return }
        guard let candidate = sessionController.snoozeCandidate(tabId: tabId) else { return }
        cancelFeaturePresentations()
        snoozeRescheduleId = nil
        snoozeTab = candidate
    }

    func showSnoozedTabs() {
        cancelFeaturePresentations()
        isSnoozedTabsVisible = true
    }

    func dismissSnooze() {
        snoozeTab = nil
        snoozeRescheduleId = nil
        isCustomSnoozeVisible = false
        isSnoozedTabsVisible = false
    }

    func applySnoozePreset(_ preset: SnoozePreset) {
        guard let wakeAt = SnoozeClockRules.shared.wakeAtMillis(
            preset: preset, nowMillis: currentTimeMillis, zoneId: TimeZone.current.identifier
        ) else { return }
        applySnooze(wakeAtMillis: wakeAt.int64Value)
    }

    func customSnooze() {
        isCustomSnoozeVisible = snoozeTab != nil
    }

    func applyCustomSnooze(_ date: Date) {
        applySnooze(wakeAtMillis: Int64(date.timeIntervalSince1970 * 1_000))
    }

    private func applySnooze(wakeAtMillis: Int64) {
        guard deletingProfileId == nil else { return }
        guard let candidate = snoozeTab else { return }
        let succeeded: Bool
        if let snoozeRescheduleId {
            succeeded = sessionController.rescheduleSnoozedTab(
                tabId: snoozeRescheduleId, wakeAtMillis: wakeAtMillis, nowMillis: currentTimeMillis
            )
        } else {
            succeeded = sessionController.snoozeTab(
                tabId: candidate.id, wakeAtMillis: wakeAtMillis, nowMillis: currentTimeMillis
            )
        }
        guard succeeded else {
            errorMessage = "Der Tab konnte nicht zurückgestellt werden. Bitte eine spätere Zeit wählen."
            return
        }
        dismissSnooze()
        synchronizeTabs()
        snoozeNotifications.requestReminders()
    }

    func rescheduleSnoozedTab(_ tabId: String) {
        guard deletingProfileId == nil else { return }
        guard let record = snoozedTabs.first(where: { $0.tab.id == tabId }) else { return }
        snoozeRescheduleId = tabId
        snoozeTab = record.tab
    }

    func openSnoozedTabNow(_ tabId: String) {
        guard deletingProfileId == nil else { return }
        let restored = sessionController.openSnoozedTabNow(tabId: tabId, nowMillis: currentTimeMillis)
        guard restored || sessionController.state.tabs.contains(where: { $0.id == tabId }) else { return }
        dismissFind()
        cancelFeaturePresentations()
        if let tab = sessionController.state.tabs.first(where: { $0.id == tabId }) {
            sessionController.selectProfile(profileId: tab.profileId)
            sessionController.dispatchTabs(intent: .selecttab, tabId: tabId)
        }
        synchronizeTabs()
    }

    func deleteSnoozedTab(_ tabId: String) {
        guard deletingProfileId == nil else { return }
        guard sessionController.deleteSnoozedTab(tabId: tabId) else { return }
        synchronizeTabs()
    }

    func resumeSnoozedTabs() {
        guard deletingProfileId == nil else { return }
        _ = sessionController.restoreDueSnoozedTabs(nowMillis: currentTimeMillis)
        synchronizeTabs()
        snoozeNotifications.reconcile(
            snoozedTabs,
            reminderRecords: SnoozeNotificationRules.shared.eligibleSnoozedTabs(
                tabs: snoozedTabs, profiles: profiles
            ),
            force: true
        )
    }

    func newPrivateTab() {
        guard deletingProfileId == nil else { return }
        captureSnapshot(tabId: selectedTabId)
        dismissFind()
        cancelFeaturePresentations()
        sessionController.doNewPrivateTab()
        synchronizeTabs()
    }

    func selectProfile(_ profileId: String) {
        guard deletingProfileId == nil else { return }
        guard profileId != activeProfileId else {
            return
        }
        captureSnapshot(tabId: selectedTabId)
        sessionController.selectProfile(profileId: profileId)
        synchronizeTabs()
        persistLocalProfiles()
    }

    func createProfile(emoji: String, isolationEnabled: Bool) {
        guard deletingProfileId == nil else { return }
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
        guard deletingProfileId == nil else { return }
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
        retireProfileSessions(tabIds: affectedTabIds)
        synchronizeTabs()
        persistLocalProfiles()
    }

    func canDeleteProfile(profileId: String) -> Bool {
        deletingProfileId == nil && BrowserProfileManagementRules.shared.canDeleteProfile(
            state: sessionController.state, profileId: profileId
        )
    }

    func moveTargetProfileIds(tabId: String) -> [String] {
        guard deletingProfileId == nil else { return [] }
        return BrowserProfileManagementRules.shared.moveTargets(
            state: sessionController.state, tabId: tabId
        ).map(\.id)
    }

    func closeAllTabs() {
        guard deletingProfileId == nil,
              sessionController.state.profiles.first(where: { $0.id == activeProfileId })?.protection == nil else { return }
        let closingTabs = sessionController.state.activeTabs.filter { !$0.isPinned }
        guard !closingTabs.isEmpty else { return }
        dismissFind()
        cancelFeaturePresentations()
        closingTabs.forEach(enqueueSyncedTabClose)
        sessionController.closeAllTabs()
        synchronizeTabs()
    }

    func moveTabToProfile(tabId: String, profileId: String) {
        guard deletingProfileId == nil,
              moveTargetProfileIds(tabId: tabId).contains(profileId),
              let source = sessionController.state.tabs.first(where: { $0.id == tabId }) else { return }
        dismissFind()
        cancelFeaturePresentations()
        enqueueSyncedTabClose(source)
        retireProfileSessions(tabIds: [tabId])
        sessionController.moveTabToProfile(tabId: tabId, profileId: profileId)
        sitePreferences.clearTabOverrides(tabId: tabId)
        if !source.isPrivate, syncTargetDeviceId(profileId: profileId) != nil,
           sessionController.state.tabs.first(where: { $0.id == tabId })?.syncCandyId == nil {
            sessionController.assignSyncCandyId(tabId: tabId, candyId: UUID().uuidString.lowercased())
        }
        synchronizeTabs()
        enqueueSyncedTab(tabId: tabId)
    }

    func deleteProfile(profileId: String) {
        guard canDeleteProfile(profileId: profileId),
              let profile = sessionController.state.profiles.first(where: { $0.id == profileId }),
              let fallback = sessionController.state.profiles.first(where: {
                  $0.id != profileId && $0.syncedDeviceId == nil && $0.protection == nil
              }) else { return }
        let affectedIds = sessionController.state.tabs.filter { $0.profileId == profileId }.map(\.id)
        deletingProfileId = profileId
        dismissFind()
        cancelFeaturePresentations()
        if activeProfileId == profileId { sessionController.selectProfile(profileId: fallback.id) }
        retireProfileSessions(tabIds: affectedIds)
        let finish: @MainActor @Sendable (Bool) -> Void = { [weak self] succeeded in
            guard let self, self.deletingProfileId == profileId else { return }
            guard succeeded, BrowserProfileManagementRules.shared.canDeleteProfile(
                state: self.sessionController.state, profileId: profileId
            ) else {
                self.deletingProfileId = nil
                self.profileCleanupAfterViewportMount = nil
                self.synchronizeTabs()
                self.errorMessage = "Das Profil konnte nicht vollständig gelöscht werden."
                return
            }
            self.sessionController.deleteProfile(profileId: profileId)
            self.libraryController.clearHistory(profileId: profileId)
            self.sitePreferences.removeProfile(profileId: profileId, tabIds: affectedIds)
            self.deletingProfileId = nil
            self.profileCleanupAfterViewportMount = nil
            self.synchronizeTabs()
            self.persistLocalProfiles()
        }
        if profile.isolationEnabled {
            profileCleanupAfterViewportMount = {
                WKWebViewBrowserSessionAdapter.deleteProfileData(profileId: profileId, completion: finish)
            }
            synchronizeTabs()
        } else {
            finish(true)
        }
    }

    func viewportDidMount(tabId: String) {
        guard let deletingProfileId,
              tabId == sessionController.state.selectedTabId,
              sessionController.state.tabs.first(where: { $0.id == tabId })?.profileId != deletingProfileId,
              let cleanup = profileCleanupAfterViewportMount else { return }
        profileCleanupAfterViewportMount = nil
        cleanup()
    }

    private func retireProfileSessions(tabIds: [String]) {
        tabIds.forEach { tabId in
            sessions.removeValue(forKey: tabId)?.eventSink = nil
            _ = sessionController.detachSession(tabId: tabId)
            snapshotStore.invalidate(tabId: tabId)
            candyTrails.removeValue(forKey: tabId)
            candyTrailHistoryBindings.removeValue(forKey: tabId)
            pendingCandyTrailTargets.removeValue(forKey: tabId)
        }
    }

    func showSettings() {
        cancelFeaturePresentations()
        isSettingsVisible = true
    }

    func dismissSettings() {
        isSettingsVisible = false
    }

    func changeBrowserMenuLocation(
        entry: BrowserMenuEntry,
        location: BrowserMenuLocation
    ) {
        let updated = BrowserMenuLayoutRules.shared.update(
            layout: menuLayout,
            entry: entry,
            location: location
        )
        changeBrowserMenuLayout(layout: updated)
    }

    func changeBrowserMenuLayout(layout: BrowserMenuLayout) {
        let updated = BrowserMenuLayoutRules.shared.normalize(layout: layout)
        menuLayout = updated
        BrowserMenuLayoutPreference.save(
            wireValues: BrowserMenuLayoutRules.shared.toWireValues(layout: updated),
            to: preferences
        )
        guard let selectedTab = sessionController.state.tabs.first(
            where: { $0.id == selectedTabId }
        ) else {
            return
        }
        menuItems = featureMenuItems(for: selectedTab)
    }

    func showBrowserMenu() {
        dismissFind()
        isBrowserMenuVisible = true
    }

    func dismissBrowserMenu() {
        isBrowserMenuVisible = false
    }

    func showFavorites() {
        cancelFeaturePresentations()
        isFavoritesVisible = true
    }

    var librarySnapshot: BrowserLibrarySnapshot? {
        if isFavoritesVisible {
            return BrowserLibrarySnapshot(kind: .favorites, entries: libraryController.state.favorites)
        }
        if isHistoryVisible {
            return BrowserLibrarySnapshot(
                kind: .history,
                entries: libraryController.state.history.filter { $0.profileId == activeProfileId }
            )
        }
        return nil
    }

    func showHistory() {
        cancelFeaturePresentations()
        isHistoryVisible = true
    }

    func dismissLibrary() {
        isFavoritesVisible = false
        isHistoryVisible = false
    }

    func openLibraryEntry(_ entry: BrowserLibraryEntry) {
        guard librarySnapshot?.entries.contains(where: {
            $0.address == entry.address && $0.profileId == entry.profileId
        }) == true else { return }
        let resolution = sessionController.navigateSelected(input: entry.address)
        guard let value = resolution.url?.value else {
            errorMessage = "Dieser Eintrag kann nicht geöffnet werden."
            return
        }
        dismissLibrary()
        address = value
        errorMessage = nil
        synchronizeTabs()
    }

    func removeLibraryEntry(_ entry: BrowserLibraryEntry) {
        if isFavoritesVisible {
            libraryController.removeFavorite(address: entry.address)
        } else if isHistoryVisible, entry.profileId == activeProfileId {
            libraryController.removeHistory(address: entry.address, profileId: activeProfileId)
        }
        synchronizeTabs()
    }

    func restoreLibraryEntry(_ entry: BrowserLibraryEntry) {
        guard isFavoritesVisible else { return }
        libraryController.addFavorite(entry: entry)
        synchronizeTabs()
    }

    func clearLibraryHistory() {
        guard isHistoryVisible else { return }
        libraryController.clearHistory(profileId: activeProfileId)
        synchronizeTabs()
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

    func openSyncExtensionStore(_ url: String) {
        guard let target = syncExtensionStoreURL(url) else { return }
        UIApplication.shared.open(target)
    }

    func shareSyncExtensionStore(_ url: String) {
        guard let target = syncExtensionStoreURL(url) else { return }
        sharePayload = BrowserSharePayload(title: "Candy Sync", url: target)
    }

    private func syncExtensionStoreURL(_ url: String) -> URL? {
        guard SyncExtensionStoreUrls.shared.isStoreUrl(url: url) else { return nil }
        return URL(string: url)
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
            guard let scriptId = item.toppingScriptId,
                  let documentId = item.toppingDocumentId,
                  let commandId = item.toppingCommandId else {
                errorMessage = "Dieser Topping-Befehl ist nicht mehr verfügbar."
                return
            }
            ToppingRuntime.shared.invoke(
                tabId: selectedTabId,
                scriptId: scriptId,
                documentId: documentId,
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
        } else if action == .snoozetab {
            showSnooze(tabId: tabId)
        } else if action == .toggledesktopview {
            toggleDesktopView(tabId: tabId)
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
        } else if action == .duplicatetab {
            performTabs(.duplicatetab, tabId: selectedTabId)
        } else if action == .closetab {
            performTabs(.closetab, tabId: selectedTabId)
        } else if action == .showtabs {
            performTabs(.showoverview)
        } else if action == .togglefavorite {
            toggleFavorite()
        } else if action == .togglepinned {
            togglePinned()
        } else if action == .toggledesktopview {
            toggleDesktopView(tabId: selectedTabId)
        } else if action == .dockaddressbar {
            parkAddressBar()
        } else if action == .parkaddressbarright {
            parkAddressBar(onRight: true)
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
        } else if action == .openfavorites {
            showFavorites()
        } else if action == .openhistory {
            showHistory()
        } else if action == .snoozetab {
            showSnooze(tabId: selectedTabId)
        } else if action == .opensnoozedtabs {
            showSnoozedTabs()
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

    func createReaderLibrary() -> ReaderLibraryDataSource {
        readerLibraryAdapter
    }

    func createReaderSpeech() -> ReaderSpeech {
        readerSpeechAdapter?.close()
        let adapter = BrowserReaderSpeechAdapter()
        readerSpeechAdapter = adapter
        return adapter.bridge
    }

    func dismissReader() {
        readerSpeechAdapter?.close()
        readerSpeechAdapter = nil
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

    func updateTabListStartsAtBottom(_ enabled: Bool) {
        tabListStartsAtBottom = enabled
        BrowserTabOverviewStartsAtBottomPreference.save(enabled, to: preferences)
    }

    func updateDismissResistancePercent(_ value: Int32) {
        dismissResistancePercent = TabDismissPhysics.shared.resistancePercent(percent: value)
        BrowserTabDismissResistancePreference.saveValue(dismissResistancePercent, to: preferences)
    }

    func updateAppearanceSettings(_ value: AppearanceSettings) {
        appearanceSettings = value.normalized()
        BrowserAppearancePreference.save(appearanceSettings, to: preferences)
        let zoom = CGFloat(appearanceSettings.webContentFontSizePercent) / 100
        sessions.values.forEach { $0.webView.pageZoom = zoom }
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

    func setToppingFrameScope(id: String, scope: ToppingFrameScope) {
        do {
            try ToppingRuntime.shared.setAllowedFrameScope(scope, id: id)
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
        guard deletingProfileId == nil else { return }
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
        if deletingProfileId == nil {
            _ = sessionController.restoreDueSnoozedTabs(nowMillis: currentTimeMillis)
        }
        var state = sessionController.state
        sitePreferences.retainTabs(tabs: state.tabs)
        snoozedTabs = state.snoozedTabs
        snoozeNotifications.reconcile(
            state.snoozedTabs,
            reminderRecords: SnoozeNotificationRules.shared.eligibleSnoozedTabs(
                tabs: state.snoozedTabs, profiles: state.profiles
            )
        )
        persistBrowserState()
        let activeIds = Set(state.tabs.map(\.id))
        let closedTabIds = Set(sessions.keys).subtracting(activeIds)

        // Restored tabs retain their shared records without starting hidden network requests.
        // Create an engine only when its tab is selected; previously visited engines stay alive.
        for tab in state.tabs where tab.id == state.selectedTabId && sessions[tab.id] == nil {
            guard let profile = state.profiles.first(where: { $0.id == tab.profileId }) else {
                preconditionFailure("Every tab must reference an existing profile")
            }
            let session = WKWebViewBrowserSessionAdapter(
                tabId: tab.id,
                shared: shared,
                profileId: profile.id,
                profileIsolationEnabled: profile.isolationEnabled,
                isPrivate: tab.isPrivate
            )
            session.eventSink = self
            bindSitePreferences(to: session)
            sessions[tab.id] = session
            snapshotStore.attach(tabId: tab.id, sessionId: session.sessionId)
            precondition(sessionController.attach(session: session))
        }
        state = sessionController.state
        sessions = sessions.filter { activeIds.contains($0.key) }
        closeCandyTrailForkDestinations(tabIds: closedTabIds)
        candyTrails = candyTrails.filter { activeIds.contains($0.key) }
        candyTrailHistoryBindings = candyTrailHistoryBindings.filter { activeIds.contains($0.key) }
        pendingCandyTrailTargets = pendingCandyTrailTargets.filter { activeIds.contains($0.key) }
        if let candyTrailTabId, !activeIds.contains(candyTrailTabId) {
            closeCandyTrail()
        }
        snapshotStore.removeTabs(notIn: activeIds)

        if selectedTabId != state.selectedTabId || state.isOverviewVisible {
            isBrowserMenuVisible = false
        }
        selectedTabId = state.selectedTabId
        if addressBarScrollSessionId != sessions[selectedTabId]?.sessionId {
            addressBarScrollSessionId = sessions[selectedTabId]?.sessionId
            resetAddressBarScroll()
        }
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

    private func persistBrowserState() {
        let stored = BrowserStoredState(
            session: sessionController.persistenceSnapshot(),
            library: libraryController.state,
            desktopPreferences: sitePreferences.persistentEntries()
        )
        localStateStore.save(stored) { [weak self] succeeded in
            if !succeeded {
                Task { @MainActor [weak self] in
                    self?.errorMessage = "Browser-Daten konnten nicht gespeichert werden."
                }
            }
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
                isFavorite: libraryController.isFavorite(address: tab.address),
                isPinned: tab.isPinned,
                isPrivate: tab.isPrivate,
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
        BrowserLocalProfileRestore.apply(
            storedProfiles,
            defaultProfileId: "candy",
            updateDefault: { profile in
                controller.updateProfileEmoji(
                    profileId: profile.id,
                    emoji: allowedEmojis.contains(profile.emoji) ? profile.emoji : fallbackEmoji
                )
                controller.updateProfileIsolation(
                    profileId: profile.id,
                    enabled: profile.isolationEnabled,
                    isolationSupported: true
                )
            },
            create: { profile in
                controller.createProfile(
                    profileId: profile.id,
                    emoji: allowedEmojis.contains(profile.emoji) ? profile.emoji : fallbackEmoji,
                    isolationEnabled: profile.isolationEnabled,
                    isolationSupported: true
                )
            }
        )
        if preferences.array(forKey: localProfilesPreferenceKey) != nil,
           !storedProfiles.contains(where: { $0["id"] as? String == "candy" }),
           controller.state.profiles.count > 1 {
            controller.deleteProfile(profileId: "candy")
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
            pinned: tab.isPinned,
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
            canCloseTab: !tab.isPinned,
            canToggleFavorite: hasPage && !tab.isPrivate,
            isFavorite: libraryController.isFavorite(address: tab.address),
            isPinned: tab.isPinned,
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
            canToggleDesktopView: sitePreferences.canToggle(tab: tab),
            isDesktopView: sitePreferences.isDesktop(tab: tab),
            canToggleDomainMute: false,
            isDomainMuted: false,
            canAddSiteCapsule: false,
            canCreateSiteWidget: false,
            canSnooze: sessionController.canSnoozeTab(tabId: tab.id),
            canDockAddressBar: canParkAddressBar,
            overflowPageActions: [.showtabs, .newtab, .closetab],
            toppingCommands: ToppingRuntime.shared.commands(tabId: tab.id).map { command in
                BrowserToppingMenuCommand(
                    scriptId: command.scriptId,
                    commandId: command.commandId,
                    caption: command.caption,
                    scriptName: command.scriptName,
                    documentId: command.documentId
                )
            }
        )
        let items = BrowserFeatureMenuRules.shared.items(
            state: state,
            capabilities: BrowserFeatureMenuCapabilities(
                supportsFirefoxExtensions: false,
                supportsSiteWidgets: false
            )
        ).filter { item in
            isImplementedFeatureAction(item.action)
        }
        return BrowserMenuLayoutRules.shared.visibleItems(
            items: items,
            layout: menuLayout,
            surface: .tab
        )
    }

    private func isImplementedFeatureAction(_ action: BrowserFeatureMenuAction) -> Bool {
        action == .back ||
            action == .forward ||
            action == .reload ||
            action == .stop ||
            action == .togglefavorite ||
            action == .togglepinned ||
            action == .toggledesktopview ||
            action == .dockaddressbar ||
            action == .parkaddressbarright ||
            action == .showtabs ||
            action == .newtab ||
            action == .duplicatetab ||
            action == .closetab ||
            action == .openreader ||
            action == .opencandytrail ||
            action == .translatepage ||
            action == .findinpage ||
            action == .share ||
            action == .openexternal ||
            action == .print ||
            action == .openfavorites ||
            action == .openhistory ||
            action == .snoozetab ||
            action == .opensnoozedtabs ||
            action == .opensettings ||
            action == .invoketoppingcommand
    }

    private func toggleFavorite() {
        toggleFavorite(tabId: selectedTabId)
    }

    private func toggleFavorite(tabId: String) {
        guard let tab = sessionController.state.tabs.first(where: { $0.id == tabId }) else {
            return
        }
        libraryController.toggleFavorite(tab: tab, nowMillis: currentTimeMillis)
        synchronizeTabs()
    }

    private func togglePinned() {
        togglePinned(tabId: selectedTabId)
    }

    private func togglePinned(tabId: String) {
        guard let tab = sessionController.state.tabs.first(where: { $0.id == tabId }) else {
            return
        }
        let pinned = !tab.isPinned
        sessionController.setTabPinned(tabId: tabId, pinned: pinned)
        synchronizeTabs()
        enqueueSyncedTabPinned(tabId: tabId, pinned: pinned)
    }

    private func bindSitePreferences(to session: WKWebViewBrowserSessionAdapter) {
        session.webView.pageZoom = CGFloat(appearanceSettings.webContentFontSizePercent) / 100
        let tabId = session.tabId
        session.downloadFailureSink = { [weak self] message in
            self?.errorMessage = message
        }
        session.desktopModeForAddress = { [weak self] address in
            guard let self,
                  let tab = self.sessionController.state.tabs.first(where: { $0.id == tabId }) else {
                return false
            }
            return self.sitePreferences.isDesktopForAddress(tab: tab, address: address)
        }
    }

    private func toggleDesktopView(tabId: String) {
        guard let tab = sessionController.state.tabs.first(where: { $0.id == tabId }),
              sitePreferences.canToggle(tab: tab) else { return }
        sitePreferences.setDesktop(tab: tab, enabled: !sitePreferences.isDesktop(tab: tab))
        synchronizeTabs()
        sessionController.execute(tabId: tabId, command: BrowserEngineCommands.shared.reload())
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
        dismissBrowserMenu()
        dismissSnooze()
        dismissReader()
        sharePayload = nil
        isFavoritesVisible = false
        isHistoryVisible = false
    }

    private func presentReader() {
        cancelFeaturePresentations()
        let sourceUrl = sessionController.state.tabs
            .first(where: { $0.id == selectedTabId })?
            .address ?? ""
        let isPrivate = sessionController.state.tabs
            .first(where: { $0.id == selectedTabId })?.isPrivate ?? true
        readerSnapshot = BrowserReaderSnapshot(
            result: nil,
            sourceUrl: sourceUrl,
            isPrivate: isPrivate
        )
        extractCurrentPage { [weak self] document in
            guard let self, self.readerSnapshot != nil else {
                return
            }
            self.errorMessage = nil
            if let document {
                self.readerSnapshot = document.sharedReaderSnapshot(isPrivate: isPrivate)
            } else {
                self.readerSnapshot = BrowserReaderSnapshot(
                    result: ReaderExtractionResultFailure(reason: .emptyarticle),
                    sourceUrl: sourceUrl,
                    isPrivate: isPrivate
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
    func browserEngineDidScroll(tabId: String, sessionId: UUID, scrollY: Double) {
        guard
            BrowserChromeScrollRules.shared.accepts(
                eventTabId: tabId,
                selectedTabId: selectedTabId,
                tabExists: sessionController.state.tabs.contains(where: { $0.id == tabId }),
                rendererIsCurrent: sessions[tabId]?.sessionId == sessionId,
                destroyed: false
            ),
            !isAddressBarEditing,
            !isTabOverviewVisible,
            !isFindVisible,
            readerSnapshot == nil
        else { return }
        let compact = addressBarScroll.update(scrollY: scrollY)
        if compact != isAddressBarCompact {
            isAddressBarCompact = compact
        }
    }

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
                resetAddressBarScroll()
            } else if event.type == .navigationfailed {
                errorMessage = event.failureDescription
            }
        }
        sessionController.onEngineEvent(event: event)
        synchronizeTabs()
        if event.type == .navigationcommitted {
            let tabId = event.tabId
            if let tab = sessionController.state.tabs.first(where: { $0.id == tabId }) {
                libraryController.recordVisit(tab: tab, nowMillis: currentTimeMillis)
                persistBrowserState()
            }
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
        guard tabId == selectedTabId,
              let tab = sessionController.state.tabs.first(where: { $0.id == tabId }) else { return }
        // Registration publishes during engine construction, before the session is attached.
        // Command changes update presentation without starting another engine reconciliation.
        menuItems = featureMenuItems(for: tab)
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
