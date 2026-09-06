@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.commands.BrowserCommand
import dev.sk2andy.materialbrowser.browser.commands.CommandConfirmation
import dev.sk2andy.materialbrowser.browser.commands.CommandCookieScope
import dev.sk2andy.materialbrowser.browser.commands.CommandSuggestion
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.reader.ReaderExtractionFailure
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult

@Composable
internal fun BrowserTransientOverlays(
    controller: BrowserController,
    clearDialogVisible: Boolean,
    onClearDialogDismiss: () -> Unit,
    pendingCommand: CommandSuggestion?,
    onPendingCommandDismiss: () -> Unit,
    onPendingCommandConfirmed: (BrowserCommand) -> Unit,
    addressNewTabButtonBounds: Rect?,
    pendingCapsuleDelete: SiteCapsule?,
    onPendingCapsuleDeleteDismiss: () -> Unit,
    onFavoriteLink: (String, String?) -> Unit,
    onSnoozeLink: (String, String?, String) -> Unit,
) {
    val rootView = LocalView.current
    val readerSavedMessage = stringResource(R.string.reader_saved_offline_confirmation)
    val readerUnsupportedMessage = stringResource(R.string.reader_extraction_unsupported)
    val readerEmptyMessage = stringResource(R.string.reader_extraction_empty)
    val readerInvalidMessage = stringResource(R.string.reader_extraction_invalid)
    if (clearDialogVisible) {
        AlertDialog(
            onDismissRequest = { onClearDialogDismiss() },
            title = { Text(stringResource(R.string.clear_data_title)) },
            text = { Text(stringResource(R.string.clear_data_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        controller.clearBrowsingData()
                        onClearDialogDismiss()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { onClearDialogDismiss() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingCommand?.let { pending ->
        val isCookieCommand = pending.command.confirmation == CommandConfirmation.ClearCookies
        AlertDialog(
            onDismissRequest = {
                onPendingCommandDismiss()
            },
            title = {
                Text(
                    stringResource(
                        if (isCookieCommand) {
                            R.string.command_cookie_confirm_title
                        } else {
                            R.string.command_duplicates_confirm_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    if (isCookieCommand) {
                        stringResource(
                            when (controller.commandCookieScope) {
                                CommandCookieScope.SharedRegularProfile ->
                                    R.string.command_cookie_confirm_regular
                                CommandCookieScope.IsolatedRegularProfile ->
                                    R.string.command_cookie_confirm_isolated
                                CommandCookieScope.PrivateProfile ->
                                    R.string.command_cookie_confirm_private
                                CommandCookieScope.AllWebViews ->
                                    R.string.command_cookie_confirm_all
                            },
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.command_duplicates_confirm_message,
                            pending.command.duplicateCount,
                            pending.command.duplicateCount,
                        )
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPendingCommandConfirmed(pending.command)
                    },
                ) {
                    Text(
                        stringResource(
                            if (isCookieCommand) {
                                R.string.action_delete
                            } else {
                                R.string.command_close_duplicates_name
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onPendingCommandDismiss()
                    },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (controller.contentActions.isLinkPeekVisible) {
        val linkTarget = controller.contentActions.target
        LinkPeekOverlay(
            url = requireNotNull(linkTarget?.linkUrl),
            progress = controller.contentActions.linkPeekProgress,
            armed = controller.contentActions.isLinkPeekArmed,
            committing = controller.contentActions.isLinkPeekCommitting,
            newTabTargetBounds = addressNewTabButtonBounds,
            createPreviewWebView = { onProgressChanged, onCommittedUrlChanged ->
                controller.createLinkPeekPreviewWebView(
                    url = requireNotNull(linkTarget?.linkUrl),
                    onProgressChanged = onProgressChanged,
                    onCommittedUrlChanged = onCommittedUrlChanged,
                )
            },
            releasePreviewWebView = controller::releaseLinkPeekPreviewWebView,
            onCommitRequested = controller.contentActions::startLinkPeekCommit,
            onOpen = {
                rootView.performConfirmHaptic()
                controller.openContextLinkInBackground()
            },
            onOpenUrl = { currentUrl ->
                rootView.performConfirmHaptic()
                controller.openContextLinkInBackground(currentUrl)
            },
            onCopyLink = { currentUrl ->
                controller.contentActions.dismiss()
                controller.copyLink(currentUrl)
            },
            onOpenInPrivate = { currentUrl ->
                if (controller.openLinkInPrivate(currentUrl)) rootView.performConfirmHaptic()
            },
            onShare = { currentUrl ->
                controller.contentActions.dismiss()
                controller.shareLink(currentUrl)
            },
            onSaveReaderOffline = { currentUrl, previewWebView ->
                controller.saveLinkPeekToReader(previewWebView, currentUrl) { result ->
                    val message = when (result) {
                        is ReaderExtractionResult.Success -> readerSavedMessage
                        is ReaderExtractionResult.Failure -> when (result.reason) {
                            ReaderExtractionFailure.UnsupportedPage -> readerUnsupportedMessage
                            ReaderExtractionFailure.EmptyArticle -> readerEmptyMessage
                            ReaderExtractionFailure.InvalidResponse -> readerInvalidMessage
                        }
                    }
                    Toast.makeText(rootView.context, message, Toast.LENGTH_SHORT).show()
                }
            },
            onFavorite = onFavoriteLink,
            onSnooze = { currentUrl, title ->
                controller.contextLinkSourceTabId?.let { sourceTabId ->
                    controller.contentActions.dismiss()
                    onSnoozeLink(currentUrl, title, sourceTabId)
                }
            },
            onOpenForeground = { currentUrl ->
                controller.openContextLinkInForeground(currentUrl)
            },
            actionLayout = controller.linkPeekActionLayout,
            isFavorite = controller::isFavorite,
            canSaveReaderOffline = controller.canPersistContextLink,
            canFavorite = controller.canPersistContextLink,
            canSnooze = controller.canSnoozeContextLink,
            canOpenInPrivate = controller.canOpenLinkInPrivate,
            onDownloadLink = controller::downloadContextLink,
            onDownloadImage = linkTarget.takeIf { it?.canDownloadImage == true }?.let {
                controller::downloadContextImage
            },
            onDismiss = controller.contentActions::dismiss,
        )
    } else if (controller.contentActions.isVisible) {
        WebContentContextSheet(
            target = controller.contentActions.target,
            onOpenLinkInBackground = controller::openContextLinkInBackground,
            onDownloadImage = controller::downloadContextImage,
            onDismiss = controller.contentActions::dismiss,
        )
    }

    pendingCapsuleDelete?.let { capsule ->
        AlertDialog(
            onDismissRequest = { onPendingCapsuleDeleteDismiss() },
            title = { Text(stringResource(R.string.capsule_delete_title)) },
            text = {
                Text(
                    stringResource(
                        if (capsule.ownsDedicatedProfile) {
                            R.string.capsule_delete_dedicated_message
                        } else {
                            R.string.capsule_delete_message
                        },
                    ),
                )
            },
            confirmButton = {
                if (capsule.ownsDedicatedProfile) {
                    Button(
                        onClick = {
                            controller.deleteSiteCapsuleAsync(capsule.id, true) {}
                            onPendingCapsuleDeleteDismiss()
                        },
                    ) { Text(stringResource(R.string.capsule_delete_with_profile)) }
                } else {
                    Button(
                        onClick = {
                            controller.deleteSiteCapsuleAsync(capsule.id, false) {}
                            onPendingCapsuleDeleteDismiss()
                        },
                    ) { Text(stringResource(R.string.action_delete)) }
                }
            },
            dismissButton = {
                Row {
                    if (capsule.ownsDedicatedProfile) {
                        TextButton(
                            onClick = {
                                controller.deleteSiteCapsuleAsync(capsule.id, false) {}
                                onPendingCapsuleDeleteDismiss()
                            },
                        ) { Text(stringResource(R.string.capsule_delete_only)) }
                    }
                    TextButton(onClick = { onPendingCapsuleDeleteDismiss() }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
        )
    }
}
