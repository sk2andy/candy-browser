package dev.sk2andy.materialbrowser.blocking

import java.net.IDN
import java.net.URI
import java.util.Locale
import java.util.UUID

const val CANDY_RULE_FORMAT_VERSION = 1

enum class CandyRuleAction { Block, Allow, Cosmetic }

enum class CandyRuleKind { RequestHost, HostPair, CosmeticCss }

enum class CandyRuleOrigin { User, PrivacyXRay, Import, Subscription }

data class CandyRule(
    val id: String,
    val action: CandyRuleAction,
    val kind: CandyRuleKind,
    val requestHost: String? = null,
    val firstPartyHost: String? = null,
    val cosmeticSelector: String? = null,
    val profileId: String? = null,
    val group: String = "Personal",
    val origin: CandyRuleOrigin = CandyRuleOrigin.User,
    val sourceUrl: String? = null,
    val updatedAtMillis: Long = 0L,
    val active: Boolean = true,
    val hitCount: Int = 0,
) {
    companion object {
        fun new(
            action: CandyRuleAction,
            kind: CandyRuleKind,
            requestHost: String? = null,
            firstPartyHost: String? = null,
            cosmeticSelector: String? = null,
            profileId: String? = null,
            group: String = "Personal",
            origin: CandyRuleOrigin = CandyRuleOrigin.User,
            sourceUrl: String? = null,
            updatedAtMillis: Long = 0L,
        ) = CandyRule(
            id = UUID.randomUUID().toString(),
            action = action,
            kind = kind,
            requestHost = requestHost,
            firstPartyHost = firstPartyHost,
            cosmeticSelector = cosmeticSelector,
            profileId = profileId,
            group = group,
            origin = origin,
            sourceUrl = sourceUrl,
            updatedAtMillis = updatedAtMillis,
        )
    }
}

sealed interface CandyRuleValidation {
    data class Valid(val rule: CandyRule) : CandyRuleValidation
    data class Invalid(val reason: CandyRuleError) : CandyRuleValidation
}

enum class CandyRuleError {
    InvalidId,
    InvalidHost,
    PublicSuffixHost,
    InvalidPair,
    InvalidSelector,
    InvalidProfile,
    InvalidGroup,
    InvalidSource,
    UnsupportedCombination,
}

object CandyHostCanonicalizer {
    fun canonicalHost(value: String?): String? {
        val candidate = value?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT)
            ?.takeIf(String::isNotEmpty) ?: return null
        if (candidate.startsWith("[") || candidate.any { it == ':' || it == '/' }) return null
        if (candidate.all { it.isDigit() || it == '.' }) return null
        return runCatching { IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES) }
            .getOrNull()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { ascii ->
                ascii.length <= 253 &&
                    '.' in ascii &&
                    ascii.split('.').all { label ->
                        label.length in 1..63 && label.first() != '-' && label.last() != '-'
                    }
            }
    }

    fun webHost(url: String?): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        return canonicalHost(uri.host ?: return null)
    }

    fun matches(host: String, ruleHost: String): Boolean =
        host == ruleHost || host.endsWith(".$ruleHost")
}

object CandyPublicSuffixRules {
    // Common ICANN multi-label suffixes. Unknown suffixes remain registrable only above one label.
    private val multiLabelSuffixes = setOf(
        "ac.uk", "co.uk", "gov.uk", "ltd.uk", "me.uk", "net.uk", "org.uk", "plc.uk",
        "asn.au", "com.au", "conf.au", "csiro.au", "edu.au", "gov.au", "id.au", "net.au",
        "org.au", "oz.au",
        "ac.nz", "co.nz", "geek.nz", "gen.nz", "govt.nz", "health.nz", "iwi.nz",
        "maori.nz", "mil.nz", "net.nz", "org.nz", "parliament.nz", "school.nz",
        "com.ar", "edu.ar", "gob.ar", "gov.ar", "int.ar", "mil.ar", "net.ar", "org.ar",
        "com.br", "net.br", "org.br", "com.cn", "net.cn", "org.cn",
        "co.in", "firm.in", "gen.in", "ind.in", "net.in", "org.in",
        "co.jp", "ne.jp", "or.jp", "com.mx", "com.tr", "co.za",
        "appspot.com", "blogspot.com", "github.io", "netlify.app", "pages.dev", "vercel.app",
    )
    private val commonSecondLevelRegistries = setOf(
        "ac", "co", "com", "edu", "firm", "gen", "go", "gov", "ind", "mil", "ne",
        "net", "nom", "or", "org",
    )

    fun isPublicSuffix(host: String): Boolean {
        if ('.' !in host || host in multiLabelSuffixes) return true
        val labels = host.split('.')
        return (labels.size == 2 && labels.last().length == 2 &&
            labels.first() in commonSecondLevelRegistries) || isUsK12Suffix(labels)
    }

    fun registrableDomain(host: String?): String? {
        val safeHost = CandyHostCanonicalizer.canonicalHost(host) ?: return null
        if (isPublicSuffix(safeHost)) return null
        val labels = safeHost.split('.')
        val lastTwo = labels.takeLast(2).joinToString(".")
        val suffixLength = when {
            labels.size >= 3 && isUsK12Suffix(labels.takeLast(3)) -> 3
            lastTwo in multiLabelSuffixes ||
                (labels.last().length == 2 &&
                    labels[labels.lastIndex - 1] in commonSecondLevelRegistries) -> 2
            else -> 1
        }
        if (labels.size <= suffixLength) return null
        return labels.takeLast(suffixLength + 1).joinToString(".")
    }

    private fun isUsK12Suffix(labels: List<String>): Boolean =
        labels.size == 3 && labels[0] == "k12" && labels[1].length == 2 && labels[2] == "us"
}

object CandyRuleValidator {
    const val MAX_RULES = 4_096
    const val MAX_COSMETIC_RULES = 64
    const val MAX_GROUP_LENGTH = 48
    const val MAX_PROFILE_ID_LENGTH = 128
    const val MAX_SELECTOR_LENGTH = 2_048
    const val MAX_SOURCE_URL_LENGTH = 2_048

    fun validate(input: CandyRule): CandyRuleValidation {
        val id = input.id.trim()
        if (id.isEmpty() || id.length > 128 || id.any(Char::isWhitespace)) {
            return CandyRuleValidation.Invalid(CandyRuleError.InvalidId)
        }
        val group = input.group.trim()
        if (group.isEmpty() || group.length > MAX_GROUP_LENGTH || group.any(Char::isISOControl)) {
            return CandyRuleValidation.Invalid(CandyRuleError.InvalidGroup)
        }
        val profileId = input.profileId?.trim()?.takeIf(String::isNotEmpty)
        if (profileId != null &&
            (profileId.length > MAX_PROFILE_ID_LENGTH || profileId.any(Char::isISOControl))
        ) return CandyRuleValidation.Invalid(CandyRuleError.InvalidProfile)
        val sourceUrl = input.sourceUrl?.trim()?.takeIf(String::isNotEmpty)
        if (sourceUrl != null &&
            (sourceUrl.toByteArray(Charsets.UTF_8).size > MAX_SOURCE_URL_LENGTH ||
                !isSafeHttpsUrl(sourceUrl))
        ) {
            return CandyRuleValidation.Invalid(CandyRuleError.InvalidSource)
        }
        val requestHost = CandyHostCanonicalizer.canonicalHost(input.requestHost)
        val firstPartyHost = CandyHostCanonicalizer.canonicalHost(input.firstPartyHost)
        val selector = input.cosmeticSelector?.trim()?.takeIf(String::isNotEmpty)
        val invalid = when (input.kind) {
            CandyRuleKind.RequestHost -> when {
                input.action == CandyRuleAction.Cosmetic -> CandyRuleError.UnsupportedCombination
                requestHost == null -> CandyRuleError.InvalidHost
                CandyPublicSuffixRules.isPublicSuffix(requestHost) -> CandyRuleError.PublicSuffixHost
                input.firstPartyHost != null || selector != null -> CandyRuleError.UnsupportedCombination
                else -> null
            }
            CandyRuleKind.HostPair -> when {
                input.action == CandyRuleAction.Cosmetic -> CandyRuleError.UnsupportedCombination
                requestHost == null || firstPartyHost == null -> CandyRuleError.InvalidPair
                CandyPublicSuffixRules.isPublicSuffix(requestHost) ||
                    CandyPublicSuffixRules.registrableDomain(firstPartyHost) == null ->
                    CandyRuleError.PublicSuffixHost
                CandyPublicSuffixRules.registrableDomain(requestHost) ==
                    CandyPublicSuffixRules.registrableDomain(firstPartyHost) ->
                    CandyRuleError.InvalidPair
                selector != null -> CandyRuleError.UnsupportedCombination
                else -> null
            }
            CandyRuleKind.CosmeticCss -> when {
                input.action != CandyRuleAction.Cosmetic -> CandyRuleError.UnsupportedCombination
                firstPartyHost == null ||
                    CandyPublicSuffixRules.registrableDomain(firstPartyHost) == null ->
                    CandyRuleError.PublicSuffixHost
                requestHost != null -> CandyRuleError.UnsupportedCombination
                !isSafeSelector(selector) -> CandyRuleError.InvalidSelector
                else -> null
            }
        }
        if (invalid != null) return CandyRuleValidation.Invalid(invalid)
        return CandyRuleValidation.Valid(
            input.copy(
                id = id,
                requestHost = requestHost,
                firstPartyHost = firstPartyHost,
                cosmeticSelector = selector,
                profileId = profileId,
                group = group,
                sourceUrl = sourceUrl,
                updatedAtMillis = input.updatedAtMillis.coerceAtLeast(0L),
                hitCount = input.hitCount.coerceAtLeast(0),
            ),
        )
    }

    fun normalizeAll(rules: Iterable<CandyRule>): List<CandyRule> = rules.asSequence()
        .mapNotNull { (validate(it) as? CandyRuleValidation.Valid)?.rule }
        .distinctBy(CandyRule::id)
        .take(MAX_RULES)
        .toList()

    fun isSafeHttpsUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.userInfo == null && uri.fragment == null &&
            CandyHostCanonicalizer.canonicalHost(uri.host) != null
    }

    internal fun isSafeSelector(selector: String?): Boolean {
        if (selector == null || selector.length > MAX_SELECTOR_LENGTH) return false
        if (selector.any(Char::isISOControl)) return false
        val lower = selector.lowercase(Locale.ROOT)
        return listOf("{", "}", "<", "@import", "javascript:", "url(", "expression(")
            .none(lower::contains)
    }
}

enum class CandyDecisionAction { Block, Allow }

data class CandyRuleDecision(
    val action: CandyDecisionAction,
    val ruleId: String,
    val rule: CandyRule,
)

data class CandyMatcherSnapshots(
    val persistent: CandyMatcherSnapshot,
    val incognito: CandyMatcherSnapshot,
) {
    companion object {
        fun compile(rules: List<CandyRule>, ephemeralRuleIds: Set<String>): CandyMatcherSnapshots =
            CandyMatcherSnapshots(
                persistent = CandyMatcherSnapshot.compile(
                    rules.filterNot { it.id in ephemeralRuleIds },
                ),
                incognito = CandyMatcherSnapshot.compile(rules),
            )
    }
}

private data class CandyRuleBucket(
    val global: CandyRule?,
    val byProfile: Map<String, CandyRule>,
) {
    fun winner(profileId: String): CandyRule? {
        val profileRule = byProfile[profileId]
        return when {
            global == null -> profileRule
            profileRule == null -> global
            CandyRulePrecedence.comparator.compare(profileRule, global) < 0 -> profileRule
            else -> global
        }
    }

    companion object {
        fun compile(rules: List<CandyRule>): CandyRuleBucket {
            val sorted = rules.filter(CandyRule::active).sortedWith(CandyRulePrecedence.comparator)
            return CandyRuleBucket(
                global = sorted.firstOrNull { it.profileId == null },
                byProfile = sorted.asSequence()
                    .filter { it.profileId != null }
                    .groupBy { it.profileId.orEmpty() }
                    .mapValues { (_, value) -> value.first() },
            )
        }
    }
}

data class CandyMatcherSnapshot private constructor(
    val rules: List<CandyRule>,
    private val hostRules: Map<String, CandyRuleBucket>,
    private val pairRules: Map<String, Map<String, CandyRuleBucket>>,
    private val cosmeticRuleList: List<CandyRule>,
) {
    val hasRequestRules: Boolean
        get() = hostRules.isNotEmpty() || pairRules.isNotEmpty()

    fun decide(
        requestUrl: String,
        pageUrl: String?,
        profileId: String,
        isForMainFrame: Boolean,
    ): CandyRuleDecision? {
        if (isForMainFrame || !hasRequestRules) return null
        val requestHost = CandyHostCanonicalizer.webHost(requestUrl) ?: return null
        val pageHost = CandyHostCanonicalizer.webHost(pageUrl)
        return decideCanonicalHosts(requestHost, pageHost, profileId)
    }

    fun decideHosts(
        requestHost: String?,
        pageHost: String?,
        profileId: String,
        isForMainFrame: Boolean,
    ): CandyRuleDecision? {
        if (isForMainFrame || !hasRequestRules) return null
        val normalizedRequestHost = requestHost?.lowercase(Locale.ROOT)?.trimEnd('.')
            ?.takeIf(String::isNotEmpty) ?: return null
        val normalizedPageHost = pageHost?.lowercase(Locale.ROOT)?.trimEnd('.')
            ?.takeIf(String::isNotEmpty)
        return decideCanonicalHosts(normalizedRequestHost, normalizedPageHost, profileId)
    }

    private fun decideCanonicalHosts(
        requestHost: String,
        pageHost: String?,
        profileId: String,
    ): CandyRuleDecision? {
        var winner: CandyRule? = null
        forEachHostSuffix(requestHost) { requestCandidate ->
            winner = chooseWinner(winner, hostRules[requestCandidate]?.winner(profileId))
        }
        if (pageHost != null) {
            forEachHostSuffix(pageHost) { pageCandidate ->
                val requestMaps = pairRules[pageCandidate] ?: return@forEachHostSuffix
                forEachHostSuffix(requestHost) { requestCandidate ->
                    winner = chooseWinner(
                        winner,
                        requestMaps[requestCandidate]?.winner(profileId),
                    )
                }
            }
        }
        val winningRule = winner ?: return null
        return CandyRuleDecision(
            action = if (winningRule.action == CandyRuleAction.Allow) {
                CandyDecisionAction.Allow
            } else {
                CandyDecisionAction.Block
            },
            ruleId = winningRule.id,
            rule = winningRule,
        )
    }

    fun cosmeticRules(originUrl: String, profileId: String): List<CandyRule> {
        val host = CandyHostCanonicalizer.webHost(originUrl) ?: return emptyList()
        return cosmeticRuleList.filter { rule ->
            rule.active && rule.kind == CandyRuleKind.CosmeticCss &&
                (rule.profileId == null || rule.profileId == profileId) &&
                rule.firstPartyHost?.let { CandyHostCanonicalizer.matches(host, it) } == true
        }.sortedBy(CandyRule::id)
    }

    companion object {
        val Empty = compile(emptyList())

        fun compile(input: Iterable<CandyRule>): CandyMatcherSnapshot {
            val rules = CandyRuleValidator.normalizeAll(input).toList()
            return CandyMatcherSnapshot(
                rules = rules,
                hostRules = rules.filter { it.kind == CandyRuleKind.RequestHost }
                    .groupBy { it.requestHost.orEmpty() }
                    .mapValues { (_, value) -> CandyRuleBucket.compile(value) },
                pairRules = rules.filter { it.kind == CandyRuleKind.HostPair }
                    .groupBy { it.firstPartyHost.orEmpty() }
                    .mapValues { (_, byPage) ->
                        byPage.groupBy { it.requestHost.orEmpty() }
                            .mapValues { (_, value) ->
                                CandyRuleBucket.compile(value)
                            }
                    },
                cosmeticRuleList = rules.filter { it.kind == CandyRuleKind.CosmeticCss }
                    .sortedBy(CandyRule::id),
            )
        }

        private inline fun forEachHostSuffix(host: String, block: (String) -> Unit) {
            var start = 0
            while (true) {
                block(if (start == 0) host else host.substring(start))
                val dot = host.indexOf('.', start)
                if (dot < 0) break
                start = dot + 1
            }
        }

        private fun chooseWinner(current: CandyRule?, candidate: CandyRule?): CandyRule? = when {
            candidate == null -> current
            current == null -> candidate
            CandyRulePrecedence.comparator.compare(candidate, current) < 0 -> candidate
            else -> current
        }
    }
}

object CandyRulePrecedence {
    val comparator: Comparator<CandyRule> = compareByDescending<CandyRule> { rule ->
        rule.kind == CandyRuleKind.HostPair && rule.action == CandyRuleAction.Allow
    }.thenByDescending { it.kind == CandyRuleKind.HostPair }
        .thenByDescending { it.firstPartyHost?.length ?: 0 }
        .thenByDescending { it.requestHost?.length ?: 0 }
        .thenByDescending { it.action == CandyRuleAction.Allow }
        .thenBy(CandyRule::id)
}
