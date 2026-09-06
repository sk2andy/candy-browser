import CandyShared
import Foundation
import WebKit

struct StoredTopping: Codable, Equatable, Identifiable {
    let id: String
    let source: String
    var enabled: Bool
}

enum ToppingStoreError: LocalizedError, Equatable {
    case invalidScript(String)
    case tooManyScripts

    var errorDescription: String? {
        switch self {
        case let .invalidScript(reason):
            "Topping abgelehnt: \(reason)"
        case .tooManyScripts:
            "Es können höchstens 128 Toppings gespeichert werden."
        }
    }
}

@MainActor
final class ToppingStore {
    static let maximumScriptCount = 128

    private let preferences: UserDefaults
    private let storageKey: String

    init(
        preferences: UserDefaults = .standard,
        storageKey: String = "candy.ios.toppings.v1"
    ) {
        self.preferences = preferences
        self.storageKey = storageKey
    }

    func records() -> [StoredTopping] {
        guard
            let data = preferences.data(forKey: storageKey),
            let decoded = try? JSONDecoder().decode([StoredTopping].self, from: data)
        else {
            return []
        }
        return decoded.filter { record in
            ToppingRules.shared.parse(
                id: record.id,
                source: record.source,
                enabled: record.enabled
            ) is ToppingParseResultAccepted
        }.sorted { $0.id < $1.id }
    }

    @discardableResult
    func save(id: String, source: String, enabled: Bool = true) throws -> StoredTopping {
        let parsed = ToppingRules.shared.parse(id: id, source: source, enabled: enabled)
        guard parsed is ToppingParseResultAccepted else {
            let reason = (parsed as? ToppingParseResultRejected)?.reason ?? "invalid_script"
            throw ToppingStoreError.invalidScript(reason)
        }
        var current = records()
        if let index = current.firstIndex(where: { $0.id == id }) {
            current[index] = StoredTopping(id: id, source: source, enabled: enabled)
        } else {
            guard current.count < Self.maximumScriptCount else {
                throw ToppingStoreError.tooManyScripts
            }
            current.append(StoredTopping(id: id, source: source, enabled: enabled))
        }
        try persist(current)
        return StoredTopping(id: id, source: source, enabled: enabled)
    }

    func setEnabled(_ enabled: Bool, id: String) throws {
        var current = records()
        guard let index = current.firstIndex(where: { $0.id == id }) else {
            throw ToppingStoreError.invalidScript("unknown_id")
        }
        current[index].enabled = enabled
        try persist(current)
    }

    func delete(id: String) throws {
        try persist(records().filter { $0.id != id })
    }

    func injectionPlans() -> [ToppingInjectionPlan] {
        let scripts = records().compactMap { record in
            (ToppingRules.shared.parse(
                id: record.id,
                source: record.source,
                enabled: record.enabled
            ) as? ToppingParseResultAccepted)?.script
        }
        return ToppingRules.shared.injectionPlans(scripts: scripts)
    }

    private func persist(_ records: [StoredTopping]) throws {
        let data = try JSONEncoder().encode(records.sorted { $0.id < $1.id })
        preferences.set(data, forKey: storageKey)
    }
}

@MainActor
final class ToppingRuntime {
    static let shared = ToppingRuntime()

    private let store: ToppingStore
    private var registrations: [ToppingControllerRegistration] = []

    init(store: ToppingStore = ToppingStore()) {
        self.store = store
    }

    func attach(_ controller: WKUserContentController, isPrivate: Bool) {
        registrations.removeAll { $0.controller == nil || $0.controller === controller }
        registrations.append(
            ToppingControllerRegistration(controller: controller, isPrivate: isPrivate)
        )
        reconcile(controller, isPrivate: isPrivate)
    }

    @discardableResult
    func save(id: String, source: String, enabled: Bool = true) throws -> StoredTopping {
        let record = try store.save(id: id, source: source, enabled: enabled)
        reconcileRegularControllers()
        return record
    }

    func setEnabled(_ enabled: Bool, id: String) throws {
        try store.setEnabled(enabled, id: id)
        reconcileRegularControllers()
    }

    func delete(id: String) throws {
        try store.delete(id: id)
        reconcileRegularControllers()
    }

    func records() -> [StoredTopping] {
        store.records()
    }

    private func reconcileRegularControllers() {
        registrations.removeAll { $0.controller == nil }
        registrations.forEach { registration in
            guard let controller = registration.controller else {
                return
            }
            reconcile(controller, isPrivate: registration.isPrivate)
        }
    }

    private func reconcile(_ controller: WKUserContentController, isPrivate: Bool) {
        controller.removeAllUserScripts()
        guard !isPrivate else {
            return
        }
        ToppingInstaller.install(store.injectionPlans(), into: controller)
    }
}

@MainActor
private final class ToppingControllerRegistration {
    weak var controller: WKUserContentController?
    let isPrivate: Bool

    init(controller: WKUserContentController, isPrivate: Bool) {
        self.controller = controller
        self.isPrivate = isPrivate
    }
}
