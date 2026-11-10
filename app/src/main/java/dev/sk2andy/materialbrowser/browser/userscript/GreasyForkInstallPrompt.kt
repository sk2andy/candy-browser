package dev.sk2andy.materialbrowser.browser.userscript

internal data class GreasyForkInstallPrompt(
    val tabId: String,
    val stage: GreasyForkInstallStage,
)

internal sealed interface GreasyForkInstallStage {
    data object Loading : GreasyForkInstallStage

    data object PrivateBlocked : GreasyForkInstallStage

    data object Installed : GreasyForkInstallStage

    data class Review(
        val script: UserScript,
        val isSaving: Boolean = false,
    ) : GreasyForkInstallStage

    data class Failed(val reason: GreasyForkInstallError) : GreasyForkInstallStage
}

internal enum class GreasyForkInstallError {
    Network,
    TooLarge,
    InvalidUtf8,
    InvalidScript,
    UnsupportedGrant,
    DependencyFailed,
    SaveFailed,
}
