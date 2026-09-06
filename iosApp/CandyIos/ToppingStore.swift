import CandyShared
import CryptoKit
import Foundation

struct StoredToppingRequire: Codable, Equatable {
    let url: String
    let sha256: String?
    let source: String
}

struct StoredToppingResource: Codable, Equatable {
    let name: String
    let url: String
    let sha256: String?
    let encodedContent: String
    let mimeType: String
}

struct StoredTopping: Codable, Equatable, Identifiable {
    let id: String
    let source: String
    var enabled: Bool
    let requires: [StoredToppingRequire]
    let resources: [StoredToppingResource]

    init(
        id: String,
        source: String,
        enabled: Bool,
        requires: [StoredToppingRequire] = [],
        resources: [StoredToppingResource] = []
    ) {
        self.id = id
        self.source = source
        self.enabled = enabled
        self.requires = requires
        self.resources = resources
    }

    private enum CodingKeys: String, CodingKey {
        case id, source, enabled, requires, resources
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(String.self, forKey: .id)
        source = try values.decode(String.self, forKey: .source)
        enabled = try values.decode(Bool.self, forKey: .enabled)
        requires = try values.decodeIfPresent([StoredToppingRequire].self, forKey: .requires) ?? []
        resources = try values.decodeIfPresent([StoredToppingResource].self, forKey: .resources) ?? []
    }
}

enum ToppingStoreError: LocalizedError, Equatable {
    case invalidScript(String)
    case unresolvedDependencies
    case tooManyScripts

    var errorDescription: String? {
        switch self {
        case let .invalidScript(reason): "Topping abgelehnt: \(reason)"
        case .unresolvedDependencies: "Topping-Abhängigkeiten müssen beim Import sicher aufgelöst werden."
        case .tooManyScripts: "Es können höchstens 128 Toppings gespeichert werden."
        }
    }
}

struct ToppingInstallation {
    let record: StoredTopping
    let script: ToppingScript
    let plan: ToppingInjectionPlan
}

@MainActor
final class ToppingStore {
    static let maximumScriptCount = 128

    private let preferences: UserDefaults
    private let storageKey: String
    private var cachedRecords: [StoredTopping]?

    init(preferences: UserDefaults = .standard, storageKey: String = "candy.ios.toppings.v2") {
        self.preferences = preferences
        self.storageKey = storageKey
    }

    func records() -> [StoredTopping] {
        if let cachedRecords {
            return cachedRecords
        }
        guard
            let data = preferences.data(forKey: storageKey),
            let decoded = try? JSONDecoder().decode([StoredTopping].self, from: data)
        else {
            return migrateVersionOneRecords()
        }
        let records = decoded.filter(isCanonical).sorted { $0.id < $1.id }
        cachedRecords = records
        return records
    }

    @discardableResult
    func save(id: String, source: String, enabled: Bool = true) throws -> StoredTopping {
        let script = try parse(id: id, source: source, enabled: enabled)
        guard script.requires.isEmpty && script.resources.isEmpty else {
            throw ToppingStoreError.unresolvedDependencies
        }
        return try save(
            StoredTopping(id: id, source: source, enabled: enabled),
            parsedScript: script
        )
    }

    @discardableResult
    func saveResolved(_ record: StoredTopping) throws -> StoredTopping {
        let script = try parse(id: record.id, source: record.source, enabled: record.enabled)
        guard dependenciesMatch(record: record, script: script) else {
            throw ToppingStoreError.unresolvedDependencies
        }
        return try save(record, parsedScript: script)
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

    func installations() -> [ToppingInstallation] {
        records().compactMap { record in
            guard
                record.enabled,
                let result = ToppingRules.shared.parse(
                    id: record.id,
                    source: record.source,
                    enabled: record.enabled
                ) as? ToppingParseResultAccepted,
                dependenciesMatch(record: record, script: result.script)
            else {
                return nil
            }
            return ToppingInstallation(
                record: record,
                script: result.script,
                plan: ToppingRules.shared.injectionPlan(script: result.script)
            )
        }.sorted { $0.record.id < $1.record.id }
    }

    private func save(_ record: StoredTopping, parsedScript: ToppingScript) throws -> StoredTopping {
        guard dependenciesMatch(record: record, script: parsedScript) else {
            throw ToppingStoreError.unresolvedDependencies
        }
        var current = records()
        if let index = current.firstIndex(where: { $0.id == record.id }) {
            current[index] = record
        } else {
            guard current.count < Self.maximumScriptCount else {
                throw ToppingStoreError.tooManyScripts
            }
            current.append(record)
        }
        try persist(current)
        return record
    }

    private func parse(id: String, source: String, enabled: Bool) throws -> ToppingScript {
        let parsed = ToppingRules.shared.parse(id: id, source: source, enabled: enabled)
        guard let accepted = parsed as? ToppingParseResultAccepted else {
            throw ToppingStoreError.invalidScript(
                (parsed as? ToppingParseResultRejected)?.reason ?? "invalid_script"
            )
        }
        return accepted.script
    }

    private func isCanonical(_ record: StoredTopping) -> Bool {
        guard
            let result = ToppingRules.shared.parse(
                id: record.id,
                source: record.source,
                enabled: record.enabled
            ) as? ToppingParseResultAccepted
        else {
            return false
        }
        return dependenciesMatch(record: record, script: result.script)
    }

    private func dependenciesMatch(record: StoredTopping, script: ToppingScript) -> Bool {
        let expectedRequires = script.requires.map { ($0.url, $0.sha256) }
        let actualRequires = record.requires.map { ($0.url, $0.sha256) }
        let expectedResources = script.resources.map { ($0.name, $0.url, $0.sha256) }
        let actualResources = record.resources.map { ($0.name, $0.url, $0.sha256) }
        guard expectedRequires.elementsEqual(actualRequires, by: { $0 == $1 }),
              expectedResources.elementsEqual(actualResources, by: { $0 == $1 }) else {
            return false
        }
        var totalBytes = 0
        for dependency in record.requires {
            guard let bytes = dependency.source.data(using: .utf8),
                  bytes.count <= 256 * 1_024,
                  Self.matchesIntegrity(bytes, expected: dependency.sha256) else {
                return false
            }
            totalBytes += bytes.count
        }
        for resource in record.resources {
            guard let bytes = Data(base64Encoded: resource.encodedContent),
                  bytes.count <= 512 * 1_024,
                  Self.matchesIntegrity(bytes, expected: resource.sha256),
                  Self.isCanonicalMimeType(resource.mimeType) else {
                return false
            }
            totalBytes += bytes.count
        }
        return totalBytes <= 2 * 1_024 * 1_024
    }

    private func persist(_ records: [StoredTopping]) throws {
        let sortedRecords = records.sorted { $0.id < $1.id }
        let data = try JSONEncoder().encode(sortedRecords)
        preferences.set(data, forKey: storageKey)
        cachedRecords = sortedRecords
    }

    private func migrateVersionOneRecords() -> [StoredTopping] {
        struct LegacyRecord: Codable { let id: String; let source: String; let enabled: Bool }
        guard
            let data = preferences.data(forKey: "candy.ios.toppings.v1"),
            let legacy = try? JSONDecoder().decode([LegacyRecord].self, from: data)
        else {
            cachedRecords = []
            return []
        }
        let migrated = legacy.map { StoredTopping(id: $0.id, source: $0.source, enabled: $0.enabled) }
            .filter(isCanonical)
        try? persist(migrated)
        return migrated.sorted { $0.id < $1.id }
    }

    private static func matchesIntegrity(_ data: Data, expected: String?) -> Bool {
        guard let expected else { return true }
        let actual = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        return actual == expected.lowercased()
    }

    private static func isCanonicalMimeType(_ value: String) -> Bool {
        value.range(
            of: #"^[a-z0-9][a-z0-9!#$&^_.+\-]*/[a-z0-9][a-z0-9!#$&^_.+\-]*$"#,
            options: .regularExpression
        ) != nil
    }
}
