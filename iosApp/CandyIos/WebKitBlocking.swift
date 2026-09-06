import Foundation
import OSLog
import WebKit

struct CandyBlockingCompilation: Sendable {
    let identifier: String
    let json: String
    let sourceRuleCount: Int
    let webKitRuleCount: Int
    let skippedRuleCount: Int
}

struct CandyBlockingActivationReport: Sendable {
    let sourceRuleCount: Int
    let webKitRuleCount: Int
    let skippedRuleCount: Int
}

enum CandyBlockingError: LocalizedError, Sendable {
    case missingResource(String)
    case unreadableResource(String)
    case invalidRuleList
    case compilationFailed(String, String)

    var errorDescription: String? {
        switch self {
        case let .missingResource(name):
            "Blocking-Ressource fehlt: \(name)"
        case let .unreadableResource(name):
            "Blocking-Ressource ist nicht lesbar: \(name)"
        case .invalidRuleList:
            "WebKit-Blocking-Regeln konnten nicht serialisiert werden."
        case let .compilationFailed(identifier, message):
            "WebKit-Blocking \(identifier) ist ungültig: \(message)"
        }
    }
}

enum CandyContentBlockerCompiler {
    // WKContentRuleList rejects regular-expression disjunctions and caps one list at 50k rules.
    // Keep room for scoped block/allow rules which must live in the same list as the host rule
    // they can override.
    private static let networkHostChunkSize = 45_000
    // WebKit applies the same 50k ceiling to cosmetic rules. Keep these lists smaller because
    // selectors and domain scopes make their encoded representation substantially larger than
    // a plain host rule list.
    private static let cosmeticChunkSize = 15_000

    static let hostResourceNames = [
        "blocked_hosts",
        "easylist_blocked_hosts",
        "hagezi_blocked_hosts",
        "uassets_blocked_hosts",
    ]

    static func compile(bundle: Bundle, pausedDomains: Set<String>) throws -> [CandyBlockingCompilation] {
        let paused = pausedDomains.compactMap(canonicalHost).sorted()
        let networkTexts = try hostResourceNames.map { name in
            try resourceText(name: name, extension: "txt", bundle: bundle)
        }
        let allowedPairs = try parsePairs(
            resourceText(name: "easylist_allowed_host_pairs", extension: "txt", bundle: bundle)
        ) + parsePairs(
            resourceText(name: "uassets_allowed_host_pairs", extension: "txt", bundle: bundle)
        )
        let blockedPairs = try parsePairs(
            resourceText(name: "uassets_blocked_host_pairs", extension: "txt", bundle: bundle)
        )
        let hostInputs = networkTexts.flatMap(parseHosts)
        let canonicalHosts = Array(Set(hostInputs.compactMap(canonicalHost))).sorted()
        let hostChunks = canonicalHosts.chunked(size: networkHostChunkSize)

        let easyCosmetic = try parseCosmetic(
            resourceText(name: "easylist_cosmetic_rules", extension: "txt", bundle: bundle),
            pausedDomains: paused
        )
        let uAssetsCosmetic = try parseCosmetic(
            resourceText(name: "uassets_cosmetic_rules", extension: "txt", bundle: bundle),
            pausedDomains: paused
        )
        let candyCosmetic = try parseCandyCosmetic(
            resourceText(name: "candy_default_rules", extension: "txt", bundle: bundle),
            pausedDomains: paused
        )
        let cookieCosmetic = try parseCookieSelectors(
            resourceText(name: "easylist_cookie_banner", extension: "css", bundle: bundle) + "\n" +
                resourceText(name: "cookie_banner_overrides", extension: "css", bundle: bundle),
            pausedDomains: paused
        )
        let cosmeticRules = easyCosmetic.rules + uAssetsCosmetic.rules + candyCosmetic.rules +
            cookieCosmetic.rules
        let sourceCosmeticCount = easyCosmetic.sourceCount + uAssetsCosmetic.sourceCount +
            candyCosmetic.sourceCount + cookieCosmetic.sourceCount
        let skippedCosmeticCount = easyCosmetic.skippedCount + uAssetsCosmetic.skippedCount +
            candyCosmetic.skippedCount + cookieCosmetic.skippedCount

        var compilations = try hostChunks.enumerated().map { part, hosts in
            var rules = hosts.map { host in
                return rule(
                    trigger: networkTrigger(host: host, unlessDomains: paused),
                    action: ["type": "block"]
                )
            }
            rules += blockedPairs.compactMap { pair in
                guard !paused.contains(where: { hostsOverlap($0, pair.page) }) else {
                    return nil
                }
                return rule(
                    trigger: scopedNetworkTrigger(pair: pair),
                    action: ["type": "block"]
                )
            }
            // An exception only affects preceding rules in its own WKContentRuleList. Repeating
            // these small rules in every part preserves EasyList/uAssets pair semantics.
            rules += allowedPairs.map { pair in
                rule(
                    trigger: scopedNetworkTrigger(pair: pair),
                    action: ["type": "ignore-previous-rules"]
                )
            }
            return try compilation(
                identifier: identifier(prefix: "network.part\(part)", pausedDomains: paused),
                rules: rules,
                sourceCount: hosts.count + (part == 0 ? blockedPairs.count + allowedPairs.count : 0),
                skippedCount: part == 0 ? hostInputs.count - canonicalHosts.count : 0
            )
        }
        let cosmeticChunks = cosmeticRules.chunked(size: cosmeticChunkSize)
        let nonEmittedCosmeticSources = max(0, sourceCosmeticCount - cosmeticRules.count)
        compilations += try cosmeticChunks.enumerated().map { part, rules in
            try compilation(
                identifier: identifier(prefix: "cosmetic.part\(part)", pausedDomains: paused),
                rules: rules,
                sourceCount: rules.count + (part == 0 ? nonEmittedCosmeticSources : 0),
                skippedCount: part == 0 ? skippedCosmeticCount : 0
            )
        }
        return compilations
    }

    static func parseHosts(_ text: String) -> [String] {
        text.lineSequence.compactMap { rawLine in
            let value = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            return value.isEmpty || value.hasPrefix("#") ? nil : value
        }
    }

    static func parsePairs(_ text: String) -> [(request: String, page: String)] {
        text.lineSequence.compactMap { rawLine in
            let value = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty, !value.hasPrefix("#") else {
                return nil
            }
            let fields = value.split(separator: "\t", omittingEmptySubsequences: false)
            guard
                fields.count == 2,
                let request = canonicalHost(String(fields[0])),
                let page = canonicalHost(String(fields[1]))
            else {
                return nil
            }
            return (request, page)
        }
    }

    private static func parseCosmetic(
        _ text: String,
        pausedDomains: [String]
    ) throws -> ParsedRules {
        var records: [CosmeticRecord] = []
        var sourceCount = 0
        var skippedCount = 0
        for line in text.lineSequence {
            let fields = line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
            guard fields.count == 4, ["H", "A", "D"].contains(fields[0]) else {
                continue
            }
            sourceCount += 1
            let host = fields[1]
            let excluded = fields[2] == "-" ? [] : fields[2].split(separator: ",").map(String.init)
            if fields[0] == "D" {
                records.append(CosmeticRecord(action: "D", host: host, exclusions: excluded, selector: ""))
                continue
            }
            guard
                let data = Data(base64URLEncoded: fields[3]),
                let selector = String(data: data, encoding: .utf8),
                isSafeSelector(selector)
            else {
                skippedCount += 1
                continue
            }
            records.append(
                CosmeticRecord(
                    action: fields[0],
                    host: host,
                    exclusions: excluded,
                    selector: selector
                )
            )
        }
        let globalAllowedSelectors = Set(records.lazy.filter { $0.action == "A" && $0.host == "*" }.map(\.selector))
        let allowedHosts = Dictionary(grouping: records.filter { $0.action == "A" && $0.host != "*" }, by: \.selector)
        let genericDisabledHosts = records.filter { $0.action == "D" }.map(\.host)
        var rules: [[String: Any]] = []
        for record in records where record.action == "H" {
            guard !globalAllowedSelectors.contains(record.selector) else {
                skippedCount += 1
                continue
            }
            let ifDomains: [String]
            if record.host == "*" {
                ifDomains = []
            } else if let host = webKitDomain(record.host) {
                ifDomains = [host]
            } else {
                skippedCount += 1
                continue
            }
            var unlessDomains = pausedDomains.compactMap(webKitDomain)
            let exceptionHosts = pausedDomains + record.exclusions +
                allowedHosts[record.selector].orEmpty.map(\.host)
            if record.host == "*" {
                unlessDomains = (exceptionHosts + genericDisabledHosts).compactMap(webKitDomain)
            } else if exceptionHosts.contains(where: { hostsOverlap($0, record.host) }) {
                // WebKit rejects a trigger containing both if-domain and unless-domain. If an
                // exception overlaps this scoped rule, omit the whole scoped cosmetic rule so an
                // allow/pause decision can never be weakened.
                skippedCount += 1
                continue
            } else {
                // Exceptions for unrelated hosts cannot intersect this if-domain and are safely
                // discarded. Keeping them would make the WebKit trigger invalid.
                unlessDomains = []
            }
            var trigger: [String: Any] = ["url-filter": ".*"]
            if !ifDomains.isEmpty {
                trigger["if-domain"] = Array(Set(ifDomains)).sorted()
            }
            if !unlessDomains.isEmpty {
                trigger["unless-domain"] = Array(Set(unlessDomains)).sorted()
            }
            rules.append(
                rule(
                    trigger: trigger,
                    action: ["type": "css-display-none", "selector": record.selector]
                )
            )
        }
        return ParsedRules(
            rules: rules,
            sourceCount: sourceCount,
            skippedCount: skippedCount
        )
    }

    private static func parseCandyCosmetic(
        _ text: String,
        pausedDomains: [String]
    ) throws -> ParsedRules {
        var rules: [[String: Any]] = []
        var sourceCount = 0
        var skippedCount = 0
        for line in text.lineSequence where line.hasPrefix("rule\tcss\torigin\t") {
            sourceCount += 1
            let fields = line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
            guard
                fields.count >= 6,
                let rawDomain = canonicalHost(fields[3]),
                let domain = webKitDomain(fields[3]),
                let data = Data(base64URLEncoded: fields[4]),
                let selector = String(data: data, encoding: .utf8),
                isSafeSelector(selector)
            else {
                skippedCount += 1
                continue
            }
            if pausedDomains.contains(where: { hostsOverlap($0, rawDomain) }) {
                skippedCount += 1
                continue
            }
            let trigger: [String: Any] = ["url-filter": ".*", "if-domain": [domain]]
            rules.append(rule(trigger: trigger, action: ["type": "css-display-none", "selector": selector]))
        }
        return ParsedRules(rules: rules, sourceCount: sourceCount, skippedCount: skippedCount)
    }

    private static func parseCookieSelectors(
        _ text: String,
        pausedDomains: [String]
    ) throws -> ParsedRules {
        let withoutComments = text.replacingOccurrences(
            of: "(?s)/\\*.*?\\*/",
            with: "",
            options: .regularExpression
        )
        let selectorBlocks = withoutComments.components(separatedBy: "{").dropLast()
        var selectors: [String] = []
        for block in selectorBlocks {
            let candidates = block.components(separatedBy: "}").last.orEmpty
                .split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            selectors += candidates.filter(isSafeSelector)
        }
        let rules = Array(Set(selectors)).sorted().map { selector in
            var trigger: [String: Any] = ["url-filter": ".*"]
            if !pausedDomains.isEmpty {
                trigger["unless-domain"] = pausedDomains.compactMap(webKitDomain)
            }
            return rule(
                trigger: trigger,
                action: ["type": "css-display-none", "selector": selector]
            )
        }
        return ParsedRules(
            rules: rules,
            sourceCount: selectors.count,
            skippedCount: selectors.count - rules.count
        )
    }

    private static func resourceText(name: String, extension fileExtension: String, bundle: Bundle) throws -> String {
        guard let url = bundle.url(forResource: name, withExtension: fileExtension) else {
            throw CandyBlockingError.missingResource("\(name).\(fileExtension)")
        }
        guard let value = try? String(contentsOf: url, encoding: .utf8) else {
            throw CandyBlockingError.unreadableResource(url.lastPathComponent)
        }
        return value
    }

    private static func compilation(
        identifier: String,
        rules: [[String: Any]],
        sourceCount: Int,
        skippedCount: Int
    ) throws -> CandyBlockingCompilation {
        guard
            JSONSerialization.isValidJSONObject(rules),
            let data = try? JSONSerialization.data(withJSONObject: rules),
            let json = String(data: data, encoding: .utf8)
        else {
            throw CandyBlockingError.invalidRuleList
        }
        return CandyBlockingCompilation(
            identifier: identifier,
            json: json,
            sourceRuleCount: sourceCount,
            webKitRuleCount: rules.count,
            skippedRuleCount: skippedCount
        )
    }

    private static func networkTrigger(host: String, unlessDomains: [String]) -> [String: Any] {
        var trigger: [String: Any] = [
            "url-filter": "^https?://([^/]+\\.)*\(NSRegularExpression.escapedPattern(for: host))(:[0-9]+)?/",
            "resource-type": ["image", "style-sheet", "script", "font", "media", "raw"],
        ]
        if !unlessDomains.isEmpty {
            trigger["unless-domain"] = unlessDomains.compactMap(webKitDomain)
        }
        return trigger
    }

    private static func scopedNetworkTrigger(
        pair: (request: String, page: String)
    ) -> [String: Any] {
        [
            "url-filter": "^https?://([^/]+\\.)*\(NSRegularExpression.escapedPattern(for: pair.request))(:[0-9]+)?/",
            "if-domain": ["*\(pair.page)"],
            "resource-type": ["image", "style-sheet", "script", "font", "media", "raw"],
        ]
    }

    private static func rule(trigger: [String: Any], action: [String: Any]) -> [String: Any] {
        ["trigger": trigger, "action": action]
    }

    private static func canonicalHost(_ rawValue: String) -> String? {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "."))
            .lowercased()
        guard (1...253).contains(value.count), !value.contains("..") else {
            return nil
        }
        let labels = value.split(separator: ".")
        guard labels.allSatisfy({ label in
            (1...63).contains(label.count) &&
                label.first != "-" && label.last != "-" &&
                label.allSatisfy { $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-") }
        }) else {
            return nil
        }
        return value
    }

    private static func webKitDomain(_ rawValue: String) -> String? {
        guard !rawValue.hasSuffix(".*"), let host = canonicalHost(rawValue) else {
            return nil
        }
        return "*\(host)"
    }

    private static func hostsOverlap(_ lhs: String, _ rhs: String) -> Bool {
        guard let left = canonicalHost(lhs), let right = canonicalHost(rhs) else {
            return false
        }
        return left == right || left.hasSuffix(".\(right)") || right.hasSuffix(".\(left)")
    }

    private static func isSafeSelector(_ selector: String) -> Bool {
        !selector.isEmpty && selector.utf8.count <= 2_048 &&
            !selector.contains("{") && !selector.contains("}") &&
            !selector.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains)
    }

    private static func identifier(prefix: String, pausedDomains: [String]) -> String {
        let fingerprint = pausedDomains.joined(separator: "|").utf8.reduce(UInt64(1_469_598_103_934_665_603)) {
            ($0 ^ UInt64($1)) &* 1_099_511_628_211
        }
        return "candy.ios.\(prefix).v2.\(String(fingerprint, radix: 16))"
    }
}

@MainActor
final class CandyBlockingRuntime {
    static let shared = CandyBlockingRuntime()

    private let preferences: UserDefaults
    private let regularStore: WKContentRuleListStore
    private let logger = Logger(subsystem: "dev.sk2andy.candy.ios.scaffold", category: "Blocking")
    private var privatePausedDomains: Set<String> = []
    private var registrations: [BlockingControllerRegistration] = []
    private(set) var lastReport: CandyBlockingActivationReport?

    init(
        preferences: UserDefaults = .standard,
        regularStore: WKContentRuleListStore = .default()
    ) {
        self.preferences = preferences
        self.regularStore = regularStore
    }

    func activate(
        controller: WKUserContentController,
        isPrivate: Bool
    ) async throws -> CandyBlockingActivationReport {
        registrations.removeAll { $0.controller == nil || $0.controller === controller }
        registrations.append(
            BlockingControllerRegistration(controller: controller, isPrivate: isPrivate)
        )
        let pausedDomains = isPrivate ? privatePausedDomains : persistedPausedDomains
        let bundle = Bundle.main
        let compilations = try await Task.detached(priority: .userInitiated) {
            try CandyContentBlockerCompiler.compile(bundle: bundle, pausedDomains: pausedDomains)
        }.value
        var lists: [WKContentRuleList] = []
        for compilation in compilations {
            logger.info("Compiling \(compilation.identifier, privacy: .public) with \(compilation.webKitRuleCount) WebKit rules")
            lists.append(try await compile(compilation, in: regularStore))
        }
        controller.removeAllContentRuleLists()
        lists.forEach(controller.add)
        let report = CandyBlockingActivationReport(
            sourceRuleCount: compilations.reduce(0) { $0 + $1.sourceRuleCount },
            webKitRuleCount: compilations.reduce(0) { $0 + $1.webKitRuleCount },
            skippedRuleCount: compilations.reduce(0) { $0 + $1.skippedRuleCount }
        )
        lastReport = report
        logger.info("Activated \(report.webKitRuleCount) WebKit rules from \(report.sourceRuleCount) source rules; skipped \(report.skippedRuleCount)")
        return report
    }

    func setPaused(_ paused: Bool, host: String, isPrivate: Bool) async throws {
        guard let normalized = URL(string: "https://\(host)")?.host?.lowercased() else {
            throw CandyBlockingError.invalidRuleList
        }
        if isPrivate {
            if paused {
                privatePausedDomains.insert(normalized)
            } else {
                privatePausedDomains.remove(normalized)
            }
        } else {
            var values = persistedPausedDomains
            if paused {
                values.insert(normalized)
            } else {
                values.remove(normalized)
            }
            preferences.set(Array(values).sorted(), forKey: Self.pausedDomainsKey)
        }
        registrations.removeAll { $0.controller == nil }
        for registration in registrations where registration.isPrivate == isPrivate {
            guard let controller = registration.controller else {
                continue
            }
            _ = try await activate(controller: controller, isPrivate: isPrivate)
        }
    }

    private var persistedPausedDomains: Set<String> {
        Set(preferences.stringArray(forKey: Self.pausedDomainsKey) ?? [])
    }

    private func compile(
        _ compilation: CandyBlockingCompilation,
        in store: WKContentRuleListStore
    ) async throws -> WKContentRuleList {
        if let cached = await lookup(compilation.identifier, in: store) {
            logger.info("Reusing compiled \(compilation.identifier, privacy: .public)")
            return cached
        }
        return try await withCheckedThrowingContinuation { continuation in
            store.compileContentRuleList(
                forIdentifier: compilation.identifier,
                encodedContentRuleList: compilation.json
            ) { list, error in
                if let list {
                    continuation.resume(returning: list)
                } else {
                    continuation.resume(
                        throwing: CandyBlockingError.compilationFailed(
                            compilation.identifier,
                            error?.localizedDescription ?? "unknown"
                        )
                    )
                }
            }
        }
    }

    private func lookup(
        _ identifier: String,
        in store: WKContentRuleListStore
    ) async -> WKContentRuleList? {
        await withCheckedContinuation { continuation in
            store.lookUpContentRuleList(forIdentifier: identifier) { list, _ in
                continuation.resume(returning: list)
            }
        }
    }

    private static let pausedDomainsKey = "candy.ios.blocking.paused-domains.v1"
}

@MainActor
private final class BlockingControllerRegistration {
    weak var controller: WKUserContentController?
    let isPrivate: Bool

    init(controller: WKUserContentController, isPrivate: Bool) {
        self.controller = controller
        self.isPrivate = isPrivate
    }
}

private struct ParsedRules {
    let rules: [[String: Any]]
    let sourceCount: Int
    let skippedCount: Int
}

private struct CosmeticRecord {
    let action: String
    let host: String
    let exclusions: [String]
    let selector: String
}

private extension String {
    var lineSequence: [Substring] {
        split(whereSeparator: \.isNewline)
    }
}

private extension Optional where Wrapped == String {
    var orEmpty: String {
        self ?? ""
    }
}

private extension Optional where Wrapped == [CosmeticRecord] {
    var orEmpty: [CosmeticRecord] {
        self ?? []
    }
}

private extension Array {
    func chunked(size: Int) -> [[Element]] {
        guard size > 0 else {
            return []
        }
        return stride(from: 0, to: count, by: size).map { start in
            Array(self[start..<Swift.min(start + size, count)])
        }
    }
}

private extension Data {
    init?(base64URLEncoded value: String) {
        var normalized = value.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        normalized += String(repeating: "=", count: (4 - normalized.count % 4) % 4)
        self.init(base64Encoded: normalized)
    }
}
