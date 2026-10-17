import Foundation

@main
enum BrowserLocalStateStoreTests {
    static func main() throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("candy-local-state-tests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let url = root.appendingPathComponent("browser/state.json")
        let store = BrowserLocalStateStore(fileURL: url)
        expect(store.load() == nil, "missing state starts empty")

        let first = state(address: "https://candy.example/日本語", pinned: true)
        expect(save(first, in: store), "atomic write creates parent directory")
        expect(BrowserLocalStateStore(fileURL: url).load() == first, "tabs, selection, Unicode, pins and library survive restart")

        expect(store.load()?.desktopPreferences == nil, "existing v1 sessions without desktop preferences remain readable")
        var desktopState = first
        desktopState.desktopPreferences = [BrowserStoredDesktopPreference(
            profileId: "candy", host: "candy.example", enabled: true
        )]
        expect(save(desktopState, in: store), "desktop preferences save alongside regular session")
        expect(BrowserLocalStateStore(fileURL: url).load() == desktopState, "desktop profile and exact host survive restart")

        expect(store.load()?.snoozedTabs == nil, "existing v1 sessions without snooze records remain readable")
        var snoozeState = desktopState
        snoozeState.snoozedTabs = [BrowserStoredSnoozedTab(
            id: "sleeping", address: "https://candy.example", title: "Later", profileId: "candy",
            isPinned: true, wakeAtMillis: 1_800_000_000_000, createdAtMillis: 1_700_000_000_000
        )]
        expect(save(snoozeState, in: store), "snooze records save atomically with active tabs")
        expect(BrowserLocalStateStore(fileURL: url).load() == snoozeState, "snooze provenance and due time survive restart")

        let second = state(address: "https://second.example", pinned: false)
        expect(save(second, in: store), "subsequent write succeeds")
        expect(store.load() == second, "latest scheduled state replaces previous envelope")
        expect(save(second, in: store), "identical write is harmless")

        let invalidVersion = BrowserStoredState(
            version: 99, tabs: [], selectedTabId: nil, activeProfileId: nil, favorites: [], history: []
        )
        expect(!save(invalidVersion, in: store), "unsupported envelope does not overwrite state")
        expect(store.load() == second, "rejected write retains accepted state")

        try Data("{broken".utf8).write(to: url)
        expect(BrowserLocalStateStore(fileURL: url).load() == nil, "truncated JSON fails safely")
        try JSONEncoder().encode(invalidVersion).write(to: url)
        expect(BrowserLocalStateStore(fileURL: url).load() == nil, "future schema fails safely")
        try Data(repeating: 32, count: 8 * 1_024 * 1_024 + 1).write(to: url)
        expect(BrowserLocalStateStore(fileURL: url).load() == nil, "oversized input rejected before decoding")

        let oversized = state(address: String(repeating: "x", count: 8 * 1_024 * 1_024), pinned: false)
        expect(!save(oversized, in: store), "oversized output rejected")

        let blocker = root.appendingPathComponent("file-instead-of-directory")
        try Data().write(to: blocker)
        expect(!save(first, in: BrowserLocalStateStore(fileURL: blocker.appendingPathComponent("state.json"))), "storage failure reported")
        print("BrowserLocalStateStoreTests: 19 passed")
    }

    private static func state(address: String, pinned: Bool) -> BrowserStoredState {
        let entry = BrowserStoredLibraryEntry(
            address: address, title: "Candy Reader", updatedAtMillis: 1_700_000_000_000, profileId: "candy"
        )
        return BrowserStoredState(
            version: 1,
            tabs: [BrowserStoredTab(
                id: "tab-1", address: address, title: "Candy", profileId: "candy", isPinned: pinned, syncCandyId: nil
            )],
            selectedTabId: "tab-1", activeProfileId: "candy", favorites: [entry], history: [entry]
        )
    }

    private static func save(_ value: BrowserStoredState, in store: BrowserLocalStateStore) -> Bool {
        let semaphore = DispatchSemaphore(value: 0)
        let result = SaveResult()
        store.save(value) { succeeded in
            result.value = succeeded
            semaphore.signal()
        }
        guard semaphore.wait(timeout: .now() + 5) == .success else {
            fatalError("Storage callback timed out")
        }
        return result.value
    }

    private static func expect(_ value: Bool, _ message: String) {
        if !value { fatalError("Failed: \(message)") }
    }

    private final class SaveResult: @unchecked Sendable {
        var value = false
    }
}
