import CandyShared
import UIKit
import WebKit

@MainActor
final class BrowserDownloadRuntime: NSObject, WKDownloadDelegate, UIDocumentPickerDelegate, UIAdaptivePresentationControllerDelegate {
    static let shared = BrowserDownloadRuntime()
    private static let maximumTransfers = 4
    private static let maximumBytes: Int64 = 1_024 * 1_024 * 1_024
    private let files = BrowserDownloadFileStore()
    private var transfers: [ObjectIdentifier: Transfer] = [:]
    private var exports: [ObjectIdentifier] = []
    private var currentExport: ObjectIdentifier?
    private weak var picker: UIDocumentPickerViewController?
    private var exportTask: Task<Void, Never>?

    func attach(
        _ download: WKDownload,
        sessionId: UUID,
        webView: WKWebView,
        onFailure: @escaping (String) -> Void
    ) {
        guard transfers.count < Self.maximumTransfers else {
            download.cancel { _ in }
            onFailure("Höchstens vier Downloads gleichzeitig möglich.")
            return
        }
        let identity = ObjectIdentifier(download)
        let transfer = Transfer(download: download, sessionId: sessionId, scene: webView.window?.windowScene, onFailure: onFailure)
        transfers[identity] = transfer
        download.delegate = self
        let token = transfer.token
        let maximumBytes = Self.maximumBytes
        transfer.observation = download.progress.observe(\.completedUnitCount, options: [.new]) { [weak self] progress, _ in
            let received = progress.completedUnitCount
            if received > maximumBytes {
                Task { @MainActor [weak self] in
                    guard let self, self.transfers[identity]?.token == token else { return }
                    self.cancel(identity, message: "Download überschreitet das Limit von 1 GB.")
                }
            }
        }
    }

    func cancel(sessionId: UUID) {
        let identities = transfers.filter { $0.value.sessionId == sessionId }.map(\.key)
        identities.forEach { cancel($0) }
    }

    func download(
        _ download: WKDownload,
        decideDestinationUsing response: URLResponse,
        suggestedFilename: String,
        completionHandler: @escaping @MainActor @Sendable (URL?) -> Void
    ) {
        let identity = ObjectIdentifier(download)
        guard let transfer = transfers[identity] else {
            completionHandler(nil)
            return
        }
        guard response.expectedContentLength <= Self.maximumBytes else {
            completionHandler(nil)
            cancel(identity, message: "Download überschreitet das Limit von 1 GB.")
            return
        }
        transfer.pendingDestination = completionHandler
        let fileName = DownloadFileNameRules.shared.fileName(
            candidate: suggestedFilename,
            mimeType: response.mimeType ?? "application/octet-stream"
        )
        let token = transfer.token
        files.prepare(id: token, fileName: fileName) { [weak self] destination in
            Task { @MainActor [weak self] in
                guard let self else { return }
                guard let transfer = self.transfers[identity], transfer.token == token else {
                    self.files.remove(id: token)
                    return
                }
                let completion = transfer.pendingDestination
                transfer.pendingDestination = nil
                transfer.file = destination
                completion?(destination)
                if destination == nil {
                    self.cancel(identity, message: "Download-Datei konnte nicht erstellt werden.")
                }
            }
        }
    }

    func downloadDidFinish(_ download: WKDownload) {
        let identity = ObjectIdentifier(download)
        guard let transfer = transfers[identity], let file = transfer.file else { return }
        transfer.observation = nil
        transfer.finished = true
        let token = transfer.token
        files.validate(file: file, maximumBytes: Self.maximumBytes) { [weak self] valid in
            Task { @MainActor [weak self] in
                guard let self, self.transfers[identity]?.token == token else { return }
                guard valid else {
                    self.cancel(identity, message: "Download-Datei ist nicht verfügbar oder zu groß.")
                    return
                }
                self.exports.append(identity)
                self.presentNextExport()
            }
        }
    }

    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        cancel(ObjectIdentifier(download), message: "Download fehlgeschlagen: \(error.localizedDescription)")
    }

    func download(
        _ download: WKDownload,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        decisionHandler: @escaping @MainActor @Sendable (WKDownload.RedirectPolicy) -> Void
    ) {
        let permitted: Bool
        if let address = request.url?.absoluteString {
            permitted = BrowserUrlRules.shared.normalizeHttpUrl(value: address) != nil
        } else {
            permitted = false
        }
        decisionHandler(permitted ? .allow : .cancel)
    }

    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        guard presentationController.presentedViewController === picker else { return }
        finishExport()
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        guard controller === picker else { return }
        finishExport()
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard controller === picker else { return }
        finishExport()
    }

    private func presentNextExport() {
        guard currentExport == nil, exportTask == nil, let identity = exports.first else { return }
        exportTask = Task { @MainActor [weak self] in
            for _ in 0..<50 {
                guard let self, !Task.isCancelled, let transfer = self.transfers[identity] else { return }
                if let window = transfer.scene?.windows.first(where: \.isKeyWindow),
                   var presenter = window.rootViewController {
                    while let presented = presenter.presentedViewController { presenter = presented }
                    if !presenter.isBeingDismissed && !presenter.isBeingPresented,
                       !(presenter is UIAlertController), let file = transfer.file {
                        let controller = UIDocumentPickerViewController(forExporting: [file], asCopy: true)
                        controller.delegate = self
                        controller.presentationController?.delegate = self
                        self.currentExport = identity
                        self.picker = controller
                        self.exports.removeAll { $0 == identity }
                        self.exportTask = nil
                        presenter.present(controller, animated: true)
                        return
                    }
                }
                try? await Task.sleep(nanoseconds: 200_000_000)
            }
            guard let self else { return }
            self.exportTask = nil
            self.cancel(identity, message: "Dateidialog konnte nicht geöffnet werden.")
        }
    }

    private func finishExport() {
        if let identity = currentExport { cancel(identity) }
    }

    private func cancel(_ identity: ObjectIdentifier, message: String? = nil) {
        guard let transfer = transfers.removeValue(forKey: identity) else { return }
        transfer.observation = nil
        transfer.pendingDestination?(nil)
        transfer.pendingDestination = nil
        transfer.download.delegate = nil
        if !transfer.finished { transfer.download.cancel { _ in } }
        if let message { transfer.onFailure(message) }
        files.remove(id: transfer.token)
        exports.removeAll { $0 == identity }
        exportTask?.cancel()
        exportTask = nil
        if currentExport == identity {
            let controller = picker
            currentExport = nil
            picker = nil
            controller?.delegate = nil
            controller?.presentationController?.delegate = nil
            controller?.dismiss(animated: true) { [weak self] in self?.presentNextExport() }
        } else {
            presentNextExport()
        }
    }

    private final class Transfer {
        let download: WKDownload
        let sessionId: UUID
        let token = UUID()
        weak var scene: UIWindowScene?
        let onFailure: (String) -> Void
        var file: URL?
        var observation: NSKeyValueObservation?
        var pendingDestination: (@MainActor @Sendable (URL?) -> Void)?
        var finished = false

        init(download: WKDownload, sessionId: UUID, scene: UIWindowScene?, onFailure: @escaping (String) -> Void) {
            self.download = download
            self.sessionId = sessionId
            self.scene = scene
            self.onFailure = onFailure
        }
    }
}
