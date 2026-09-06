import CandyShared
import Foundation
import SwiftArgon2

/**
 * Candy Sync recovery keys are protocol material, not a presentation preference.
 * Keep this adapter synchronous because the KMP repository owns its serial executor.
 */
final class IosSyncRecoveryKeyDeriver: NSObject, SyncRecoveryKeyDeriver {
    func derive(passphrase: KotlinByteArray, kdf: SyncRecoveryKdf) throws -> KotlinByteArray {
        guard
            kdf.memoryKiB == 65_536,
            kdf.iterations == 3,
            kdf.parallelism == 4
        else {
            throw IosSyncPlatformError.invalidRecoveryKdf
        }

        let password = passphrase.data
        let salt = try SyncBase64.shared.decode(
            value: kdf.salt,
            expectedBytes: KotlinInt(int: 16),
            maxBytes: 16
        ).data
        let parameters = Argon2Params(
            parallelism: UInt32(kdf.parallelism),
            tagLength: 32,
            memorySize: UInt32(kdf.memoryKiB),
            iterations: UInt32(kdf.iterations),
            variant: .argon2id
        )
        let argon2 = try Argon2(params: parameters)
        let output = try blocking { try await argon2.compute(password: password, salt: salt) }
        return KotlinByteArray(data: output)
    }

    private func blocking<T: Sendable>(_ operation: @escaping @Sendable () async throws -> T) throws -> T {
        let semaphore = DispatchSemaphore(value: 0)
        let result = LockedResult<T>()
        Task.detached {
            defer { semaphore.signal() }
            do {
                result.set(.success(try await operation()))
            } catch {
                result.set(.failure(error))
            }
        }
        semaphore.wait()
        return try result.get()
    }
}

private final class LockedResult<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Result<Value, Error>?

    func set(_ value: Result<Value, Error>) {
        lock.lock()
        self.value = value
        lock.unlock()
    }

    func get() throws -> Value {
        lock.lock()
        defer { lock.unlock() }
        guard let value else { throw IosSyncPlatformError.recoveryDerivationFailed }
        return try value.get()
    }
}

enum IosSyncPlatformError: LocalizedError {
    case invalidRecoveryKdf
    case recoveryDerivationFailed

    var errorDescription: String? {
        switch self {
        case .invalidRecoveryKdf: "Unsupported Candy Sync recovery KDF."
        case .recoveryDerivationFailed: "Candy Sync recovery key derivation failed."
        }
    }
}
