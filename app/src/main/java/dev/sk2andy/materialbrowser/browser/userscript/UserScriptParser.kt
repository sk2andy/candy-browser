package dev.sk2andy.materialbrowser.browser.userscript

import java.net.URI

internal object UserScriptParser {
    const val MAX_SCRIPTS = 128
    const val MAX_SOURCE_BYTES = 256 * 1_024
    const val MAX_NAME_CHARS = 120
    const val MAX_PATTERN_CHARS = 2_048
    const val MAX_PATTERNS_PER_KIND = 64

    fun parse(
        id: String,
        source: String,
        enabled: Boolean = true,
        updatedAtMillis: Long = 0L,
    ): UserScriptParseResult {
        if (!isValidId(id)) return rejected(UserScriptRejectionReason.InvalidId)
        if (updatedAtMillis < 0L) return rejected(UserScriptRejectionReason.InvalidUpdatedAt)
        val normalizedSource = source.removePrefix(UTF8_BOM)
        if (normalizedSource.isBlank()) return rejected(UserScriptRejectionReason.SourceEmpty)
        if (normalizedSource.toByteArray(Charsets.UTF_8).size > MAX_SOURCE_BYTES) {
            return rejected(UserScriptRejectionReason.SourceTooLarge)
        }

        val metadata = metadataLines(normalizedSource)
            ?: return rejected(UserScriptRejectionReason.InvalidMetadataBlock)
        val values = linkedMapOf<String, MutableList<String>>()
        metadata.forEach { line ->
            val match = METADATA_LINE.matchEntire(line) ?: return@forEach
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].trim()
            values.getOrPut(key, ::mutableListOf).add(value)
        }

        if (REMOTE_DIRECTIVES.any(values::containsKey)) {
            return rejected(UserScriptRejectionReason.RemoteDependency)
        }
        if (values.containsKey("connect")) {
            return rejected(UserScriptRejectionReason.PrivilegedGrant)
        }
        if (values["grant"].orEmpty().any { grant -> !grant.equals("none", ignoreCase = true) }) {
            return rejected(UserScriptRejectionReason.PrivilegedGrant)
        }

        val names = values["name"].orEmpty()
        val name = names.singleOrNull()?.takeIf(String::isNotBlank)
            ?: return rejected(UserScriptRejectionReason.MissingName)
        if (name.length > MAX_NAME_CHARS || name.any { char -> char.isISOControl() }) {
            return rejected(UserScriptRejectionReason.NameTooLong)
        }

        val matches = values["match"].orEmpty()
        val includes = values["include"].orEmpty()
        val excludes = values["exclude"].orEmpty()
        if (listOf(matches, includes, excludes).any { it.size > MAX_PATTERNS_PER_KIND }) {
            return rejected(UserScriptRejectionReason.TooManyMetadataValues)
        }
        if (matches.isEmpty() && includes.isEmpty()) {
            return rejected(UserScriptRejectionReason.MissingInclude)
        }
        if (matches.any { pattern -> parseMatchPattern(pattern) == null }) {
            return rejected(UserScriptRejectionReason.InvalidMatchPattern)
        }
        if (includes.any { pattern -> parseGlobPattern(pattern) == null }) {
            return rejected(UserScriptRejectionReason.InvalidIncludePattern)
        }
        if (excludes.any { pattern -> parseGlobPattern(pattern) == null }) {
            return rejected(UserScriptRejectionReason.InvalidExcludePattern)
        }

        val runAtValues = values["run-at"].orEmpty()
        if (runAtValues.size > 1) return rejected(UserScriptRejectionReason.InvalidRunAt)
        val runAt = when (runAtValues.singleOrNull()?.lowercase()) {
            null, "document-end" -> UserScriptRunAt.DocumentEnd
            "document-start" -> UserScriptRunAt.DocumentStart
            else -> return rejected(UserScriptRejectionReason.InvalidRunAt)
        }

        return UserScriptParseResult.Accepted(
            UserScript(
                id = id,
                name = name,
                source = normalizedSource,
                enabled = enabled,
                matchPatterns = matches,
                includePatterns = includes,
                excludePatterns = excludes,
                runAt = runAt,
                updatedAtMillis = updatedAtMillis,
            ),
        )
    }

    internal fun parseMatchPattern(value: String): ParsedUserScriptMatchPattern? {
        if (value == "<all_urls>") {
            return ParsedUserScriptMatchPattern(
                scheme = "*",
                host = "*",
                path = "/*",
            )
        }
        if (!isBoundedPattern(value)) return null
        val match = MATCH_PATTERN.matchEntire(value) ?: return null
        val scheme = match.groupValues[1].lowercase()
        val host = match.groupValues[2].lowercase()
        val path = match.groupValues[3]
        if (!isValidMatchHost(host)) return null
        return ParsedUserScriptMatchPattern(scheme = scheme, host = host, path = path)
    }

    internal fun parseGlobPattern(value: String): ParsedUserScriptGlobPattern? {
        if (!isBoundedPattern(value)) return null
        val match = MATCH_PATTERN.matchEntire(value) ?: return null
        val scheme = match.groupValues[1].lowercase()
        val authority = match.groupValues[2].lowercase()
        val path = match.groupValues[3]
        if (!isValidGlobAuthority(authority)) return null
        return ParsedUserScriptGlobPattern(scheme = scheme, authority = authority, path = path)
    }

    private fun metadataLines(source: String): List<String>? {
        val lines = source.lineSequence().map { line -> line.removeSuffix("\r") }.toList()
        val start = lines.indexOfFirst { line -> METADATA_START.matches(line) }
        if (start < 0) return null
        val endOffset = lines.drop(start + 1).indexOfFirst { line -> METADATA_END.matches(line) }
        if (endOffset < 0) return null
        val end = start + 1 + endOffset
        if (lines.drop(end + 1).any { line -> METADATA_START.matches(line) }) return null
        return lines.subList(start + 1, end)
    }

    private fun isValidId(value: String): Boolean =
        value.length in 1..MAX_ID_CHARS && value.none { char ->
            char.isWhitespace() || char.isISOControl()
        }

    private fun isBoundedPattern(value: String): Boolean =
        value.length in 1..MAX_PATTERN_CHARS && value.none { char -> char.isISOControl() }

    private fun isValidGlobAuthority(value: String): Boolean {
        if (value.isBlank() || value.contains('@')) return false
        val portSeparator = value.lastIndexOf(':')
        val hasPort = portSeparator >= 0
        val host = if (hasPort) value.substring(0, portSeparator) else value
        val port = if (hasPort) value.substring(portSeparator + 1) else null
        val portNumber = port?.toIntOrNull()
        if (port != null && (portNumber == null || portNumber !in 1..65_535)) return false
        if (host == "*") return true
        val candidate = host.removePrefix("*.")
        if (candidate.isEmpty() || candidate.contains('*')) return false
        return runCatching { URI("https://$candidate/").host != null }.getOrDefault(false)
    }

    private fun isValidMatchHost(value: String): Boolean {
        if (value == "*") return true
        val candidate = value.removePrefix("*.")
        if (candidate.isEmpty() || candidate.contains('*')) return false
        return runCatching {
            val uri = URI("https://$candidate/")
            uri.host != null && uri.rawUserInfo == null && uri.port == -1
        }.getOrDefault(false)
    }

    private fun rejected(reason: UserScriptRejectionReason) = UserScriptParseResult.Rejected(reason)

    private const val MAX_ID_CHARS = 128
    private const val UTF8_BOM = "\uFEFF"
    private val METADATA_START = Regex("""^\s*//\s*==UserScript==\s*$""")
    private val METADATA_END = Regex("""^\s*//\s*==/UserScript==\s*$""")
    private val METADATA_LINE = Regex("""^\s*//\s*@([A-Za-z][A-Za-z0-9_-]*)\s*(.*?)\s*$""")
    private val MATCH_PATTERN = Regex("""^(http|https|\*)://([^/]+)(/.*)$""", RegexOption.IGNORE_CASE)
    private val REMOTE_DIRECTIVES = setOf("require", "resource", "downloadurl", "updateurl")
}

internal data class ParsedUserScriptMatchPattern(
    val scheme: String,
    val host: String,
    val path: String,
)

internal data class ParsedUserScriptGlobPattern(
    val scheme: String,
    val authority: String,
    val path: String,
)
