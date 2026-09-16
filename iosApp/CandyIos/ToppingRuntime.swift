import CandyShared
import CryptoKit
import Foundation
import WebKit

struct ToppingMenuCommand: Equatable {
    let tabId: String
    let scriptId: String
    let scriptName: String
    let documentId: String
    let commandId: String
    let caption: String
}

enum ToppingSessionKind {
    case regular
    case privateBrowsing
    case preview
}

@MainActor
protocol ToppingRuntimeDelegate: AnyObject {
    func toppingRuntimeDidChangeCommands(tabId: String, commands: [ToppingMenuCommand])
    func toppingRuntimeOpenTab(sourceTabId: String, url: URL, active: Bool)
}

@MainActor
final class ToppingRuntime {
    static let shared = ToppingRuntime()

    weak var delegate: ToppingRuntimeDelegate?

    private let store: ToppingStore
    private let values: ToppingValueStore
    private let resolver: ToppingDependencyResolver
    private var registrations: [ToppingControllerRegistration] = []

    convenience init() {
        self.init(
            store: ToppingStore(),
            values: ToppingValueStore(),
            resolver: ToppingDependencyResolver()
        )
    }

    init(store: ToppingStore, values: ToppingValueStore, resolver: ToppingDependencyResolver) {
        self.store = store
        self.values = values
        self.resolver = resolver
    }

    func attach(
        _ controller: WKUserContentController,
        tabId: String,
        sessionKind: ToppingSessionKind
    ) {
        registrations.removeAll { $0.controller == nil || $0.controller === controller }
        let registration = ToppingControllerRegistration(
            controller: controller,
            tabId: tabId,
            sessionKind: sessionKind
        )
        registrations.append(registration)
        reconcile(registration)
    }

    func bind(_ webView: WKWebView, tabId: String) {
        registrations.first { $0.tabId == tabId && $0.controller === webView.configuration.userContentController }?
            .webView = webView
    }

    func navigationStarted(tabId: String) {
        guard let registration = registrations.first(where: { $0.tabId == tabId }) else { return }
        registration.navigationGeneration &+= 1
        registration.documents.removeAll()
        registration.menuCommands.removeAll()
        publishCommands(registration)
    }

    @discardableResult
    func save(id: String, source: String, enabled: Bool = true) throws -> StoredTopping {
        let record = try store.save(id: id, source: source, enabled: enabled)
        reconcileRegularControllers()
        return record
    }

    @discardableResult
    func resolveAndSave(id: String, source: String, enabled: Bool = true) async throws -> StoredTopping {
        let record = try await resolver.resolve(id: id, source: source, enabled: enabled)
        let saved = try store.saveResolved(record)
        reconcileRegularControllers()
        return saved
    }

    func setEnabled(_ enabled: Bool, id: String) throws {
        try store.setEnabled(enabled, id: id)
        reconcileRegularControllers()
    }

    func setAllowedFrameScope(_ scope: ToppingFrameScope, id: String) throws {
        try store.setAllowedFrameScope(scope, id: id)
        reconcileRegularControllers()
    }

    func delete(id: String) throws {
        try store.delete(id: id)
        values.deleteScript(id: id)
        reconcileRegularControllers()
    }

    func records() -> [StoredTopping] { store.records() }

    func commands(tabId: String) -> [ToppingMenuCommand] {
        registrations.first(where: { $0.tabId == tabId })?.menuCommands.values.map(\.command).sorted {
            if $0.scriptName == $1.scriptName {
                if $0.commandId == $1.commandId { return $0.documentId < $1.documentId }
                return $0.commandId < $1.commandId
            }
            return $0.scriptName < $1.scriptName
        } ?? []
    }

    func invoke(tabId: String, scriptId: String, documentId: String, commandId: String) {
        guard let documentUUID = UUID(uuidString: documentId) else { return }
        let routeKey = Self.commandRouteKey(documentId: documentUUID, commandId: commandId)
        guard
            let registration = registrations.first(where: {
                $0.tabId == tabId && $0.sessionKind == .regular
            }),
            let webView = registration.webView,
            let route = registration.menuCommands[routeKey],
            route.command.scriptId == scriptId,
            route.command.documentId == documentId.lowercased(),
            let document = registration.documents[route.documentId],
            document.scriptId == scriptId,
            document.navigationGeneration == registration.navigationGeneration,
            let installed = registration.installed[scriptId]
        else { return }
        let encodedDocumentId = Self.jsonString(route.documentId.uuidString.lowercased())
        let encodedCommandId = Self.jsonString(route.sourceCommandId)
        webView.evaluateJavaScript(
            "globalThis.\(installed.invocationFunction)(\(encodedDocumentId), \(encodedCommandId));",
            in: document.frameInfo,
            in: installed.world
        ) { [weak self, weak registration] result in
            guard case .failure = result, let registration else { return }
            self?.removeDocument(route.documentId, from: registration)
        }
    }

    fileprivate func receive(
        _ message: WKScriptMessage,
        tabId: String,
        scriptId: String,
        revision: String,
        reply: @escaping @MainActor @Sendable (Any?, String?) -> Void
    ) {
        guard
            let body = message.body as? [String: Any],
            let type = body["type"] as? String,
            let sourceURL = message.frameInfo.request.url,
            let registration = registrations.first(where: {
                $0.tabId == tabId && $0.sessionKind == .regular
            }),
            let webView = registration.webView,
            message.webView === webView,
            let installed = registration.installed[scriptId],
            installed.revision == revision,
            sourceURL.scheme == "http" || sourceURL.scheme == "https",
            ToppingRules.shared.matchesUrl(script: installed.installation.script, url: sourceURL.absoluteString),
            let frameOrigin = ToppingFrameOrigin(
                scheme: message.frameInfo.securityOrigin.protocol,
                host: message.frameInfo.securityOrigin.host,
                port: message.frameInfo.securityOrigin.port
            ),
            ToppingFrameAuthorizationRules.allows(
                scope: installed.installation.effectiveFrameScope,
                isMainFrame: message.frameInfo.isMainFrame,
                frameOrigin: frameOrigin,
                topOrigin: webView.url.flatMap { ToppingFrameOrigin(url: $0) }
            ),
            !message.frameInfo.isMainFrame || Self.sameDocument(sourceURL, webView.url),
            registration.rateWindow.accept()
        else {
            reply(nil, "Topping request rejected")
            return
        }
        if type == "authorize" {
            guard registration.documents.count < 256,
                  registration.documentCount(scriptId: scriptId) < 128,
                  let frameInfo = message.frameInfo.copy() as? WKFrameInfo else {
                reply(nil, "Too many Topping documents")
                return
            }
            let documentId = UUID()
            registration.documents[documentId] = ToppingDocumentRegistration(
                id: documentId,
                scriptId: scriptId,
                navigationGeneration: registration.navigationGeneration,
                frameInfo: frameInfo,
                frameURL: sourceURL,
                isMainFrame: message.frameInfo.isMainFrame
            )
            var response: [String: Any] = [
                "ok": true,
                "documentId": documentId.uuidString.lowercased(),
            ]
            if installed.hasValueGrant {
                response["valueSnapshot"] = ToppingValueRules.snapshot(
                    values.values(scriptId: scriptId)
                ) ?? "{}"
            }
            reply(response, nil)
            return
        }

        guard let documentIdValue = body["documentId"] as? String,
              let documentId = UUID(uuidString: documentIdValue),
              let document = registration.documents[documentId],
              document.scriptId == scriptId,
              document.navigationGeneration == registration.navigationGeneration,
              document.matches(frameInfo: message.frameInfo, sourceURL: sourceURL) else {
            reply(nil, "Stale Topping document")
            return
        }
        document.lastSeen = Date()
        switch type {
        case "set-value":
            guard installed.hasGrant(.setvalue),
                  let key = body["key"] as? String,
                  let value = body["value"] as? String,
                  let snapshot = try? values.set(value, key: key, scriptId: scriptId) else {
                reply(["ok": false], nil)
                return
            }
            reply(["ok": true, "snapshot": snapshot], nil)
            broadcastValues(snapshot, scriptId: scriptId, registration: registration)
        case "delete-value":
            guard installed.hasGrant(.deletevalue),
                  let key = body["key"] as? String,
                  let snapshot = try? values.delete(key: key, scriptId: scriptId) else {
                reply(["ok": false], nil)
                return
            }
            reply(["ok": true, "snapshot": snapshot], nil)
            broadcastValues(snapshot, scriptId: scriptId, registration: registration)
        case "register-menu":
            guard installed.hasGrant(.registermenucommand),
                  let commandId = Self.validCommandId(body["commandId"]),
                  let caption = Self.validCaption(body["caption"]),
                  document.commandIds[commandId] != nil || document.commandIds.count < 32,
                  document.commandIds[commandId] != nil || registration.commandCount(scriptId: scriptId) < 32,
                  registration.menuCommands.count < 128 else {
                reply(nil, "Invalid Topping menu command")
                return
            }
            let routeKey = Self.commandRouteKey(documentId: documentId, commandId: commandId)
            let scriptName = message.frameInfo.isMainFrame ? installed.installation.script.name :
                "\(installed.installation.script.name) · \(sourceURL.host ?? "Frame")"
            registration.menuCommands[routeKey] = ToppingCommandRegistration(
                documentId: documentId,
                sourceCommandId: commandId,
                command: ToppingMenuCommand(
                    tabId: tabId,
                    scriptId: scriptId,
                    scriptName: scriptName,
                    documentId: documentId.uuidString.lowercased(),
                    commandId: commandId,
                    caption: caption
                )
            )
            document.commandIds[commandId] = routeKey
            publishCommands(registration)
            reply(["ok": true], nil)
        case "unregister-menu":
            guard installed.hasGrant(.unregistermenucommand),
                  let commandId = Self.validCommandId(body["commandId"]) else {
                reply(nil, "Invalid Topping menu command")
                return
            }
            if let routeKey = document.commandIds.removeValue(forKey: commandId) {
                registration.menuCommands.removeValue(forKey: routeKey)
            }
            publishCommands(registration)
            reply(["ok": true], nil)
        case "open-tab":
            guard installed.hasGrant(.openintab),
                  registration.openTabRateWindow.accept(),
                  let rawURL = body["url"] as? String,
                  rawURL.utf8.count <= 8_192,
                  let url = URL(string: rawURL),
                  url.scheme == "http" || url.scheme == "https",
                  url.user == nil,
                  url.password == nil else {
                reply(nil, "Invalid Topping tab request")
                return
            }
            delegate?.toppingRuntimeOpenTab(
                sourceTabId: tabId,
                url: url,
                active: body["active"] as? Bool ?? true
            )
            reply(["ok": true], nil)
        case "dispose":
            removeDocument(documentId, from: registration)
            reply(["ok": true], nil)
        default:
            reply(nil, "Unknown Topping request")
        }
    }

    private func broadcastValues(
        _ snapshot: String,
        scriptId: String,
        registration: ToppingControllerRegistration
    ) {
        guard let webView = registration.webView,
              let installed = registration.installed[scriptId],
              installed.hasValueGrant else { return }
        let encodedSnapshot = Self.jsonString(snapshot)
        let documents = registration.documents.values.filter { $0.scriptId == scriptId }
        for document in documents {
            let encodedDocumentId = Self.jsonString(document.id.uuidString.lowercased())
            webView.evaluateJavaScript(
                "globalThis.\(installed.valueSyncFunction)(\(encodedDocumentId), \(encodedSnapshot));",
                in: document.frameInfo,
                in: installed.world
            ) { [weak self, weak registration] result in
                guard case .failure = result, let registration else { return }
                self?.removeDocument(document.id, from: registration)
            }
        }
    }

    private func removeDocument(
        _ documentId: UUID,
        from registration: ToppingControllerRegistration
    ) {
        guard let document = registration.documents.removeValue(forKey: documentId) else { return }
        let removedCommand = document.commandIds.values.reduce(false) { removed, routeKey in
            registration.menuCommands.removeValue(forKey: routeKey) != nil || removed
        }
        if removedCommand {
            publishCommands(registration)
        }
    }

    private func reconcileRegularControllers() {
        registrations.removeAll { $0.controller == nil }
        registrations.forEach(reconcile)
    }

    private func reconcile(_ registration: ToppingControllerRegistration) {
        guard let controller = registration.controller else { return }
        registration.handlers.forEach { handler in
            controller.removeScriptMessageHandler(forName: handler.name, contentWorld: handler.world)
        }
        registration.handlers.removeAll()
        registration.installed.removeAll()
        registration.documents.removeAll()
        registration.menuCommands.removeAll()
        publishCommands(registration)
        guard registration.sessionKind == .regular else { return }
        store.installations().forEach { installation in
            let revision = Self.revision(installation.record)
            let handlerName = ToppingInstaller.handlerName(scriptId: "\(installation.script.id)_\(revision)")
            let bridge = ToppingBridgeHandler(
                runtime: self,
                tabId: registration.tabId,
                scriptId: installation.script.id,
                revision: revision
            )
            let knownKey = "\(installation.script.id):\(revision)"
            let installedScript: ToppingInstalledScript
            if let known = registration.knownScripts[knownKey] {
                installedScript = known
            } else {
                installedScript = ToppingInstaller.install(
                    installation,
                    values: values.values(scriptId: installation.script.id),
                    handlerName: handlerName,
                    into: controller
                )
                registration.knownScripts[knownKey] = installedScript
            }
            controller.addScriptMessageHandler(bridge, contentWorld: installedScript.world, name: handlerName)
            registration.handlers.append((handlerName, installedScript.world, bridge))
            registration.installed[installation.script.id] = InstalledRegistration(
                installation: installation,
                revision: revision,
                script: installedScript
            )
        }
    }

    private func publishCommands(_ registration: ToppingControllerRegistration) {
        delegate?.toppingRuntimeDidChangeCommands(
            tabId: registration.tabId,
            commands: commands(tabId: registration.tabId)
        )
    }

    private static func revision(_ record: StoredTopping) -> String {
        let data = (try? JSONEncoder().encode(record)) ?? Data(record.id.utf8)
        return SHA256.hash(data: data).prefix(8).map { String(format: "%02x", $0) }.joined()
    }

    private static func validCommandId(_ value: Any?) -> String? {
        guard let value = value as? String,
              (1...128).contains(value.count),
              !value.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains) else {
            return nil
        }
        return value
    }

    private static func validCaption(_ value: Any?) -> String? {
        guard let value = (value as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              (1...120).contains(value.count),
              !value.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains) else {
            return nil
        }
        return value
    }

    private static func commandRouteKey(documentId: UUID, commandId: String) -> String {
        "\(documentId.uuidString.lowercased()):\(commandId)"
    }

    private static func sameDocument(_ frameURL: URL, _ webViewURL: URL?) -> Bool {
        guard let webViewURL else { return false }
        var frameComponents = URLComponents(url: frameURL, resolvingAgainstBaseURL: false)
        var webViewComponents = URLComponents(url: webViewURL, resolvingAgainstBaseURL: false)
        frameComponents?.fragment = nil
        webViewComponents?.fragment = nil
        return frameComponents?.url == webViewComponents?.url
    }

    private static func jsonString(_ value: String) -> String {
        let data = try? JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed])
        return data.flatMap { String(data: $0, encoding: .utf8) } ?? "\"\""
    }
}

@MainActor
private final class ToppingBridgeHandler: NSObject, WKScriptMessageHandlerWithReply {
    weak var runtime: ToppingRuntime?
    let tabId: String
    let scriptId: String
    let revision: String

    init(runtime: ToppingRuntime, tabId: String, scriptId: String, revision: String) {
        self.runtime = runtime
        self.tabId = tabId
        self.scriptId = scriptId
        self.revision = revision
    }

    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage,
        replyHandler: @escaping @MainActor @Sendable (Any?, String?) -> Void
    ) {
        Task { @MainActor [weak self] in
            guard let self, let runtime else {
                replyHandler(nil, "Topping runtime unavailable")
                return
            }
            runtime.receive(
                message,
                tabId: tabId,
                scriptId: scriptId,
                revision: revision,
                reply: replyHandler
            )
        }
    }
}

@MainActor
private final class ToppingControllerRegistration {
    weak var controller: WKUserContentController?
    weak var webView: WKWebView?
    let tabId: String
    let sessionKind: ToppingSessionKind
    var navigationGeneration: UInt64 = 0
    var handlers: [(name: String, world: WKContentWorld, owner: ToppingBridgeHandler)] = []
    var knownScripts: [String: ToppingInstalledScript] = [:]
    var installed: [String: InstalledRegistration] = [:]
    var documents: [UUID: ToppingDocumentRegistration] = [:]
    var menuCommands: [String: ToppingCommandRegistration] = [:]
    let rateWindow = ToppingRateWindow(maximum: 512, seconds: 1)
    let openTabRateWindow = ToppingRateWindow(maximum: 4, seconds: 10)

    init(controller: WKUserContentController, tabId: String, sessionKind: ToppingSessionKind) {
        self.controller = controller
        self.tabId = tabId
        self.sessionKind = sessionKind
    }

    func documentCount(scriptId: String) -> Int {
        documents.values.lazy.filter { $0.scriptId == scriptId }.count
    }

    func commandCount(scriptId: String) -> Int {
        menuCommands.values.lazy.filter { $0.command.scriptId == scriptId }.count
    }
}

@MainActor
private final class ToppingDocumentRegistration {
    let id: UUID
    let scriptId: String
    let navigationGeneration: UInt64
    var frameInfo: WKFrameInfo
    var frameURL: URL
    let isMainFrame: Bool
    var lastSeen = Date()
    var commandIds: [String: String] = [:]

    init(
        id: UUID,
        scriptId: String,
        navigationGeneration: UInt64,
        frameInfo: WKFrameInfo,
        frameURL: URL,
        isMainFrame: Bool
    ) {
        self.id = id
        self.scriptId = scriptId
        self.navigationGeneration = navigationGeneration
        self.frameInfo = frameInfo
        self.frameURL = frameURL
        self.isMainFrame = isMainFrame
    }

    func matches(frameInfo: WKFrameInfo, sourceURL: URL) -> Bool {
        guard isMainFrame == frameInfo.isMainFrame,
              Self.documentURL(frameURL) == Self.documentURL(sourceURL),
              let storedOrigin = ToppingFrameOrigin(url: frameURL),
              let messageOrigin = ToppingFrameOrigin(
                  scheme: frameInfo.securityOrigin.protocol,
                  host: frameInfo.securityOrigin.host,
                  port: frameInfo.securityOrigin.port
              ) else {
            return false
        }
        return storedOrigin == messageOrigin
    }

    private static func documentURL(_ url: URL) -> URL? {
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.fragment = nil
        return components?.url
    }
}

private struct ToppingCommandRegistration {
    let documentId: UUID
    let sourceCommandId: String
    let command: ToppingMenuCommand
}

private struct InstalledRegistration {
    let installation: ToppingInstallation
    let revision: String
    let script: ToppingInstalledScript

    var world: WKContentWorld { script.world }
    var invocationFunction: String { script.invocationFunction }
    var valueSyncFunction: String { script.valueSyncFunction }
    var hasValueGrant: Bool {
        installation.script.grants.contains(.getvalue) ||
            installation.script.grants.contains(.setvalue) ||
            installation.script.grants.contains(.deletevalue) ||
            installation.script.grants.contains(.listvalues)
    }
    func hasGrant(_ grant: ToppingGrant) -> Bool { installation.script.grants.contains(grant) }
}

private final class ToppingRateWindow {
    private let maximum: Int
    private let duration: TimeInterval
    private var startedAt = Date.distantPast
    private var count = 0

    init(maximum: Int, seconds: TimeInterval) {
        self.maximum = maximum
        duration = seconds
    }

    func accept(now: Date = Date()) -> Bool {
        if now.timeIntervalSince(startedAt) >= duration {
            startedAt = now
            count = 0
        }
        guard count < maximum else { return false }
        count += 1
        return true
    }
}

@MainActor
final class ToppingValueStore {
    private let preferences: UserDefaults
    private let storageKey: String

    init(preferences: UserDefaults = .standard, storageKey: String = "candy.ios.topping-values.v1") {
        self.preferences = preferences
        self.storageKey = storageKey
    }

    func values(scriptId: String) -> [String: String] { all()[scriptId] ?? [:] }

    func set(_ encodedValue: String, key: String, scriptId: String) throws -> String {
        guard ToppingValueRules.isValidKey(key),
              ToppingValueRules.isValidEncodedValue(encodedValue) else {
            throw ToppingValueError.invalidValue
        }
        var storage = all()
        var scriptValues = storage[scriptId] ?? [:]
        guard scriptValues[key] != nil || scriptValues.count < 128 else { throw ToppingValueError.full }
        scriptValues[key] = encodedValue
        guard let snapshot = ToppingValueRules.snapshot(scriptValues) else {
            throw ToppingValueError.full
        }
        storage[scriptId] = scriptValues
        try persist(storage)
        return snapshot
    }

    func delete(key: String, scriptId: String) throws -> String {
        guard ToppingValueRules.isValidKey(key) else { throw ToppingValueError.invalidValue }
        var storage = all()
        var scriptValues = storage[scriptId] ?? [:]
        scriptValues.removeValue(forKey: key)
        storage[scriptId] = scriptValues
        try persist(storage)
        return ToppingValueRules.snapshot(scriptValues) ?? "{}"
    }

    func deleteScript(id: String) {
        var storage = all()
        storage.removeValue(forKey: id)
        try? persist(storage)
    }

    private func all() -> [String: [String: String]] {
        guard let data = preferences.data(forKey: storageKey),
              let value = try? JSONDecoder().decode([String: [String: String]].self, from: data) else {
            return [:]
        }
        return value
    }

    private func persist(_ value: [String: [String: String]]) throws {
        preferences.set(try JSONEncoder().encode(value), forKey: storageKey)
    }

}

private enum ToppingValueError: Error { case invalidValue, full }
