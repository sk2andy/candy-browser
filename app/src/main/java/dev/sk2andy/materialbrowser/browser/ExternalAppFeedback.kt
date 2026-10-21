package dev.sk2andy.materialbrowser.browser

internal enum class ExternalAppFeedbackStage {
    Opened,
    ConfirmAlwaysInCandy,
}

internal data class ExternalAppFeedback(
    val id: Long,
    val stage: ExternalAppFeedbackStage,
    val site: String?,
    val isPrivate: Boolean,
)
