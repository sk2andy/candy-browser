package dev.sk2andy.materialbrowser.browser.userscript

internal enum class UserScriptRunAt {
    DocumentStart,
    DocumentEnd,
}

internal data class UserScript(
    val id: String,
    val name: String,
    val source: String,
    val enabled: Boolean = true,
    val matchPatterns: List<String>,
    val includePatterns: List<String>,
    val excludePatterns: List<String>,
    val runAt: UserScriptRunAt,
    val updatedAtMillis: Long = 0L,
)

internal enum class UserScriptRejectionReason {
    SourceEmpty,
    SourceTooLarge,
    InvalidId,
    InvalidMetadataBlock,
    MissingName,
    NameTooLong,
    MissingInclude,
    TooManyMetadataValues,
    InvalidMatchPattern,
    InvalidIncludePattern,
    InvalidExcludePattern,
    InvalidRunAt,
    PrivilegedGrant,
    RemoteDependency,
    InvalidUpdatedAt,
}

internal sealed interface UserScriptParseResult {
    data class Accepted(val script: UserScript) : UserScriptParseResult

    data class Rejected(val reason: UserScriptRejectionReason) : UserScriptParseResult
}
