import Foundation

@main
enum BrowserDownloadFileStoreTests {
    static func main() throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("candy-download-tests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let stale = root.appendingPathComponent("private-from-previous-launch")
        try Data("private".utf8).write(to: stale)
        let store = BrowserDownloadFileStore(root: root)
        let id = UUID()
        guard let destination = prepare(store, id: id, fileName: "report.pdf") else {
            fatalError("Could not prepare destination")
        }
        expect(!FileManager.default.fileExists(atPath: stale.path), "previous-launch temporary files removed before transfer")
        expect(destination.lastPathComponent == "report.pdf", "shared safe filename preserved")
        expect(FileManager.default.fileExists(atPath: destination.deletingLastPathComponent().path), "destination directory exists")
        expect(!FileManager.default.fileExists(atPath: destination.path), "WebKit destination file must not exist")
        try Data([1, 2, 3, 4]).write(to: destination)
        expect(validate(store, file: destination, maximumBytes: 4), "finished file accepted within size bound")
        expect(!validate(store, file: destination, maximumBytes: 3), "oversized completed file rejected")
        expect(prepare(store, id: id, fileName: "report.pdf") == nil, "existing transfer cannot be overwritten")
        expect(prepare(store, id: UUID(), fileName: "../escape.pdf") == nil, "destination cannot escape unique transfer folder")
        expect(prepare(store, id: UUID(), fileName: "nested/report.pdf") == nil, "nested path cannot bypass shared sanitizer seam")
        let secondId = UUID()
        let second = prepare(store, id: secondId, fileName: "report.pdf")
        expect(second != destination, "same filename in another transfer uses unique directory")
        store.remove(id: id)
        expect(!validate(store, file: destination, maximumBytes: 4), "cancel/export cleanup removes downloaded bytes")
        expect(!FileManager.default.fileExists(atPath: destination.deletingLastPathComponent().path), "transfer directory also removed")
        store.remove(id: secondId)
        _ = validate(store, file: destination, maximumBytes: 4)
        print("BrowserDownloadFileStoreTests: 12 passed")
    }

    private static func prepare(_ store: BrowserDownloadFileStore, id: UUID, fileName: String) -> URL? {
        let result = ResultBox<URL?>(nil)
        let semaphore = DispatchSemaphore(value: 0)
        store.prepare(id: id, fileName: fileName) { value in result.value = value; semaphore.signal() }
        wait(semaphore)
        return result.value
    }

    private static func validate(_ store: BrowserDownloadFileStore, file: URL, maximumBytes: Int64) -> Bool {
        let result = ResultBox(false)
        let semaphore = DispatchSemaphore(value: 0)
        store.validate(file: file, maximumBytes: maximumBytes) { value in result.value = value; semaphore.signal() }
        wait(semaphore)
        return result.value
    }

    private static func wait(_ semaphore: DispatchSemaphore) {
        guard semaphore.wait(timeout: .now() + 5) == .success else { fatalError("Download storage callback timed out") }
    }

    private static func expect(_ condition: Bool, _ message: String) {
        if !condition { fatalError("Failed: \(message)") }
    }

    private final class ResultBox<T>: @unchecked Sendable {
        var value: T
        init(_ value: T) { self.value = value }
    }
}
