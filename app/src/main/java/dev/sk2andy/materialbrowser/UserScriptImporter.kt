package dev.sk2andy.materialbrowser

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.data.UserScriptImportReader
import dev.sk2andy.materialbrowser.data.UserScriptImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class UserScriptImporter(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val browserController: BrowserController,
) {
    fun import(uri: Uri) {
        lifecycleScope.launch {
            val importResult = withContext(Dispatchers.IO) {
                UserScriptImportReader.read(context.contentResolver, uri)
            }
            when (importResult) {
                is UserScriptImportResult.Loaded -> importLoadedScript(importResult)
                else -> UserScriptImportFeedbackRules.from(importResult)?.let { feedback ->
                    showFeedback(feedback)
                }
            }
        }
    }

    private fun importLoadedScript(importResult: UserScriptImportResult.Loaded) {
        val parsedName = (
            UserScriptParser.parse(
                id = "import-preview",
                source = importResult.source,
            ) as? UserScriptParseResult.Accepted
            )?.script?.name
        browserController.saveUserScript(
            id = null,
            source = importResult.source,
        ) { outcome ->
            showFeedback(
                feedback = UserScriptImportFeedbackRules.from(outcome),
                importedName = parsedName,
            )
        }
    }

    private fun showFeedback(
        feedback: UserScriptImportFeedback,
        importedName: String? = null,
    ) {
        Toast.makeText(
            context,
            feedback.message(context, importedName),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun UserScriptImportFeedback.message(
    context: Context,
    importedName: String? = null,
): String = when (this) {
    UserScriptImportFeedback.Imported -> context.getString(
        R.string.userscript_import_success,
        importedName ?: context.getString(R.string.userscript_title),
    )
    UserScriptImportFeedback.LimitReached -> context.getString(
        R.string.userscript_error_limit,
        UserScriptParser.MAX_SCRIPTS,
    )
    UserScriptImportFeedback.EmptyFile -> context.getString(R.string.userscript_import_error_empty)
    UserScriptImportFeedback.FileTooLarge -> context.getString(
        R.string.userscript_import_error_too_large,
        UserScriptParser.MAX_SOURCE_BYTES / 1_024,
    )
    UserScriptImportFeedback.InvalidUtf8 -> context.getString(
        R.string.userscript_import_error_invalid_utf8,
    )
    UserScriptImportFeedback.UnreadableFile -> context.getString(
        R.string.userscript_import_error_unreadable,
    )
    UserScriptImportFeedback.InvalidMetadata -> context.getString(
        R.string.userscript_import_error_invalid_metadata,
    )
    UserScriptImportFeedback.MissingName -> context.getString(
        R.string.userscript_import_error_missing_name,
    )
    UserScriptImportFeedback.NameTooLong -> context.getString(
        R.string.userscript_import_error_name_too_long,
        UserScriptParser.MAX_NAME_CHARS,
    )
    UserScriptImportFeedback.MissingScope -> context.getString(
        R.string.userscript_import_error_missing_scope,
    )
    UserScriptImportFeedback.TooManyMetadataValues -> context.getString(
        R.string.userscript_import_error_too_many_metadata_values,
        UserScriptParser.MAX_PATTERNS_PER_KIND,
    )
    UserScriptImportFeedback.InvalidScope -> context.getString(
        R.string.userscript_import_error_invalid_scope,
    )
    UserScriptImportFeedback.InvalidRunAt -> context.getString(
        R.string.userscript_import_error_invalid_run_at,
    )
    UserScriptImportFeedback.UnsupportedGrant -> context.getString(
        R.string.userscript_import_error_unsupported_grant,
    )
    UserScriptImportFeedback.InvalidDependency -> context.getString(
        R.string.userscript_import_error_invalid_dependency,
    )
    UserScriptImportFeedback.TooManyDependencies -> context.getString(
        R.string.userscript_import_error_too_many_dependencies,
    )
    UserScriptImportFeedback.DependencyUnavailable -> context.getString(
        R.string.userscript_import_error_dependency_unavailable,
    )
    UserScriptImportFeedback.DependencyTooLarge -> context.getString(
        R.string.userscript_import_error_dependency_too_large,
    )
    UserScriptImportFeedback.DependencyInvalidUtf8 -> context.getString(
        R.string.userscript_import_error_dependency_invalid_utf8,
    )
    UserScriptImportFeedback.DependencyIntegrityMismatch -> context.getString(
        R.string.userscript_import_error_dependency_integrity,
    )
    UserScriptImportFeedback.SaveFailed -> context.getString(
        R.string.userscript_import_error_save_failed,
    )
}
