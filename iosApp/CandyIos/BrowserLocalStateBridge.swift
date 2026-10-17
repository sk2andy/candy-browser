import CandyShared

extension BrowserStoredLibraryEntry {
    init(_ entry: BrowserLibraryEntry) {
        address = entry.address
        title = entry.title
        updatedAtMillis = entry.updatedAtMillis
        profileId = entry.profileId
    }

    var sharedEntry: BrowserLibraryEntry {
        BrowserLibraryEntry(
            address: address,
            title: title,
            updatedAtMillis: updatedAtMillis,
            profileId: profileId
        )
    }
}

extension BrowserStoredState {
    init(
        session: BrowserSessionSnapshot,
        library: BrowserLibraryState,
        desktopPreferences: [BrowserDesktopPreference]
    ) {
        version = Int(session.version)
        tabs = session.tabs.map { tab in
            BrowserStoredTab(
                id: tab.id,
                address: tab.address,
                title: tab.title,
                profileId: tab.profileId,
                isPinned: tab.isPinned,
                syncCandyId: tab.syncCandyId
            )
        }
        snoozedTabs = session.snoozedTabs.map { record in
            BrowserStoredSnoozedTab(
                id: record.tab.id, address: record.tab.url, title: record.tab.title,
                profileId: record.tab.profileId, isPinned: record.tab.isPinned,
                wakeAtMillis: record.wakeAtMillis, createdAtMillis: record.createdAtMillis
            )
        }
        selectedTabId = session.selectedTabId
        activeProfileId = session.activeProfileId
        favorites = library.favorites.map(BrowserStoredLibraryEntry.init)
        history = library.history.map(BrowserStoredLibraryEntry.init)
        self.desktopPreferences = desktopPreferences.map { entry in
            BrowserStoredDesktopPreference(profileId: entry.profileId, host: entry.host, enabled: entry.enabled)
        }
    }

    var sharedSession: BrowserSessionSnapshot {
        BrowserSessionSnapshot(
            version: Int32(clamping: version),
            tabs: tabs.map { tab in
                BrowserTabState(
                    id: tab.id,
                    address: tab.address,
                    title: tab.title,
                    profileId: tab.profileId,
                    canGoBack: false,
                    canGoForward: false,
                    isLoading: false,
                    isPinned: tab.isPinned,
                    isPrivate: false,
                    syncCandyId: tab.syncCandyId
                )
            },
            selectedTabId: selectedTabId,
            activeProfileId: activeProfileId,
            snoozedTabs: (snoozedTabs ?? []).compactMap { record in
                BrowserSnoozeRules.shared.storedTab(
                    id: record.id, address: record.address, title: record.title,
                    profileId: record.profileId, isPinned: record.isPinned,
                    wakeAtMillis: record.wakeAtMillis, createdAtMillis: record.createdAtMillis
                )
            }
        )
    }
}

extension BrowserStoredDesktopPreference {
    var sharedPreference: BrowserDesktopPreference {
        BrowserDesktopPreference(profileId: profileId, host: host, enabled: enabled)
    }
}
