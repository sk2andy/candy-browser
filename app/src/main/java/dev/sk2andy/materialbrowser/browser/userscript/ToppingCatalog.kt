package dev.sk2andy.materialbrowser.browser.userscript

internal data class ToppingCatalog(
    val schemaVersion: Int,
    val toppings: List<ToppingCatalogEntry>,
)

internal data class ToppingCatalogEntry(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val license: String,
    val version: String,
    val source: String,
    val matches: List<String>,
    val sha256: String,
)

internal enum class ToppingCatalogRejectionReason {
    Empty,
    TooLarge,
    MalformedJson,
    InvalidSchema,
    TooManyToppings,
    InvalidEntry,
    DuplicateEntry,
}

internal sealed interface ToppingCatalogParseResult {
    data class Accepted(val catalog: ToppingCatalog) : ToppingCatalogParseResult

    data class Rejected(
        val reason: ToppingCatalogRejectionReason,
    ) : ToppingCatalogParseResult
}

internal sealed interface ToppingVerificationResult {
    data class Accepted(val script: UserScript) : ToppingVerificationResult

    data object IntegrityMismatch : ToppingVerificationResult

    data object InvalidUtf8 : ToppingVerificationResult

    data class InvalidScript(
        val reason: UserScriptRejectionReason,
    ) : ToppingVerificationResult

    data object MetadataMismatch : ToppingVerificationResult
}
