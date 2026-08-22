package dev.sk2andy.materialbrowser

import dev.sk2andy.materialbrowser.browser.UserScriptSaveOutcome
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyFailureReason
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRejectionReason
import dev.sk2andy.materialbrowser.data.UserScriptImportResult

internal enum class UserScriptImportFeedback {
    Imported,
    LimitReached,
    EmptyFile,
    FileTooLarge,
    InvalidUtf8,
    UnreadableFile,
    InvalidMetadata,
    MissingName,
    NameTooLong,
    MissingScope,
    TooManyMetadataValues,
    InvalidScope,
    InvalidRunAt,
    UnsupportedGrant,
    InvalidDependency,
    TooManyDependencies,
    DependencyUnavailable,
    DependencyTooLarge,
    DependencyInvalidUtf8,
    DependencyIntegrityMismatch,
    SaveFailed,
}

internal object UserScriptImportFeedbackRules {
    fun from(result: UserScriptImportResult): UserScriptImportFeedback? = when (result) {
        is UserScriptImportResult.Loaded -> null
        UserScriptImportResult.Empty -> UserScriptImportFeedback.EmptyFile
        UserScriptImportResult.TooLarge -> UserScriptImportFeedback.FileTooLarge
        UserScriptImportResult.InvalidUtf8 -> UserScriptImportFeedback.InvalidUtf8
        UserScriptImportResult.Unreadable -> UserScriptImportFeedback.UnreadableFile
    }

    fun from(outcome: UserScriptSaveOutcome): UserScriptImportFeedback = when (outcome) {
        UserScriptSaveOutcome.Saved -> UserScriptImportFeedback.Imported
        UserScriptSaveOutcome.LimitReached -> UserScriptImportFeedback.LimitReached
        UserScriptSaveOutcome.Missing,
        UserScriptSaveOutcome.PersistenceFailed,
        -> UserScriptImportFeedback.SaveFailed
        is UserScriptSaveOutcome.Rejected -> from(outcome.reason)
        is UserScriptSaveOutcome.DependencyFailed -> from(outcome.reason)
    }

    fun from(reason: UserScriptDependencyFailureReason): UserScriptImportFeedback = when (reason) {
        UserScriptDependencyFailureReason.InvalidDeclaration ->
            UserScriptImportFeedback.InvalidDependency
        UserScriptDependencyFailureReason.Network -> UserScriptImportFeedback.DependencyUnavailable
        UserScriptDependencyFailureReason.TooLarge,
        UserScriptDependencyFailureReason.TotalTooLarge,
        -> UserScriptImportFeedback.DependencyTooLarge
        UserScriptDependencyFailureReason.InvalidUtf8 ->
            UserScriptImportFeedback.DependencyInvalidUtf8
        UserScriptDependencyFailureReason.IntegrityMismatch ->
            UserScriptImportFeedback.DependencyIntegrityMismatch
    }

    private fun from(reason: UserScriptRejectionReason): UserScriptImportFeedback = when (reason) {
        UserScriptRejectionReason.SourceEmpty -> UserScriptImportFeedback.EmptyFile
        UserScriptRejectionReason.SourceTooLarge -> UserScriptImportFeedback.FileTooLarge
        UserScriptRejectionReason.InvalidMetadataBlock -> UserScriptImportFeedback.InvalidMetadata
        UserScriptRejectionReason.MissingName -> UserScriptImportFeedback.MissingName
        UserScriptRejectionReason.NameTooLong -> UserScriptImportFeedback.NameTooLong
        UserScriptRejectionReason.MissingInclude -> UserScriptImportFeedback.MissingScope
        UserScriptRejectionReason.TooManyMetadataValues ->
            UserScriptImportFeedback.TooManyMetadataValues
        UserScriptRejectionReason.InvalidMatchPattern,
        UserScriptRejectionReason.InvalidIncludePattern,
        UserScriptRejectionReason.InvalidExcludePattern,
        -> UserScriptImportFeedback.InvalidScope
        UserScriptRejectionReason.InvalidRunAt -> UserScriptImportFeedback.InvalidRunAt
        UserScriptRejectionReason.InvalidRequire,
        UserScriptRejectionReason.InvalidResource,
        -> UserScriptImportFeedback.InvalidDependency
        UserScriptRejectionReason.TooManyDependencies ->
            UserScriptImportFeedback.TooManyDependencies
        UserScriptRejectionReason.PrivilegedGrant -> UserScriptImportFeedback.UnsupportedGrant
        UserScriptRejectionReason.InvalidId,
        UserScriptRejectionReason.InvalidUpdatedAt,
        -> UserScriptImportFeedback.SaveFailed
    }
}
