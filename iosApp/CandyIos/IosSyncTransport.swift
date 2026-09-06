import CandyShared
import Foundation

private enum IosSyncTransportError: Error {
    case invalidEndpoint
    case invalidResponse
    case oversizedResponse
    case unsupportedServer
}

private final class NoRedirectSessionDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        completionHandler(nil)
    }
}

private final class IosSyncRealtimeCallbacks: @unchecked Sendable {
    let event: (SyncRealtimeEvent) -> Void
    let closed: (KotlinThrowable?) -> Void

    init(event: @escaping (SyncRealtimeEvent) -> Void, closed: @escaping (KotlinThrowable?) -> Void) {
        self.event = event
        self.closed = closed
    }
}

private final class IosSyncRealtimeConnection: NSObject, KotlinAutoCloseable, @unchecked Sendable {
    private let task: URLSessionWebSocketTask
    private let session: URLSession
    private var receiveTask: Task<Void, Never>?

    init(
        url: URL,
        onEvent: @escaping (SyncRealtimeEvent) -> Void,
        onClosed: @escaping (KotlinThrowable?) -> Void
    ) {
        session = URLSession(configuration: .ephemeral)
        task = session.webSocketTask(with: url)
        super.init()
        task.resume()
        let callbacks = IosSyncRealtimeCallbacks(event: onEvent, closed: onClosed)
        receiveTask = Task { [weak self, callbacks] in
            guard let self else { return }
            do {
                while !Task.isCancelled {
                    let message = try await task.receive()
                    guard case let .string(raw) = message else {
                        task.cancel(with: .unsupportedData, reason: nil)
                        break
                    }
                    let event = try SyncProtocolCodec.shared.decodeRealtimeEvent(raw: raw)
                    await MainActor.run { callbacks.event(event) }
                }
                await MainActor.run { callbacks.closed(nil) }
            } catch {
                await MainActor.run { callbacks.closed(nil) }
            }
        }
    }

    func close() {
        receiveTask?.cancel()
        receiveTask = nil
        task.cancel(with: .goingAway, reason: nil)
        session.invalidateAndCancel()
    }
}

final class IosSyncTransport: NSObject, SyncTransport {
    private static let maximumResponseBytes = 1_048_576
    private let endpoint: URL
    private let session: URLSession
    private var tabMutationsV2 = false
    private var realtime = false
    private var remoteHttpApproved: Bool

    init(endpoint rawEndpoint: String) {
        let normalized = SyncEndpointRules.shared.normalize(
            value: rawEndpoint,
            allowRemoteHttp: true
        )
        guard let normalized, let endpoint = URL(string: normalized) else {
            preconditionFailure("Candy Sync endpoint must already be normalized")
        }
        self.endpoint = endpoint
        remoteHttpApproved = endpoint.scheme?.lowercased() != "http" || Self.isLoopback(endpoint.host)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 10
        configuration.timeoutIntervalForResource = 10
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        session = URLSession(
            configuration: configuration,
            delegate: NoRedirectSessionDelegate(),
            delegateQueue: nil
        )
        super.init()
    }

    func discover() throws {
        let data = try request(path: ".well-known/candy-sync", method: "GET")
        let root = try Self.object(data)
        try Self.requireExactKeys(root, ["protocol", "versions", "allowHttp", "features", "limits"])
        guard root["protocol"] as? String == "candy-sync",
              let versions = root["versions"] as? [Int],
              versions.count <= 16,
              versions.contains(1),
              let allowHttp = root["allowHttp"] as? Bool,
              let features = root["features"] as? [String],
              features.count <= 32,
              Set(features).isSuperset(of: ["e2ee", "tab-snapshots", "encrypted-device-icons"]),
              let limits = root["limits"] as? [String: Any]
        else {
            throw IosSyncTransportError.unsupportedServer
        }
        try Self.requireExactKeys(limits, ["batchChanges", "payloadBytes", "devices"])
        guard remoteHttpApproved || allowHttp else {
            throw IosSyncTransportError.unsupportedServer
        }
        tabMutationsV2 = versions.contains(2) && features.contains("tab-mutations-v2")
        realtime = tabMutationsV2 && features.contains("realtime")
        remoteHttpApproved = true
    }

    func bootstrap(username: String, password: KotlinByteArray) throws -> SyncBootstrap {
        let raw = try request(
            path: "v1/bootstrap",
            method: "GET",
            authorization: Self.basic(username: username, password: password.data)
        )
        return try SyncProtocolCodec.shared.decodeBootstrap(raw: Self.string(raw))
    }

    func enroll(
        username: String,
        password: KotlinByteArray,
        identity: SyncDeviceIdentity,
        encryptedName: SyncEncryptedValue,
        encryptedIcon: SyncEncryptedValue,
        recoveryEnvelope: SyncRecoveryEnvelope?
    ) throws -> SyncEnrollmentResponse {
        let body = try SyncProtocolCodec.shared.encodeEnrollment(
            identity: identity,
            name: encryptedName,
            icon: encryptedIcon,
            recovery: recoveryEnvelope
        )
        let value = try Self.object(try request(
            path: "v1/devices",
            method: "POST",
            authorization: Self.basic(username: username, password: password.data),
            body: Data(body.utf8)
        ))
        let allowed = Set(["workspaceId", "deviceId", "token", "cursor", "expiresAt"])
        guard Set(value.keys).isSubset(of: allowed),
              let workspaceId = value["workspaceId"] as? String,
              let deviceId = value["deviceId"] as? String,
              let token = value["token"] as? String,
              let cursor = value["cursor"] as? String
        else { throw IosSyncTransportError.invalidResponse }
        return SyncEnrollmentResponse(
            workspaceId: workspaceId,
            deviceId: deviceId,
            token: token,
            cursor: cursor
        )
    }

    func listDevices(token: String) throws -> [SyncDeviceRecord] {
        let raw = try request(path: "v1/devices", method: "GET", authorization: Self.bearer(token))
        return try SyncProtocolCodec.shared.decodeDevices(raw: Self.string(raw))
    }

    func pull(token: String, cursor: String) throws -> SyncPullPage {
        let query = cursor.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let raw = try request(
            path: "v1/sync/pull?after=\(query)&limit=100",
            method: "GET",
            authorization: Self.bearer(token)
        )
        return try SyncProtocolCodec.shared.decodePull(raw: Self.string(raw))
    }

    func snapshot(token: String) throws -> SyncServerSnapshot {
        let raw = try request(path: "v1/sync/snapshot", method: "GET", authorization: Self.bearer(token))
        return try SyncProtocolCodec.shared.decodeServerSnapshot(raw: Self.string(raw))
    }

    func putTabs(token: String, change: SyncEncryptedChange) throws -> SyncPutResponse {
        let body = try SyncProtocolCodec.shared.encodePutSnapshot(change: change)
        return try putResponse(try request(
            path: "v1/devices/\(change.targetDeviceId)/tabs",
            method: "PUT",
            authorization: Self.bearer(token),
            body: Data(body.utf8),
            idempotencyKey: change.changeId
        ))
    }

    func acknowledge(token: String, cursor: String) throws {
        let body = try JSONSerialization.data(withJSONObject: ["cursor": cursor], options: [.sortedKeys])
        _ = try request(
            path: "v1/sync/ack",
            method: "POST",
            authorization: Self.bearer(token),
            body: body,
            expectsBody: false
        )
    }

    func supportsTabMutationsV2() -> Bool { tabMutationsV2 }
    func supportsRealtime() -> Bool { realtime }

    func pullDeltas(token: String, cursor: String) throws -> SyncDeltaPullPage {
        let query = cursor.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let raw = try request(
            path: "v2/sync/pull?after=\(query)&limit=100",
            method: "GET",
            authorization: Self.bearer(token)
        )
        return try SyncProtocolCodec.shared.decodeDeltaPull(raw: Self.string(raw))
    }

    func pushDelta(token: String, change: SyncEncryptedDelta) throws -> SyncPutResponse {
        let encoded = try SyncProtocolCodec.shared.encodeDelta(change: change)
        let changeObject = try Self.object(Data(encoded.utf8))
        let body = try JSONSerialization.data(
            withJSONObject: ["changes": [changeObject]],
            options: [.sortedKeys]
        )
        let response = try Self.object(try request(
            path: "v2/sync/push",
            method: "POST",
            authorization: Self.bearer(token),
            body: body,
            idempotencyKey: change.changeId
        ))
        try Self.requireExactKeys(response, ["cursor", "results"])
        guard let cursor = response["cursor"] as? String,
              let results = response["results"] as? [[String: Any]],
              results.count == 1,
              let revision = Self.int64(results[0]["revision"]),
              results[0]["changeId"] as? String == change.changeId
        else { throw IosSyncTransportError.invalidResponse }
        return SyncPutResponse(revision: revision, cursor: cursor)
    }

    func requestRealtimeTicket(token: String) throws -> SyncRealtimeTicket {
        let value = try Self.object(try request(
            path: "v2/realtime/tickets",
            method: "POST",
            authorization: Self.bearer(token),
            body: Data("{}".utf8)
        ))
        try Self.requireExactKeys(value, ["ticket", "expiresAt"])
        guard let ticket = value["ticket"] as? String,
              let expiresAt = value["expiresAt"] as? String
        else { throw IosSyncTransportError.invalidResponse }
        return SyncRealtimeTicket(ticket: ticket, expiresAt: expiresAt)
    }

    func connectRealtime(
        ticket: SyncRealtimeTicket,
        onEvent: @escaping (SyncRealtimeEvent) -> Void,
        onClosed: @escaping (KotlinThrowable?) -> Void
    ) throws -> KotlinAutoCloseable {
        guard var components = URLComponents(url: endpoint, resolvingAgainstBaseURL: false) else {
            throw IosSyncTransportError.invalidEndpoint
        }
        components.scheme = endpoint.scheme == "https" ? "wss" : "ws"
        components.path = endpoint.appendingPathComponent("v2/realtime").path
        components.queryItems = [URLQueryItem(name: "ticket", value: ticket.ticket)]
        guard let url = components.url else { throw IosSyncTransportError.invalidEndpoint }
        return IosSyncRealtimeConnection(url: url, onEvent: onEvent, onClosed: onClosed)
    }

    private func putResponse(_ data: Data) throws -> SyncPutResponse {
        let value = try Self.object(data)
        try Self.requireExactKeys(value, ["revision", "cursor"])
        guard let revision = Self.int64(value["revision"]),
              let cursor = value["cursor"] as? String
        else { throw IosSyncTransportError.invalidResponse }
        return SyncPutResponse(revision: revision, cursor: cursor)
    }

    private func request(
        path: String,
        method: String,
        authorization: String? = nil,
        body: Data? = nil,
        idempotencyKey: String? = nil,
        expectsBody: Bool = true
    ) throws -> Data {
        guard remoteHttpApproved || path == ".well-known/candy-sync",
              let url = URL(string: path, relativeTo: endpoint)?.absoluteURL
        else { throw IosSyncTransportError.invalidEndpoint }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        if let authorization { request.setValue(authorization, forHTTPHeaderField: "Authorization") }
        if let idempotencyKey { request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key") }

        let semaphore = DispatchSemaphore(value: 0)
        let lock = NSLock()
        nonisolated(unsafe) var result: Result<(Data, URLResponse), Error>?
        let task = session.dataTask(with: request) { data, response, error in
            lock.lock()
            defer { lock.unlock(); semaphore.signal() }
            if let error { result = .failure(error); return }
            guard let data, let response else {
                result = .failure(IosSyncTransportError.invalidResponse)
                return
            }
            result = .success((data, response))
        }
        task.resume()
        guard semaphore.wait(timeout: .now() + 11) == .success else {
            task.cancel()
            throw URLError(.timedOut)
        }
        lock.lock()
        let resolved = result
        lock.unlock()
        let (data, response) = try resolved?.get() ?? { throw IosSyncTransportError.invalidResponse }()
        guard data.count <= Self.maximumResponseBytes else { throw IosSyncTransportError.oversizedResponse }
        guard let http = response as? HTTPURLResponse else { throw IosSyncTransportError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else {
            let problemCode = (try? Self.object(data)["code"] as? String) ?? nil
            throw SyncTransportException(
                statusCode: KotlinInt(int: Int32(http.statusCode)),
                problemCode: problemCode,
                cause: nil
            ).asError()
        }
        if expectsBody && data.isEmpty { throw IosSyncTransportError.invalidResponse }
        return data
    }

    private static func basic(username: String, password: Data) -> String {
        var credentials = Data(username.utf8)
        credentials.append(0x3A)
        credentials.append(password)
        return "Basic \(credentials.base64EncodedString())"
    }

    private static func bearer(_ token: String) -> String { "Bearer \(token)" }

    private static func object(_ data: Data) throws -> [String: Any] {
        guard data.count <= maximumResponseBytes,
              let value = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { throw IosSyncTransportError.invalidResponse }
        return value
    }

    private static func string(_ data: Data) throws -> String {
        guard let value = String(data: data, encoding: .utf8) else {
            throw IosSyncTransportError.invalidResponse
        }
        return value
    }

    private static func requireExactKeys(_ object: [String: Any], _ keys: Set<String>) throws {
        guard Set(object.keys) == keys else { throw IosSyncTransportError.invalidResponse }
    }

    private static func int64(_ value: Any?) -> Int64? {
        guard let number = value as? NSNumber,
              CFGetTypeID(number) != CFBooleanGetTypeID()
        else { return nil }
        return number.int64Value
    }

    private static func isLoopback(_ host: String?) -> Bool {
        guard let host = host?.lowercased() else { return false }
        return host == "localhost" || host == "127.0.0.1" || host == "::1"
    }
}

extension KotlinByteArray {
    convenience init(data: Data) {
        self.init(size: Int32(data.count))
        for (index, byte) in data.enumerated() {
            set(index: Int32(index), value: Int8(bitPattern: byte))
        }
    }

    var data: Data {
        Data((0..<Int(size)).map { UInt8(bitPattern: get(index: Int32($0))) })
    }
}
