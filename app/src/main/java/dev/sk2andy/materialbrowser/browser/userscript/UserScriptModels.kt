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
    val grants: Set<UserScriptGrant>,
    val runAt: UserScriptRunAt,
    val requires: List<UserScriptRequire> = emptyList(),
    val resources: List<UserScriptResource> = emptyList(),
    val updatedAtMillis: Long = 0L,
)

internal data class UserScriptRequire(
    val url: String,
    val sha256: String? = null,
    val source: String? = null,
)

internal data class UserScriptResource(
    val name: String,
    val url: String,
    val sha256: String? = null,
    val encodedContent: String? = null,
    val mimeType: String? = null,
)

internal enum class UserScriptGrant(
    val metadataValue: String,
    private vararg val aliases: String,
) {
    AddStyle("GM_addStyle", "GM.addStyle"),
    DeleteValue("GM_deleteValue", "GM.deleteValue"),
    GetValue("GM_getValue", "GM.getValue"),
    GetResourceText("GM_getResourceText", "GM.getResourceText"),
    GetResourceUrl("GM_getResourceURL", "GM.getResourceUrl", "GM.getResourceURL"),
    Info("GM_info", "GM.info"),
    ListValues("GM_listValues", "GM.listValues"),
    OpenInTab("GM_openInTab", "GM.openInTab"),
    RegisterMenuCommand("GM_registerMenuCommand", "GM.registerMenuCommand"),
    SetValue("GM_setValue", "GM.setValue"),
    UnregisterMenuCommand("GM_unregisterMenuCommand", "GM.unregisterMenuCommand"),
    ;

    companion object {
        fun fromMetadata(value: String): UserScriptGrant? = entries.firstOrNull { grant ->
            grant.metadataValue.equals(value, ignoreCase = true) ||
                grant.aliases.any { alias -> alias.equals(value, ignoreCase = true) }
        }
    }
}

internal data class UserScriptMenuCommand(
    val tabId: String,
    val scriptId: String,
    val scriptName: String,
    val commandId: String,
    val caption: String,
)

internal data class UserScriptOpenTabRequest(
    val tabId: String,
    val scriptId: String,
    val url: String,
    val active: Boolean,
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
    InvalidRequire,
    InvalidResource,
    TooManyDependencies,
    PrivilegedGrant,
    InvalidUpdatedAt,
}

internal sealed interface UserScriptParseResult {
    data class Accepted(val script: UserScript) : UserScriptParseResult

    data class Rejected(val reason: UserScriptRejectionReason) : UserScriptParseResult
}
