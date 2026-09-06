import CandyShared
import CryptoKit
import Foundation
import WebKit

struct ToppingMenuCommand: Equatable {
    let tabId: String
    let scriptId: String
    let scriptName: String
    let commandId: String
    let caption: String
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

    func attach(_ controller: WKUserContentController, tabId: String, isPrivate: Bool) {
        registrations.removeAll { $0.controller == nil || $0.controller === controller }
        let registration = ToppingControllerRegistration(
            controller: controller,
            tabId: tabId,
            isPrivate: isPrivate
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
        registration.documentURL = nil
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

    func delete(id: String) throws {
        try store.delete(id: id)
        values.deleteScript(id: id)
        reconcileRegularControllers()
    }

    func records() -> [StoredTopping] { store.records() }

    func commands(tabId: String) -> [ToppingMenuCommand] {
        registrations.first(where: { $0.tabId == tabId })?.menuCommands.values.sorted {
            if $0.scriptName == $1.scriptName { return $0.commandId < $1.commandId }
            return $0.scriptName < $1.scriptName
        } ?? []
    }

    func invoke(tabId: String, scriptId: String, commandId: String) {
        guard
            let registration = registrations.first(where: { $0.tabId == tabId && !$0.isPrivate }),
            let webView = registration.webView,
            let installed = registration.installed[scriptId],
            registration.menuCommands["\(scriptId):\(commandId)"] != nil
        else { return }
        let encoded = Self.jsonString(commandId)
        webView.evaluateJavaScript(
            "globalThis.\(installed.invocationFunction)(\(encoded));",
            in: nil,
            in: installed.world
        ) { _ in }
    }

    fileprivate func receive(
        _ message: WKScriptMessage,
        tabId: String,
        scriptId: String,
        revision: String,
        reply: @escaping @MainActor @Sendable (Any?, String?) -> Void
    ) {
        guard
            message.frameInfo.isMainFrame,
            let sourceURL = message.frameInfo.request.url,
            let registration = registrations.first(where: { $0.tabId == tabId && !$0.isPrivate }),
            let installed = registration.installed[scriptId],
            installed.revision == revision,
            sourceURL.scheme == "http" || sourceURL.scheme == "https",
            ToppingRules.shared.matchesUrl(script: installed.installation.script, url: sourceURL.absoluteString),
            let body = message.body as? [String: Any],
            let type = body["type"] as? String,
            registration.rateWindow.accept()
        else {
            reply(nil, "Topping request rejected")
            return
        }
        if registration.documentURL != sourceURL.absoluteString {
            registration.documentURL = sourceURL.absoluteString
            registration.menuCommands = registration.menuCommands.filter { !$0.key.hasPrefix("\(scriptId):") }
            publishCommands(registration)
        }
        switch type {
        case "authorize":
            reply(["ok": true], nil)
        case "set-value":
            guard installed.hasGrant(.setvalue),
                  let key = body["key"] as? String,
                  let value = body["value"] as? String,
                  let snapshot = try? values.set(value, key: key, scriptId: scriptId) else {
                reply(["ok": false], nil)
                return
            }
            reply(["ok": true, "snapshot": snapshot], nil)
        case "delete-value":
            guard installed.hasGrant(.deletevalue),
                  let key = body["key"] as? String,
                  let snapshot = try? values.delete(key: key, scriptId: scriptId) else {
                reply(["ok": false], nil)
                return
            }
            reply(["ok": true, "snapshot": snapshot], nil)
        case "register-menu":
            guard installed.hasGrant(.registermenucommand),
                  let commandId = Self.validCommandId(body["commandId"]),
                  let caption = Self.validCaption(body["caption"]),
                  registration.commandCount(scriptId: scriptId) < 32,
                  registration.menuCommands.count < 128 else {
                reply(nil, "Invalid Topping menu command")
                return
            }
            registration.menuCommands["\(scriptId):\(commandId)"] = ToppingMenuCommand(
                tabId: tabId,
                scriptId: scriptId,
                scriptName: installed.installation.script.name,
                commandId: commandId,
                caption: caption
            )
            publishCommands(registration)
            reply(["ok": true], nil)
        case "unregister-menu":
            guard installed.hasGrant(.unregistermenucommand),
                  let commandId = Self.validCommandId(body["commandId"]) else {
                reply(nil, "Invalid Topping menu command")
                return
            }
            registration.menuCommands.removeValue(forKey: "\(scriptId):\(commandId)")
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
        default:
            reply(nil, "Unknown Topping request")
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
        registration.menuCommands.removeAll()
        publishCommands(registration)
        guard !registration.isPrivate else { return }
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
    let isPrivate: Bool
    var documentURL: String?
    var handlers: [(name: String, world: WKContentWorld, owner: ToppingBridgeHandler)] = []
    var knownScripts: [String: ToppingInstalledScript] = [:]
    var installed: [String: InstalledRegistration] = [:]
    var menuCommands: [String: ToppingMenuCommand] = [:]
    let rateWindow = ToppingRateWindow(maximum: 128, seconds: 1)
    let openTabRateWindow = ToppingRateWindow(maximum: 4, seconds: 10)

    init(controller: WKUserContentController, tabId: String, isPrivate: Bool) {
        self.controller = controller
        self.tabId = tabId
        self.isPrivate = isPrivate
    }

    func commandCount(scriptId: String) -> Int {
        menuCommands.keys.lazy.filter { $0.hasPrefix("\(scriptId):") }.count
    }
}

private struct InstalledRegistration {
    let installation: ToppingInstallation
    let revision: String
    let script: ToppingInstalledScript

    var world: WKContentWorld { script.world }
    var invocationFunction: String { script.invocationFunction }
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
