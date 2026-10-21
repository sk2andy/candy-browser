package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.SiteConnectionKind
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.shared.ui.AddressBarFieldContent
import dev.sk2andy.materialbrowser.shared.ui.BrowserCustomAction
import dev.sk2andy.materialbrowser.shared.ui.BrowserCustomActionButton
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceRole
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens

internal data class BrowserMenuSheetAddressSnapshot(
    val displayText: String,
    val actionLayout: AddressBarActionLayout,
    val actionState: AddressBarActionState,
    val siteAvailable: Boolean,
    val activityVisible: Boolean,
    val connectionKind: SiteConnectionKind,
    val blockedCount: Int,
    val tabId: String,
)

@Composable
internal fun BrowserMenuSheetAddressBar(
    displayText: String,
    actionLayout: AddressBarActionLayout,
    actionState: AddressBarActionState,
    actionCallbacks: AddressBarActionCallbacks,
    commit: (() -> Unit) -> Unit,
    onAddress: () -> Unit,
    onDismissRequest: () -> Unit,
    connectionContent: @Composable () -> Unit,
    customSlots: Map<Int, BrowserCustomAction?> = emptyMap(),
    reservedSlots: Int = 0,
    onCustomAction: (String) -> Unit = {},
) {
    val tokens = browserChromeSurfaceTokens(BrowserChromeSurfaceRole.AddressBar)
    val callbacks = actionCallbacks.committingWith(commit)
    val slots = CustomWorkflowAddressSlotRules.slots(actionLayout, customSlots.keys, reservedSlots)
    Surface(
        shape = RoundedCornerShape(tokens.cornerRadius),
        color = tokens.fieldContainerColor,
        contentColor = tokens.fieldContentColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            slots.beforeAddress.forEach { slot ->
                if (slot.overridden) {
                    BrowserCustomActionButton(customSlots[slot.index], { id -> commit { onCustomAction(id) } })
                } else if (slot.builtinAction != null) {
                    AddressBarActionButton(slot.builtinAction, actionState, callbacks)
                } else {
                    Box(Modifier.size(48.dp))
                }
            }
            AddressBarFieldContent(
                editing = false,
                editValue = TextFieldValue(),
                onEditValueChange = {},
                ghostCompletion = null,
                placeholder = stringResource(R.string.search_or_enter_url),
                displayText = displayText,
                onSubmitAddress = {},
                modifier = Modifier.weight(1f),
                displayTextModifier = Modifier.addressBarPressActions(
                    longPressEnabled = false,
                    onClick = { commit(onAddress) },
                    onLongPress = {},
                    longPressLabel = "",
                ),
                displayTrailingContent = connectionContent,
                contentColor = tokens.fieldContentColor,
                secondaryContentColor = tokens.fieldSecondaryContentColor,
                cursorColor = tokens.accentColor,
            )
            slots.afterAddress.forEach { slot ->
                if (slot.overridden) {
                    BrowserCustomActionButton(customSlots[slot.index], { id -> commit { onCustomAction(id) } })
                } else if (slot.builtinAction != null) {
                    AddressBarActionButton(slot.builtinAction, actionState, callbacks)
                } else {
                    Box(Modifier.size(48.dp))
                }
            }
            IconButton(onClick = onDismissRequest) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.browser_menu_dismiss),
                )
            }
        }
    }
}

private fun AddressBarActionCallbacks.committingWith(
    commit: (() -> Unit) -> Unit,
): AddressBarActionCallbacks = copy(
    onTabs = { commit(onTabs) },
    onToggleFavorite = { commit(onToggleFavorite) },
    onTogglePinned = { commit(onTogglePinned) },
    onDesktopViewChange = { checked -> commit { onDesktopViewChange(checked) } },
    onForceVerticalScrollingChange = { checked -> commit { onForceVerticalScrollingChange(checked) } },
    onReaderStudio = { commit(onReaderStudio) },
    onFindInPage = { commit(onFindInPage) },
    onShare = { commit(onShare) },
    onPrint = { commit(onPrint) },
    onNewTab = { commit(onNewTab) },
    onHome = { commit(onHome) },
    onReloadOrStop = { commit(onReloadOrStop) },
    onCloseTab = { commit(onCloseTab) },
    onBack = { commit(onBack) },
    onForward = { commit(onForward) },
    onParkRight = { commit(onParkRight) },
    onNewTabButtonBounds = {},
)
