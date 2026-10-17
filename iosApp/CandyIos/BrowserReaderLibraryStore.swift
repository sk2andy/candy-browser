import Foundation

/// Atomic Apple file I/O. Kotlin owns the Reader schema, validation and storage budget.
final class BrowserReaderLibraryStore: @unchecked Sendable {
    private let fileURL: URL
    private let queue = DispatchQueue(label: "dev.sk2andy.candy.reader-library", qos: .utility)
    private static let maximumBytes = 8 * 1_024 * 1_024

    init(fileURL: URL = BrowserReaderLibraryStore.defaultFileURL) {
        self.fileURL = fileURL
    }

    static var defaultFileURL: URL {
        BrowserLocalStateStore.defaultFileURL.deletingLastPathComponent()
            .appendingPathComponent("reader-library-v1.json")
    }

    func load(completion: @escaping @Sendable (String?) -> Void) {
        queue.async { [self] in
            guard
                let attributes = try? FileManager.default.attributesOfItem(atPath: fileURL.path),
                let size = attributes[.size] as? NSNumber,
                size.intValue <= Self.maximumBytes,
                let data = try? Data(contentsOf: fileURL),
                data.count <= Self.maximumBytes,
                let text = String(data: data, encoding: .utf8)
            else {
                completion(nil)
                return
            }
            completion(text)
        }
    }

    func save(_ text: String, completion: @escaping @Sendable (Bool) -> Void) {
        queue.async { [self] in
            let data = Data(text.utf8)
            guard data.count <= Self.maximumBytes else {
                completion(false)
                return
            }
            do {
                var directory = fileURL.deletingLastPathComponent()
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                var resources = URLResourceValues()
                resources.isExcludedFromBackup = true
                try directory.setResourceValues(resources)
                #if os(iOS)
                try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUnlessOpen])
                #else
                try data.write(to: fileURL, options: .atomic)
                #endif
                completion(true)
            } catch {
                completion(false)
            }
        }
    }
}
