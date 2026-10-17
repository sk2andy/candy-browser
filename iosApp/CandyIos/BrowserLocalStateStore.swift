import Foundation

struct BrowserStoredLibraryEntry: Codable, Equatable {
    let address: String
    let title: String
    let updatedAtMillis: Int64
    let profileId: String
}

struct BrowserStoredTab: Codable, Equatable {
    let id: String
    let address: String
    let title: String
    let profileId: String
    let isPinned: Bool
    let syncCandyId: String?
}

struct BrowserStoredDesktopPreference: Codable, Equatable {
    let profileId: String
    let host: String
    let enabled: Bool
}

struct BrowserStoredSnoozedTab: Codable, Equatable {
    let id: String
    let address: String
    let title: String
    let profileId: String
    let isPinned: Bool
    let wakeAtMillis: Int64
    let createdAtMillis: Int64
}

struct BrowserStoredState: Codable, Equatable {
    let version: Int
    let tabs: [BrowserStoredTab]
    let selectedTabId: String?
    let activeProfileId: String?
    let favorites: [BrowserStoredLibraryEntry]
    let history: [BrowserStoredLibraryEntry]
    var desktopPreferences: [BrowserStoredDesktopPreference]? = nil
    var snoozedTabs: [BrowserStoredSnoozedTab]? = nil
}

/// Apple storage only. Shared Kotlin validates URLs, profiles, retention and private state.
final class BrowserLocalStateStore: @unchecked Sendable {
    private let fileURL: URL
    private let queue = DispatchQueue(label: "dev.sk2andy.candy.browser-state", qos: .utility)
    private var lastScheduledState: BrowserStoredState?
    private static let maximumBytes = 8 * 1_024 * 1_024

    init(fileURL: URL = BrowserLocalStateStore.defaultFileURL) {
        self.fileURL = fileURL
    }

    static var defaultFileURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("CandyBrowser", isDirectory: true)
            .appendingPathComponent("browser-state-v1.json")
    }

    func load() -> BrowserStoredState? {
        queue.sync {
            guard
                let attributes = try? FileManager.default.attributesOfItem(atPath: fileURL.path),
                let byteCount = attributes[.size] as? NSNumber,
                byteCount.intValue <= Self.maximumBytes,
                let data = try? Data(contentsOf: fileURL),
                let value = try? JSONDecoder().decode(BrowserStoredState.self, from: data),
                value.version == 1
            else {
                return nil
            }
            lastScheduledState = value
            return value
        }
    }

    func save(
        _ state: BrowserStoredState,
        completion: @escaping @Sendable (Bool) -> Void = { _ in }
    ) {
        queue.async { [self] in
            if state == lastScheduledState {
                completion(true)
                return
            }
            do {
                let data = try JSONEncoder().encode(state)
                guard state.version == 1, data.count <= Self.maximumBytes else {
                    completion(false)
                    return
                }
                let directory = fileURL.deletingLastPathComponent()
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                var protectedDirectory = directory
                var resourceValues = URLResourceValues()
                resourceValues.isExcludedFromBackup = true
                try protectedDirectory.setResourceValues(resourceValues)
                #if os(iOS)
                try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUnlessOpen])
                #else
                try data.write(to: fileURL, options: .atomic)
                #endif
                lastScheduledState = state
                completion(true)
            } catch {
                completion(false)
            }
        }
    }
}
