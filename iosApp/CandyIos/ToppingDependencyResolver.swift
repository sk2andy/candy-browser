import CandyShared
import CryptoKit
import Darwin
import Foundation

enum ToppingDependencyError: LocalizedError, Equatable {
    case invalidDeclaration
    case untrustedURL(String)
    case network(String)
    case tooLarge(String)
    case invalidUTF8(String)
    case integrityMismatch(String)
    case totalTooLarge

    var errorDescription: String? {
        switch self {
        case .invalidDeclaration: "Ungültige Topping-Abhängigkeit."
        case let .untrustedURL(url): "Nicht erlaubte Topping-Abhängigkeit: \(url)"
        case let .network(url): "Topping-Abhängigkeit konnte nicht geladen werden: \(url)"
        case let .tooLarge(url): "Topping-Abhängigkeit ist zu groß: \(url)"
        case let .invalidUTF8(url): "@require ist kein gültiges UTF-8-JavaScript: \(url)"
        case let .integrityMismatch(url): "SHA-256 der Topping-Abhängigkeit stimmt nicht: \(url)"
        case .totalTooLarge: "Alle Topping-Abhängigkeiten zusammen sind zu groß."
        }
    }
}

/// Resolves dependencies only during an explicit import/update call.
actor ToppingDependencyResolver {
    func resolve(id: String, source: String, enabled: Bool = true) async throws -> StoredTopping {
        let parsed = ToppingRules.shared.parse(id: id, source: source, enabled: enabled)
        guard let accepted = parsed as? ToppingParseResultAccepted else {
            throw ToppingDependencyError.invalidDeclaration
        }
        var totalBytes = 0
        var requires: [StoredToppingRequire] = []
        for dependency in accepted.script.requires {
            let response = try await fetch(dependency.url, maximumBytes: Int(ToppingRules.shared.MAX_REQUIRE_BYTES))
            totalBytes += response.data.count
            guard totalBytes <= Int(ToppingRules.shared.MAX_TOTAL_DEPENDENCY_BYTES) else {
                throw ToppingDependencyError.totalTooLarge
            }
            try verify(response.data, sha256: dependency.sha256, url: dependency.url)
            guard let resolvedSource = String(data: response.data, encoding: .utf8),
                  resolvedSource.data(using: .utf8) == response.data else {
                throw ToppingDependencyError.invalidUTF8(dependency.url)
            }
            requires.append(
                StoredToppingRequire(
                    url: dependency.url,
                    sha256: dependency.sha256,
                    source: resolvedSource
                )
            )
        }
        var resources: [StoredToppingResource] = []
        for dependency in accepted.script.resources {
            let response = try await fetch(dependency.url, maximumBytes: Int(ToppingRules.shared.MAX_RESOURCE_BYTES))
            totalBytes += response.data.count
            guard totalBytes <= Int(ToppingRules.shared.MAX_TOTAL_DEPENDENCY_BYTES) else {
                throw ToppingDependencyError.totalTooLarge
            }
            try verify(response.data, sha256: dependency.sha256, url: dependency.url)
            resources.append(
                StoredToppingResource(
                    name: dependency.name,
                    url: dependency.url,
                    sha256: dependency.sha256,
                    encodedContent: response.data.base64EncodedString(),
                    mimeType: Self.normalizedMimeType(response.mimeType)
                )
            )
        }
        return StoredTopping(
            id: id,
            source: source,
            enabled: enabled,
            requires: requires,
            resources: resources
        )
    }

    private func fetch(_ rawURL: String, maximumBytes: Int) async throws -> FetchResponse {
        guard let url = URL(string: rawURL), ToppingDependencyPolicy.isAllowed(url) else {
            throw ToppingDependencyError.untrustedURL(rawURL)
        }
        guard ToppingDependencyPolicy.resolvesPublicly(url.host ?? "") else {
            throw ToppingDependencyError.untrustedURL(rawURL)
        }
        let delegate = ToppingRedirectDelegate()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        configuration.timeoutIntervalForRequest = 20
        configuration.httpMaximumConnectionsPerHost = 1
        let session = URLSession(configuration: configuration, delegate: delegate, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        do {
            let (stream, response) = try await session.bytes(from: url)
            guard let http = response as? HTTPURLResponse,
                  (200..<300).contains(http.statusCode),
                  delegate.wasValid,
                  ToppingDependencyPolicy.isAllowed(http.url),
                  ToppingDependencyPolicy.resolvesPublicly(http.url?.host ?? "") else {
                throw ToppingDependencyError.network(rawURL)
            }
            if let expectedLength = http.value(forHTTPHeaderField: "Content-Length").flatMap(Int.init),
               expectedLength > maximumBytes {
                throw ToppingDependencyError.tooLarge(rawURL)
            }
            var data = Data()
            data.reserveCapacity(min(maximumBytes, 64 * 1_024))
            for try await byte in stream {
                guard data.count < maximumBytes else {
                    throw ToppingDependencyError.tooLarge(rawURL)
                }
                data.append(byte)
            }
            return FetchResponse(data: data, mimeType: http.mimeType)
        } catch let error as ToppingDependencyError {
            throw error
        } catch {
            throw ToppingDependencyError.network(rawURL)
        }
    }

    private func verify(_ data: Data, sha256: String?, url: String) throws {
        guard let sha256 else { return }
        let actual = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        guard actual == sha256.lowercased() else {
            throw ToppingDependencyError.integrityMismatch(url)
        }
    }

    private static func normalizedMimeType(_ value: String?) -> String {
        guard let value = value?.split(separator: ";", maxSplits: 1).first?
            .trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              value.range(of: #"^[a-z0-9][a-z0-9!#$&^_.+\-]*/[a-z0-9][a-z0-9!#$&^_.+\-]*$"#,
                          options: .regularExpression) != nil else {
            return "application/octet-stream"
        }
        return value
    }
}

private struct FetchResponse { let data: Data; let mimeType: String? }

private final class ToppingRedirectDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    private(set) var wasValid = true
    private var redirectCount = 0

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        redirectCount += 1
        let allowed = redirectCount <= 3 && ToppingDependencyPolicy.isAllowed(request.url) &&
            ToppingDependencyPolicy.resolvesPublicly(request.url?.host ?? "")
        wasValid = wasValid && allowed
        completionHandler(allowed ? request : nil)
    }
}

enum ToppingDependencyPolicy {
    static func isAllowed(_ url: URL?) -> Bool {
        guard let url,
              url.scheme?.lowercased() == "https",
              url.user == nil,
              url.password == nil,
              url.port == nil || url.port == 443,
              let host = url.host?.lowercased(),
              ToppingRules.shared.isTrustedDependencyHost(host: host) else {
            return false
        }
        return !host.hasSuffix(".local") && host != "localhost"
    }

    static func resolvesPublicly(_ host: String) -> Bool {
        var hints = addrinfo()
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_STREAM
        var results: UnsafeMutablePointer<addrinfo>?
        guard getaddrinfo(host, nil, &hints, &results) == 0, let first = results else {
            return false
        }
        defer { freeaddrinfo(results) }
        var cursor: UnsafeMutablePointer<addrinfo>? = first
        var found = false
        while let item = cursor {
            let address = item.pointee.ai_addr
            if item.pointee.ai_family == AF_INET, let address {
                found = true
                let ipv4 = address.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee }
                if isPrivateIPv4(UInt32(bigEndian: ipv4.sin_addr.s_addr)) { return false }
            } else if item.pointee.ai_family == AF_INET6, let address {
                found = true
                let ipv6 = address.withMemoryRebound(to: sockaddr_in6.self, capacity: 1) { $0.pointee }
                let bytes = withUnsafeBytes(of: ipv6.sin6_addr) { Array($0) }
                if isPrivateIPv6(bytes) { return false }
            }
            cursor = item.pointee.ai_next
        }
        return found
    }

    private static func isPrivateIPv4(_ value: UInt32) -> Bool {
        let first = UInt8((value >> 24) & 0xff)
        let second = UInt8((value >> 16) & 0xff)
        return first == 0 || first == 10 || first == 127 || first >= 224 ||
            (first == 169 && second == 254) || (first == 172 && second >= 16 && second <= 31) ||
            (first == 192 && second == 168) || (first == 100 && second >= 64 && second <= 127)
    }

    private static func isPrivateIPv6(_ bytes: [UInt8]) -> Bool {
        guard bytes.count == 16 else { return true }
        let loopback = bytes.dropLast().allSatisfy { $0 == 0 } && bytes.last == 1
        let unspecified = bytes.allSatisfy { $0 == 0 }
        let uniqueLocal = bytes[0] & 0xfe == 0xfc
        let linkLocal = bytes[0] == 0xfe && bytes[1] & 0xc0 == 0x80
        return loopback || unspecified || uniqueLocal || linkLocal || bytes[0] == 0xff
    }
}
