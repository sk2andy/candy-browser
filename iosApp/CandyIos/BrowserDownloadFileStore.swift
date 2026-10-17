import Foundation

/// Temporary transfer files never enter app backups; every export/cancel removes its directory.
final class BrowserDownloadFileStore: @unchecked Sendable {
    private let root: URL
    private let queue = DispatchQueue(label: "dev.sk2andy.candy.download-files", qos: .utility)
    private var startupReady = false

    init(root: URL = FileManager.default.temporaryDirectory.appendingPathComponent("CandyDownloads-v1", isDirectory: true)) {
        self.root = root
        queue.async { [self] in
            if FileManager.default.fileExists(atPath: root.path) {
                do {
                    try FileManager.default.removeItem(at: root)
                } catch {
                    return
                }
            }
            startupReady = true
        }
    }

    func prepare(
        id: UUID,
        fileName: String,
        completion: @escaping @Sendable (URL?) -> Void
    ) {
        queue.async { [self] in
            let directory = root.appendingPathComponent(id.uuidString, isDirectory: true)
            let file = directory.appendingPathComponent(fileName, isDirectory: false)
            guard startupReady,
                  !FileManager.default.fileExists(atPath: file.path),
                  file.deletingLastPathComponent().standardizedFileURL == directory.standardizedFileURL else {
                completion(nil)
                return
            }
            do {
                try FileManager.default.createDirectory(
                    at: directory,
                    withIntermediateDirectories: true,
                    attributes: [.posixPermissions: 0o700]
                )
                var protectedRoot = root
                var resources = URLResourceValues()
                resources.isExcludedFromBackup = true
                try protectedRoot.setResourceValues(resources)
                #if os(iOS)
                try FileManager.default.setAttributes(
                    [.protectionKey: FileProtectionType.completeUnlessOpen], ofItemAtPath: directory.path
                )
                #endif
                completion(file)
            } catch {
                completion(nil)
            }
        }
    }

    func remove(id: UUID) {
        queue.async { [self] in
            try? FileManager.default.removeItem(at: root.appendingPathComponent(id.uuidString, isDirectory: true))
        }
    }

    func validate(file: URL, maximumBytes: Int64, completion: @escaping @Sendable (Bool) -> Void) {
        queue.async {
            let attributes = try? FileManager.default.attributesOfItem(atPath: file.path)
            let size = attributes?[.size] as? NSNumber
            completion(size.map { $0.int64Value <= maximumBytes } ?? false)
        }
    }
}
