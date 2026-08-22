package dev.sk2andy.materialbrowser

import dev.sk2andy.materialbrowser.browser.UserScriptSaveOutcome
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyFailureReason
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRejectionReason
import dev.sk2andy.materialbrowser.data.UserScriptImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserScriptImportFeedbackRulesTest {
    @Test
    fun `loaded file waits for save outcome`() {
        assertNull(UserScriptImportFeedbackRules.from(UserScriptImportResult.Loaded("source")))
    }

    @Test
    fun `file read failures keep their exact cause`() {
        val expected = mapOf(
            UserScriptImportResult.Empty to UserScriptImportFeedback.EmptyFile,
            UserScriptImportResult.TooLarge to UserScriptImportFeedback.FileTooLarge,
            UserScriptImportResult.InvalidUtf8 to UserScriptImportFeedback.InvalidUtf8,
            UserScriptImportResult.Unreadable to UserScriptImportFeedback.UnreadableFile,
        )

        expected.forEach { (result, feedback) ->
            assertEquals(feedback, UserScriptImportFeedbackRules.from(result))
        }
    }

    @Test
    fun `save outcomes distinguish success limits and persistence failure`() {
        val expected = mapOf(
            UserScriptSaveOutcome.Saved to UserScriptImportFeedback.Imported,
            UserScriptSaveOutcome.LimitReached to UserScriptImportFeedback.LimitReached,
            UserScriptSaveOutcome.Missing to UserScriptImportFeedback.SaveFailed,
            UserScriptSaveOutcome.PersistenceFailed to UserScriptImportFeedback.SaveFailed,
        )

        expected.forEach { (outcome, feedback) ->
            assertEquals(feedback, UserScriptImportFeedbackRules.from(outcome))
        }
    }

    @Test
    fun `metadata rejection reports the actionable category`() {
        val expected = mapOf(
            UserScriptRejectionReason.SourceEmpty to UserScriptImportFeedback.EmptyFile,
            UserScriptRejectionReason.SourceTooLarge to UserScriptImportFeedback.FileTooLarge,
            UserScriptRejectionReason.InvalidMetadataBlock to
                UserScriptImportFeedback.InvalidMetadata,
            UserScriptRejectionReason.MissingName to UserScriptImportFeedback.MissingName,
            UserScriptRejectionReason.NameTooLong to UserScriptImportFeedback.NameTooLong,
            UserScriptRejectionReason.MissingInclude to UserScriptImportFeedback.MissingScope,
            UserScriptRejectionReason.TooManyMetadataValues to
                UserScriptImportFeedback.TooManyMetadataValues,
            UserScriptRejectionReason.InvalidMatchPattern to UserScriptImportFeedback.InvalidScope,
            UserScriptRejectionReason.InvalidIncludePattern to UserScriptImportFeedback.InvalidScope,
            UserScriptRejectionReason.InvalidExcludePattern to UserScriptImportFeedback.InvalidScope,
            UserScriptRejectionReason.InvalidRunAt to UserScriptImportFeedback.InvalidRunAt,
            UserScriptRejectionReason.InvalidRequire to UserScriptImportFeedback.InvalidDependency,
            UserScriptRejectionReason.InvalidResource to UserScriptImportFeedback.InvalidDependency,
            UserScriptRejectionReason.TooManyDependencies to
                UserScriptImportFeedback.TooManyDependencies,
            UserScriptRejectionReason.PrivilegedGrant to UserScriptImportFeedback.UnsupportedGrant,
            UserScriptRejectionReason.InvalidId to UserScriptImportFeedback.SaveFailed,
            UserScriptRejectionReason.InvalidUpdatedAt to UserScriptImportFeedback.SaveFailed,
        )

        expected.forEach { (reason, feedback) ->
            assertEquals(
                feedback,
                UserScriptImportFeedbackRules.from(UserScriptSaveOutcome.Rejected(reason)),
            )
        }
        assertEquals(UserScriptRejectionReason.entries.toSet(), expected.keys)
    }

    @Test
    fun `dependency resolution failure keeps its exact category`() {
        val expected = mapOf(
            UserScriptDependencyFailureReason.InvalidDeclaration to
                UserScriptImportFeedback.InvalidDependency,
            UserScriptDependencyFailureReason.Network to
                UserScriptImportFeedback.DependencyUnavailable,
            UserScriptDependencyFailureReason.TooLarge to
                UserScriptImportFeedback.DependencyTooLarge,
            UserScriptDependencyFailureReason.TotalTooLarge to
                UserScriptImportFeedback.DependencyTooLarge,
            UserScriptDependencyFailureReason.InvalidUtf8 to
                UserScriptImportFeedback.DependencyInvalidUtf8,
            UserScriptDependencyFailureReason.IntegrityMismatch to
                UserScriptImportFeedback.DependencyIntegrityMismatch,
        )

        expected.forEach { (reason, feedback) ->
            assertEquals(feedback, UserScriptImportFeedbackRules.from(reason))
            assertEquals(
                feedback,
                UserScriptImportFeedbackRules.from(UserScriptSaveOutcome.DependencyFailed(reason)),
            )
        }
        assertEquals(UserScriptDependencyFailureReason.entries.toSet(), expected.keys)
    }
}
