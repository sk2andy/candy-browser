import CandyShared
import CryptoKit
import Foundation
import Security

private enum IosSyncCryptoError: Error {
    case invalidInput
    case invalidUtf8
    case invalidKey
    case keychain(OSStatus)
}

final class IosSyncDispatch: NSObject, SyncDispatch {
    private let queue = DispatchQueue(label: "dev.sk2andy.candy.sync", qos: .utility)

    func dispatch(block: @escaping () -> Void) {
        queue.async(execute: block)
    }

    func dispatchAfter(seconds: Int64, block: @escaping () -> Void) {
        queue.asyncAfter(deadline: .now() + .seconds(Int(seconds)), execute: block)
    }
}

final class IosSyncSettingsStore: NSObject, SyncSettingsStore {
    private static let key = "ios.candy-sync.settings.v1"
    private let preferences: UserDefaults

    init(preferences: UserDefaults = .standard) {
        self.preferences = preferences
    }

    func load() -> SyncConnectionSettings? {
        guard let raw = preferences.string(forKey: Self.key) else { return nil }
        return try? SyncStateCodec.shared.decodeSettings(raw: raw)
    }

    func save(value_: SyncConnectionSettings) -> Bool {
        guard let raw = try? SyncStateCodec.shared.encodeSettings(value: value_) else {
            return false
        }
        preferences.set(raw, forKey: Self.key)
        return preferences.string(forKey: Self.key) == raw
    }

    func clear_() -> Bool {
        preferences.removeObject(forKey: Self.key)
        return preferences.object(forKey: Self.key) == nil
    }
}

final class IosSyncVaultStore: NSObject, SyncVaultStore {
    private let keychain: IosSyncKeychain

    init(keychain: IosSyncKeychain = IosSyncKeychain()) {
        self.keychain = keychain
    }

    func load() -> SyncVaultSecrets? {
        guard let data = try? keychain.read(account: "vault-v1") else { return nil }
        return try? SyncStateCodec.shared.decodeVault(raw: KotlinByteArray(data: data))
    }

    func save(value__: SyncVaultSecrets) -> Bool {
        guard let encoded = try? SyncStateCodec.shared.encodeVault(value: value__).data else {
            return false
        }
        return (try? keychain.write(encoded, account: "vault-v1")) != nil
    }

    func clear() {
        try? keychain.delete(account: "vault-v1")
    }
}

final class IosSyncCacheStore: NSObject, SyncCacheStore {
    private static let aad = Data("candy-sync/ios-cache/v1".utf8)
    private let keychain: IosSyncKeychain
    private let fileUrl: URL

    init(keychain: IosSyncKeychain = IosSyncKeychain()) {
        self.keychain = keychain
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("CandySync", isDirectory: true)
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        fileUrl = root.appendingPathComponent("cache-v1.bin")
    }

    func load() -> SyncCache {
        guard
            let envelope = try? Data(contentsOf: fileUrl, options: .mappedIfSafe),
            envelope.count <= 9 * 1_024 * 1_024,
            let key = try? cacheKey(),
            let plaintext = try? Self.open(envelope, key: key),
            let value = try? SyncStateCodec.shared.decodeCache(raw: KotlinByteArray(data: plaintext))
        else {
            return Self.empty
        }
        return value
    }

    func save(value: SyncCache) -> Bool {
        guard let plaintext = try? SyncStateCodec.shared.encodeCache(value: value).data else {
            return false
        }
        guard plaintext.count <= 8 * 1_024 * 1_024 else { return false }
        do {
            let envelope = try Self.seal(plaintext, key: cacheKey())
            try envelope.write(to: fileUrl, options: [.atomic, .completeFileProtection])
            return true
        } catch {
            return false
        }
    }

    func clear() {
        try? FileManager.default.removeItem(at: fileUrl)
    }

    private func cacheKey() throws -> SymmetricKey {
        if let stored = try keychain.read(account: "cache-key-v1") {
            guard stored.count == 32 else { throw IosSyncCryptoError.invalidKey }
            return SymmetricKey(data: stored)
        }
        let key = SymmetricKey(size: .bits256)
        let data = key.withUnsafeBytes { Data($0) }
        try keychain.write(data, account: "cache-key-v1")
        return key
    }

    private static func seal(_ plaintext: Data, key: SymmetricKey) throws -> Data {
        let sealed = try AES.GCM.seal(plaintext, using: key, authenticating: aad)
        guard let combined = sealed.combined else { throw IosSyncCryptoError.invalidInput }
        return combined
    }

    private static func open(_ envelope: Data, key: SymmetricKey) throws -> Data {
        try AES.GCM.open(AES.GCM.SealedBox(combined: envelope), using: key, authenticating: aad)
    }

    private static var empty: SyncCache {
        SyncCache(
            cursor: "",
            profiles: [:],
            pendingMutations: [],
            preparedWrites: [:],
            deltaCursor: "",
            preparedDeltas: [:]
        )
    }
}

final class IosSyncKeychain {
    private let service: String

    init(service: String = "dev.sk2andy.candy.sync") {
        self.service = service
    }

    func read(account: String) throws -> Data? {
        var query = baseQuery(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else {
            throw IosSyncCryptoError.keychain(status)
        }
        return data
    }

    func write(_ data: Data, account: String) throws {
        let query = baseQuery(account: account)
        let attributes: [String: Any] = [kSecValueData as String: data]
        let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecSuccess { return }
        guard updateStatus == errSecItemNotFound else {
            throw IosSyncCryptoError.keychain(updateStatus)
        }
        var insert = query
        insert[kSecValueData as String] = data
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let addStatus = SecItemAdd(insert as CFDictionary, nil)
        guard addStatus == errSecSuccess else { throw IosSyncCryptoError.keychain(addStatus) }
    }

    func delete(account: String) throws {
        let status = SecItemDelete(baseQuery(account: account) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw IosSyncCryptoError.keychain(status)
        }
    }

    private func baseQuery(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}

final class IosSyncCryptoProvider: NSObject, SyncCryptoProvider {
    func randomBytes(size: Int32) throws -> KotlinByteArray {
        guard size > 0, size <= 1_024 else { throw IosSyncCryptoError.invalidInput }
        var bytes = Data(count: Int(size))
        let status = bytes.withUnsafeMutableBytes { buffer in
            SecRandomCopyBytes(kSecRandomDefault, buffer.count, buffer.baseAddress!)
        }
        guard status == errSecSuccess else { throw IosSyncCryptoError.keychain(status) }
        return KotlinByteArray(data: bytes)
    }

    func doNewChangeId() -> String {
        UUID().uuidString.lowercased()
    }

    func nowIso8601() -> String {
        ISO8601DateFormatter().string(from: Date())
    }

    func generateDeviceIdentity() throws -> SyncDeviceIdentity {
        let key = P256.Signing.PrivateKey()
        let rawPrivate = key.rawRepresentation
        let x963Public = key.publicKey.x963Representation
        let spki = Data(hex: "3059301306072a8648ce3d020106082a8648ce3d030107034200") + x963Public
        let pkcs8 = Data(hex: "308187020100301306072a8648ce3d020106082a8648ce3d030107046d306b0201010420") +
            rawPrivate + Data(hex: "a144034200") + x963Public
        return SyncDeviceIdentity(
            privateKeyPkcs8: KotlinByteArray(data: pkcs8),
            publicKeySpki: KotlinByteArray(data: spki),
            fingerprint: try fingerprint(publicKeySpki: KotlinByteArray(data: spki))
        )
    }

    func fingerprint(publicKeySpki: KotlinByteArray) throws -> String {
        let data = publicKeySpki.data
        guard data.count == 91 else { throw IosSyncCryptoError.invalidInput }
        return SyncBase64.shared.encode(
            value: KotlinByteArray(data: Data(SHA256.hash(data: data)))
        )
    }

    func createRecoveryEnvelope(
        recoveryKey: KotlinByteArray,
        workspaceKey: KotlinByteArray,
        workspaceId: String
    ) throws -> SyncRecoveryEnvelope {
        let encrypted = try encrypt(
            key: recoveryKey.data,
            plaintext: workspaceKey.data,
            aad: Data("candy-sync/recovery-envelope/v1/\(workspaceId)".utf8)
        )
        return SyncRecoveryEnvelope(
            cryptoVersion: 1,
            nonce: encrypted.nonce,
            ciphertext: encrypted.ciphertext
        )
    }

    func unlockRecoveryEnvelope(
        recoveryKey: KotlinByteArray,
        envelope: SyncRecoveryEnvelope,
        workspaceId: String
    ) -> KotlinByteArray? {
        guard envelope.cryptoVersion == 1 else { return nil }
        guard let plaintext = try? decrypt(
            key: recoveryKey.data,
            encrypted: SyncEncryptedValue(nonce: envelope.nonce, ciphertext: envelope.ciphertext),
            aad: Data("candy-sync/recovery-envelope/v1/\(workspaceId)".utf8),
            maximum: 48
        ), plaintext.count == 32 else {
            return nil
        }
        return KotlinByteArray(data: plaintext)
    }

    func encryptDeviceName(
        workspaceKey: KotlinByteArray,
        workspaceId: String,
        fingerprint: String,
        name: String
    ) throws -> SyncEncryptedValue {
        guard !name.isEmpty, name.count <= 80 else { throw IosSyncCryptoError.invalidInput }
        return try encrypt(
            key: deriveDeviceKey(workspaceKey.data, workspaceId, "device-name", fingerprint),
            plaintext: Data(name.utf8),
            aad: jsonArray([.string("candy-sync-device-name"), .number(1), .string(workspaceId), .string(fingerprint)])
        )
    }

    func decryptDeviceName(
        workspaceKey: KotlinByteArray,
        workspaceId: String,
        fingerprint: String,
        encrypted: SyncEncryptedValue
    ) throws -> String {
        let plaintext = try decrypt(
            key: deriveDeviceKey(workspaceKey.data, workspaceId, "device-name", fingerprint),
            encrypted: encrypted,
            aad: jsonArray([.string("candy-sync-device-name"), .number(1), .string(workspaceId), .string(fingerprint)]),
            maximum: 4_096
        )
        guard let value = String(data: plaintext, encoding: .utf8), !value.isEmpty, value.count <= 80 else {
            throw IosSyncCryptoError.invalidUtf8
        }
        return value
    }

    func encryptDeviceIcon(
        workspaceKey: KotlinByteArray,
        workspaceId: String,
        fingerprint: String,
        descriptor: SyncDeviceIconDescriptor
    ) throws -> SyncEncryptedValue {
        let raw = try SyncProtocolCodec.shared.encodeDeviceIcon(value: descriptor)
        return try encrypt(
            key: deriveDeviceKey(workspaceKey.data, workspaceId, "device-icon", fingerprint),
            plaintext: Data(raw.utf8),
            aad: jsonArray([.string("candy-sync-device-icon"), .number(1), .string(workspaceId), .string(fingerprint)])
        )
    }

    func decryptDeviceIcon(
        workspaceKey: KotlinByteArray,
        workspaceId: String,
        fingerprint: String,
        encrypted: SyncEncryptedValue
    ) throws -> SyncDeviceIconDescriptor {
        let plaintext = try decrypt(
            key: deriveDeviceKey(workspaceKey.data, workspaceId, "device-icon", fingerprint),
            encrypted: encrypted,
            aad: jsonArray([.string("candy-sync-device-icon"), .number(1), .string(workspaceId), .string(fingerprint)]),
            maximum: 4_096
        )
        guard let raw = String(data: plaintext, encoding: .utf8) else { throw IosSyncCryptoError.invalidUtf8 }
        return try SyncProtocolCodec.shared.decodeDeviceIcon(raw: raw)
    }

    func encryptTabSnapshot(
        workspaceKey: KotlinByteArray,
        metadata: SyncEncryptedChange,
        snapshot: SyncTabSnapshot
    ) throws -> SyncEncryptedChange {
        guard metadata.revision == nil else { throw IosSyncCryptoError.invalidInput }
        let plaintext = Data(try SyncProtocolCodec.shared.encodeTabSnapshot(snapshot: snapshot).utf8)
        let encrypted = try encrypt(
            key: hkdf(workspaceKey.data, salt: Data(metadata.targetDeviceId.utf8), info: Data("candy-sync/v1/payload/tabs".utf8)),
            plaintext: plaintext,
            aad: changeAad(metadata)
        )
        return metadata.doCopy(
            changeId: metadata.changeId,
            writerDeviceId: metadata.writerDeviceId,
            targetDeviceId: metadata.targetDeviceId,
            baseRevision: metadata.baseRevision,
            revision: metadata.revision,
            nonce: encrypted.nonce,
            ciphertext: encrypted.ciphertext
        )
    }

    func decryptTabSnapshot(
        workspaceKey: KotlinByteArray,
        change: SyncEncryptedChange
    ) throws -> SyncTabSnapshot {
        let plaintext = try decrypt(
            key: hkdf(workspaceKey.data, salt: Data(change.targetDeviceId.utf8), info: Data("candy-sync/v1/payload/tabs".utf8)),
            encrypted: SyncEncryptedValue(nonce: change.nonce, ciphertext: change.ciphertext),
            aad: changeAad(change),
            maximum: 393_216
        )
        guard let raw = String(data: plaintext, encoding: .utf8) else { throw IosSyncCryptoError.invalidUtf8 }
        return try SyncProtocolCodec.shared.decodeTabSnapshot(raw: raw)
    }

    func encryptTabMutation(
        workspaceKey: KotlinByteArray,
        metadata: SyncEncryptedDelta,
        mutation: SyncPendingMutation
    ) throws -> SyncEncryptedDelta {
        guard metadata.revision == nil,
              metadata.mutationId == mutation.mutationId,
              metadata.targetDeviceId == mutation.targetDeviceId else {
            throw IosSyncCryptoError.invalidInput
        }
        let plaintext = Data(try SyncMutationCodec.shared.encode(value: mutation).utf8)
        let encrypted = try encrypt(
            key: deltaKey(workspaceKey.data, metadata.workspaceId, metadata.targetDeviceId),
            plaintext: plaintext,
            aad: deltaAad(metadata)
        )
        return metadata.doCopy(
            changeId: metadata.changeId,
            mutationId: metadata.mutationId,
            workspaceId: metadata.workspaceId,
            writerDeviceId: metadata.writerDeviceId,
            targetDeviceId: metadata.targetDeviceId,
            baseRevision: metadata.baseRevision,
            revision: metadata.revision,
            nonce: encrypted.nonce,
            ciphertext: encrypted.ciphertext
        )
    }

    func decryptTabMutation(
        workspaceKey: KotlinByteArray,
        change: SyncEncryptedDelta
    ) throws -> SyncPendingMutation {
        let plaintext = try decrypt(
            key: deltaKey(workspaceKey.data, change.workspaceId, change.targetDeviceId),
            encrypted: SyncEncryptedValue(nonce: change.nonce, ciphertext: change.ciphertext),
            aad: deltaAad(change),
            maximum: 196_624
        )
        guard let raw = String(data: plaintext, encoding: .utf8) else { throw IosSyncCryptoError.invalidUtf8 }
        let mutation = try SyncMutationCodec.shared.decode(raw: raw)
        guard mutation.mutationId == change.mutationId,
              mutation.targetDeviceId == change.targetDeviceId else {
            throw IosSyncCryptoError.invalidInput
        }
        return mutation
    }

    private func deriveDeviceKey(_ key: Data, _ workspaceId: String, _ purpose: String, _ fingerprint: String) throws -> Data {
        _ = try decodeBase64(fingerprint, expected: 32, maximum: 32)
        return hkdf(
            key,
            salt: Data(workspaceId.utf8),
            info: Data("candy-sync/v1/\(purpose)/\(fingerprint)".utf8)
        )
    }

    private func deltaKey(_ key: Data, _ workspaceId: String, _ targetDeviceId: String) -> Data {
        hkdf(
            key,
            salt: jsonArray([.string(workspaceId), .string(targetDeviceId)]),
            info: Data("candy-sync/v2/payload/tab-delta".utf8)
        )
    }

    private func hkdf(_ key: Data, salt: Data, info: Data) -> Data {
        let derived = HKDF<SHA256>.deriveKey(
            inputKeyMaterial: SymmetricKey(data: key),
            salt: salt,
            info: info,
            outputByteCount: 32
        )
        return derived.withUnsafeBytes { Data($0) }
    }

    private func encrypt(key: Data, plaintext: Data, aad: Data) throws -> SyncEncryptedValue {
        guard key.count == 32 else { throw IosSyncCryptoError.invalidKey }
        let nonceData = try randomBytes(size: 12).data
        let nonce = try AES.GCM.Nonce(data: nonceData)
        let sealed = try AES.GCM.seal(
            plaintext,
            using: SymmetricKey(data: key),
            nonce: nonce,
            authenticating: aad
        )
        return SyncEncryptedValue(
            nonce: SyncBase64.shared.encode(value: KotlinByteArray(data: nonceData)),
            ciphertext: SyncBase64.shared.encode(
                value: KotlinByteArray(data: sealed.ciphertext + sealed.tag)
            )
        )
    }

    private func decrypt(key: Data, encrypted: SyncEncryptedValue, aad: Data, maximum: Int) throws -> Data {
        guard key.count == 32,
              let nonce = try? decodeBase64(encrypted.nonce, expected: 12, maximum: 12),
              let combined = try? decodeBase64(encrypted.ciphertext, expected: nil, maximum: maximum),
              combined.count >= 16 else {
            throw IosSyncCryptoError.invalidInput
        }
        let ciphertext = combined.dropLast(16)
        let tag = combined.suffix(16)
        return try AES.GCM.open(
            AES.GCM.SealedBox(
                nonce: AES.GCM.Nonce(data: nonce),
                ciphertext: ciphertext,
                tag: tag
            ),
            using: SymmetricKey(data: key),
            authenticating: aad
        )
    }

    private func decodeBase64(_ value: String, expected: Int?, maximum: Int) throws -> Data {
        let expectedBytes = expected.map { KotlinInt(int: Int32($0)) }
        return try SyncBase64.shared.decode(
            value: value,
            expectedBytes: expectedBytes,
            maxBytes: Int32(maximum)
        ).data
    }

    private func changeAad(_ value: SyncEncryptedChange) -> Data {
        jsonArray([
            .string("candy-sync-change"), .number(1), .number(1), .number(1),
            .string(value.writerDeviceId), .string(value.changeId), .string("tabs"),
            .string(value.targetDeviceId), .string("snapshot"), .string(String(value.baseRevision)),
        ])
    }

    private func deltaAad(_ value: SyncEncryptedDelta) -> Data {
        jsonArray([
            .string("candy-sync-change"), .number(1), .number(1), .number(2),
            .string(value.workspaceId), .string(value.writerDeviceId), .string(value.changeId),
            .string(value.mutationId), .string("tabs"), .string(value.targetDeviceId),
            .string("delta"), .string(String(value.baseRevision)),
        ])
    }
}

private enum IosSyncJsonAtom {
    case string(String)
    case number(Int)
}

private func jsonArray(_ atoms: [IosSyncJsonAtom]) -> Data {
    let raw = atoms.map { atom -> String in
        switch atom {
        case let .string(value):
            let data = try! JSONEncoder().encode(value)
            return String(decoding: data, as: UTF8.self)
        case let .number(value):
            return String(value)
        }
    }.joined(separator: ",")
    return Data("[\(raw)]".utf8)
}

private extension Data {
    init(hex: String) {
        precondition(hex.count.isMultiple(of: 2))
        self.init()
        reserveCapacity(hex.count / 2)
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            append(UInt8(hex[index..<next], radix: 16)!)
            index = next
        }
    }
}
