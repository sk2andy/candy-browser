import CandyShared
import UIKit
import WebKit

/// Apple presentation only. Android and WebKit use the same Kotlin prompt sanitizer.
@MainActor
final class BrowserWebPromptPresenter: NSObject, WKUIDelegate {
    private var pendingCompletion: ((String?) -> Void)?
    private weak var alert: UIAlertController?

    func cancel() {
        let currentAlert = alert
        finish(value: nil)
        currentAlert?.dismiss(animated: false)
    }

    func webView(
        _ webView: WKWebView,
        runJavaScriptAlertPanelWithMessage message: String,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping @MainActor @Sendable () -> Void
    ) {
        present(webView, frame: frame, kind: .alert, message: message) { _ in
            completionHandler()
        }
    }

    func webView(
        _ webView: WKWebView,
        runJavaScriptConfirmPanelWithMessage message: String,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping @MainActor @Sendable (Bool) -> Void
    ) {
        present(webView, frame: frame, kind: .confirm, message: message) { value in
            completionHandler(value != nil)
        }
    }

    func webView(
        _ webView: WKWebView,
        runJavaScriptTextInputPanelWithPrompt prompt: String,
        defaultText: String?,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping @MainActor @Sendable (String?) -> Void
    ) {
        present(
            webView, frame: frame, kind: .text, message: prompt,
            defaultText: defaultText, completion: completionHandler
        )
    }

    private func present(
        _ webView: WKWebView,
        frame: WKFrameInfo,
        kind: BrowserWebPromptKind,
        message: String,
        defaultText: String? = nil,
        completion: @escaping (String?) -> Void
    ) {
        guard
            pendingCompletion == nil,
            let root = webView.window?.rootViewController,
            root.presentedViewController == nil,
            let prompt = BrowserWebPromptRules.shared.sanitized(
                id: 1,
                tabId: "webkit",
                kind: kind,
                title: frame.request.url?.host,
                message: message,
                defaultValue: defaultText,
                choices: [],
                allowMultiple: false,
                shareUri: nil
            )
        else {
            completion(nil)
            return
        }
        let controller = UIAlertController(
            title: prompt.title ?? "Website",
            message: prompt.message,
            preferredStyle: .alert
        )
        if kind == .text {
            controller.addTextField { field in
                field.text = prompt.defaultValue
                field.autocorrectionType = .no
            }
        }
        if kind != .alert {
            controller.addAction(UIAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in
                self?.finish(value: nil)
            })
        }
        controller.addAction(UIAlertAction(title: "OK", style: .default) { [weak self, weak controller] _ in
            let value = kind == .text ? controller?.textFields?.first?.text ?? "" : ""
            // Kotlin strings count UTF-16 code units, as does NSString.
            let bounded = (value as NSString).substring(
                to: min((value as NSString).length, Int(BrowserWebPromptRules.shared.MAX_INPUT_LENGTH))
            )
            self?.finish(value: bounded)
        })
        pendingCompletion = completion
        alert = controller
        root.present(controller, animated: true)
    }

    private func finish(value: String?) {
        let completion = pendingCompletion
        pendingCompletion = nil
        alert = nil
        completion?(value)
    }
}
