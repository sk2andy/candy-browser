package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.userscript.GreasyForkInstallError
import dev.sk2andy.materialbrowser.browser.userscript.GreasyForkInstallStage
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser

@Composable
internal fun GreasyForkInstallDialog(
    stage: GreasyForkInstallStage,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val review = stage as? GreasyForkInstallStage.Review
    var showSource by remember(review?.script?.source) { mutableStateOf(false) }
    if (showSource && review != null) {
        AlertDialog(
            onDismissRequest = { showSource = false },
            modifier = Modifier.testTag(GreasyForkInstallTestTags.SourceDialog),
            title = { Text(stringResource(R.string.userscript_source_label)) },
            text = {
                SelectionContainer {
                    Text(
                        text = review.script.source,
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag(GreasyForkInstallTestTags.Source),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSource = false }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (review?.isSaving != true) onDismiss() },
        modifier = Modifier.testTag(GreasyForkInstallTestTags.Dialog),
        title = { Text(stringResource(R.string.greasy_fork_install_title)) },
        text = {
            when (stage) {
                GreasyForkInstallStage.Loading -> Text(stringResource(R.string.greasy_fork_loading))
                GreasyForkInstallStage.PrivateBlocked -> Text(
                    stringResource(R.string.greasy_fork_private_blocked),
                )
                GreasyForkInstallStage.Installed -> Text(
                    stringResource(R.string.greasy_fork_installed),
                )
                is GreasyForkInstallStage.Failed -> Text(greasyForkErrorMessage(stage.reason))
                is GreasyForkInstallStage.Review -> {
                    val script = stage.script
                    val patterns = (script.matchPatterns + script.includePatterns).distinct()
                    val grants = script.grants.map { it.metadataValue }.sorted()
                    val dependencies = (script.requires.map { it.url } + script.resources.map { it.url })
                        .distinct()
                    val none = stringResource(R.string.greasy_fork_none)
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(script.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.greasy_fork_patterns, patterns.joinToString("\n")))
                        if (script.excludePatterns.isNotEmpty()) {
                            Text(stringResource(R.string.greasy_fork_excludes, script.excludePatterns.joinToString("\n")))
                        }
                        Text(
                            stringResource(
                                R.string.greasy_fork_grants,
                                grants.joinToString(", ").ifEmpty { none },
                            ),
                        )
                        if (dependencies.isNotEmpty()) {
                            Text(stringResource(R.string.greasy_fork_dependencies, dependencies.joinToString("\n")))
                        }
                        Text(stringResource(R.string.userscript_risk_body))
                        Text(stringResource(R.string.greasy_fork_engine_scope))
                        TextButton(
                            onClick = { showSource = true },
                            enabled = !stage.isSaving,
                            modifier = Modifier.testTag(GreasyForkInstallTestTags.ShowSource),
                        ) { Text(stringResource(R.string.greasy_fork_view_source)) }
                    }
                }
            }
        },
        confirmButton = {
            if (review != null) {
                Button(
                    onClick = onInstall,
                    enabled = !review.isSaving,
                    modifier = Modifier.testTag(GreasyForkInstallTestTags.Install),
                ) {
                    Text(stringResource(if (review.isSaving) R.string.greasy_fork_saving else R.string.greasy_fork_install))
                }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag(GreasyForkInstallTestTags.Dismiss)) {
                    Text(stringResource(if (stage == GreasyForkInstallStage.Loading) R.string.action_cancel else R.string.action_done))
                }
            }
        },
        dismissButton = if (review != null) {
            {
                TextButton(
                    onClick = onDismiss,
                    enabled = !review.isSaving,
                    modifier = Modifier.testTag(GreasyForkInstallTestTags.Dismiss),
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        } else {
            null
        },
    )
}

@Composable
private fun greasyForkErrorMessage(reason: GreasyForkInstallError): String = when (reason) {
    GreasyForkInstallError.Network -> stringResource(R.string.greasy_fork_error_network)
    GreasyForkInstallError.TooLarge -> stringResource(
        R.string.userscript_import_error_too_large,
        UserScriptParser.MAX_SOURCE_BYTES / 1_024,
    )
    GreasyForkInstallError.InvalidUtf8 -> stringResource(R.string.userscript_import_error_invalid_utf8)
    GreasyForkInstallError.InvalidScript -> stringResource(R.string.greasy_fork_error_invalid)
    GreasyForkInstallError.UnsupportedGrant -> stringResource(R.string.userscript_import_error_unsupported_grant)
    GreasyForkInstallError.DependencyFailed -> stringResource(R.string.greasy_fork_error_dependency)
    GreasyForkInstallError.SaveFailed -> stringResource(R.string.userscript_import_error_save_failed)
}

internal object GreasyForkInstallTestTags {
    const val Dialog = "greasy_fork_install_dialog"
    const val SourceDialog = "greasy_fork_source_dialog"
    const val Source = "greasy_fork_source"
    const val ShowSource = "greasy_fork_show_source"
    const val Install = "greasy_fork_install"
    const val Dismiss = "greasy_fork_dismiss"
}
