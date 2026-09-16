import Foundation

enum StoredToppingFrameScope: String, Codable {
    case top
    case sameOrigin = "same-origin"
    case allMatching = "all-matching"

    func restricted(to maximum: StoredToppingFrameScope) -> StoredToppingFrameScope {
        rank <= maximum.rank ? self : maximum
    }

    func isWithin(_ maximum: StoredToppingFrameScope) -> Bool {
        restricted(to: maximum) == self
    }

    private var rank: Int {
        switch self {
        case .top: 0
        case .sameOrigin: 1
        case .allMatching: 2
        }
    }
}

struct ToppingFrameOrigin: Equatable {
    let scheme: String
    let host: String
    let port: Int

    init?(url: URL) {
        guard let scheme = url.scheme?.lowercased(),
              let host = url.host?.lowercased(),
              let port = url.port ?? Self.defaultPort(scheme: scheme),
              scheme == "http" || scheme == "https" else {
            return nil
        }
        self.scheme = scheme
        self.host = host
        self.port = port
    }

    init?(scheme: String, host: String, port: Int) {
        let normalizedScheme = scheme.lowercased()
        let normalizedHost = host.lowercased()
        guard !normalizedHost.isEmpty,
              normalizedScheme == "http" || normalizedScheme == "https" else {
            return nil
        }
        self.scheme = normalizedScheme
        self.host = normalizedHost
        self.port = port > 0 ? port : Self.defaultPort(scheme: normalizedScheme) ?? -1
        guard self.port > 0 else { return nil }
    }

    private static func defaultPort(scheme: String) -> Int? {
        switch scheme {
        case "http": 80
        case "https": 443
        default: nil
        }
    }
}

enum ToppingFrameAuthorizationRules {
    static func allows(
        scope: StoredToppingFrameScope,
        isMainFrame: Bool,
        frameOrigin: ToppingFrameOrigin,
        topOrigin: ToppingFrameOrigin?
    ) -> Bool {
        if isMainFrame { return true }
        switch scope {
        case .top:
            return false
        case .sameOrigin:
            return topOrigin == frameOrigin
        case .allMatching:
            return true
        }
    }
}

enum ToppingStoredScopeRules {
    static func scopeForSave(
        existing: StoredToppingFrameScope?,
        declared: StoredToppingFrameScope
    ) -> StoredToppingFrameScope {
        existing?.restricted(to: declared) ?? declared
    }
}
