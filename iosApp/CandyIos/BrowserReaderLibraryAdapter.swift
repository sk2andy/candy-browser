import CandyShared
import Foundation

@MainActor
final class BrowserReaderLibraryAdapter: NSObject, @preconcurrency ReaderLibraryDataSource {
    private let controller = ReaderLibraryController()
    private let store: BrowserReaderLibraryStore
    private let profileId: () -> String?
    private let onFailure: () -> Void
    private var isLoaded = false
    private var isLoading = false
    private var isProcessing = false
    private var operations: [() -> Void] = []
    private var pendingCompletion: (() -> Void)?
    private var committedJSON: String?

    init(
        store: BrowserReaderLibraryStore = BrowserReaderLibraryStore(),
        profileId: @escaping () -> String?,
        onFailure: @escaping () -> Void
    ) {
        self.store = store
        self.profileId = profileId
        self.onFailure = onFailure
        super.init()
    }

    func load(isPrivate: Bool, onLoaded: @escaping (ReaderLibraryState) -> Void) {
        guard !isPrivate else {
            onLoaded(controller.visibleState(isPrivate: true))
            return
        }
        enqueue { [self] in
            onLoaded(controller.visibleState(isPrivate: false))
            finish()
        }
    }

    func updateSettings(
        settings: ReaderSettings,
        isPrivate: Bool,
        onUpdated: @escaping (ReaderLibraryState) -> Void
    ) {
        guard !isPrivate else {
            onUpdated(controller.visibleState(isPrivate: true))
            return
        }
        enqueue { [self] in
            mutate({ controller.updateSettings(settings: settings, isPrivate: false) }, onUpdated: onUpdated)
        }
    }

    func updateProgress(sourceUrl: String, progress: Float, isPrivate: Bool) {
        guard !isPrivate else { return }
        enqueue { [self] in
            mutate({
                controller.updateProgress(sourceUrl: sourceUrl, progress: progress, isPrivate: false)
            }, onUpdated: { _ in })
        }
    }

    func saveSnapshot(
        document: ReaderDocument,
        progress: Float,
        isPrivate: Bool,
        onUpdated: @escaping (ReaderLibraryState) -> Void
    ) {
        guard !isPrivate else {
            onUpdated(controller.visibleState(isPrivate: true))
            return
        }
        let sourceProfileId = profileId()
        let savedAt = Int64(Date().timeIntervalSince1970 * 1_000)
        let snapshotId = UUID().uuidString.lowercased()
        enqueue { [self] in
            mutate({
                controller.saveSnapshot(
                    document: document,
                    progress: progress,
                    isPrivate: false,
                    nowMillis: savedAt,
                    snapshotId: snapshotId,
                    sourceProfileId: sourceProfileId
                )
            }, onUpdated: onUpdated)
        }
    }

    func deleteSnapshot(
        snapshotId: String,
        isPrivate: Bool,
        onUpdated: @escaping (ReaderLibraryState) -> Void
    ) {
        guard !isPrivate else {
            onUpdated(controller.visibleState(isPrivate: true))
            return
        }
        enqueue { [self] in
            mutate({
                controller.deleteSnapshot(snapshotId: snapshotId, isPrivate: false)
            }, onUpdated: onUpdated)
        }
    }

    private func enqueue(_ operation: @escaping () -> Void) {
        operations.append(operation)
        guard !isLoading else { return }
        if !isLoaded {
            isLoading = true
            store.load { [weak self] raw in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    self.controller.restore(raw: raw)
                    self.committedJSON = raw
                    self.isLoaded = true
                    self.isLoading = false
                    self.processNext()
                }
            }
        } else {
            processNext()
        }
    }

    private func processNext() {
        guard isLoaded, !isProcessing, !operations.isEmpty else { return }
        isProcessing = true
        operations.removeFirst()()
    }

    private func finish() {
        isProcessing = false
        processNext()
    }

    private func mutate(
        _ transform: () -> ReaderLibraryState,
        onUpdated: @escaping (ReaderLibraryState) -> Void
    ) {
        let beforeState = controller.state
        _ = transform()
        if controller.state.isEqual(beforeState) {
            onUpdated(controller.visibleState(isPrivate: false))
            finish()
            return
        }
        let before = committedJSON
        let after = controller.persistenceJson()
        if before == after {
            onUpdated(controller.visibleState(isPrivate: false))
            finish()
            return
        }
        pendingCompletion = { [self] in
            onUpdated(controller.visibleState(isPrivate: false))
            finish()
        }
        store.save(after) { [weak self] succeeded in
            Task { @MainActor [weak self] in
                guard let self else { return }
                if !succeeded {
                    self.controller.restore(raw: before)
                    self.onFailure()
                } else {
                    self.committedJSON = after
                }
                let completion = self.pendingCompletion
                self.pendingCompletion = nil
                completion?()
            }
        }
    }
}
