import Foundation

@main
enum BrowserReaderLibraryStoreTests {
    static func main() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("candy-reader-store-tests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let fileURL = directory.appendingPathComponent("library/reader.json")
        let store = BrowserReaderLibraryStore(fileURL: fileURL)
        expect(load(store) == nil, "missing library returns no payload")
        let source = "{\"version\":1,\"title\":\"日本語 Reader 📖\"}"
        expect(save(source, store), "new library directory and atomic file created")
        expect(load(BrowserReaderLibraryStore(fileURL: fileURL)) == source, "UTF-8 payload survives restart without native schema transformation")
        expect(save("replacement", store), "next write succeeds")
        expect(load(store) == "replacement", "latest payload replaces previous file")
        expect(!save(String(repeating: "x", count: 8 * 1_024 * 1_024 + 1), store), "oversized encoded library rejected")
        expect(load(store) == "replacement", "rejected write preserves committed payload")
        try Data(repeating: 32, count: 8 * 1_024 * 1_024 + 1).write(to: fileURL)
        expect(load(store) == nil, "oversized file rejected before decoding")
        try Data([0xff, 0xfe]).write(to: fileURL)
        expect(load(store) == nil, "invalid UTF-8 rejected")
        let blocker = directory.appendingPathComponent("directory-blocker")
        try Data().write(to: blocker)
        expect(!save(source, BrowserReaderLibraryStore(fileURL: blocker.appendingPathComponent("library.json"))), "write failure returned to native repository")
        print("BrowserReaderLibraryStoreTests: 10 passed")
    }

    private static func load(_ store: BrowserReaderLibraryStore) -> String? {
        let result = ResultBox<String?>(nil)
        let semaphore = DispatchSemaphore(value: 0)
        store.load { value in
            result.value = value
            semaphore.signal()
        }
        wait(semaphore)
        return result.value
    }

    private static func save(_ text: String, _ store: BrowserReaderLibraryStore) -> Bool {
        let result = ResultBox(false)
        let semaphore = DispatchSemaphore(value: 0)
        store.save(text) { value in
            result.value = value
            semaphore.signal()
        }
        wait(semaphore)
        return result.value
    }

    private static func wait(_ semaphore: DispatchSemaphore) {
        guard semaphore.wait(timeout: .now() + 5) == .success else {
            fatalError("Reader storage callback timed out")
        }
    }

    private static func expect(_ condition: Bool, _ message: String) {
        if !condition { fatalError("Failed: \(message)") }
    }

    private final class ResultBox<T>: @unchecked Sendable {
        var value: T
        init(_ value: T) { self.value = value }
    }
}
