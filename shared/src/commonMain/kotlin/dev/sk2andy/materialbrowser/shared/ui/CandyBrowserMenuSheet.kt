package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CandyBrowserMenuSheet(
    snapshot: BrowserViewportSnapshot,
    actionSink: BrowserViewportActionSink,
) {
    val effects = LocalCandyBrowserChromeEffects.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.88f }
    var committing by remember { mutableStateOf(false) }
    fun commit(action: () -> Unit) {
        if (committing) return
        committing = true
        keyboard?.hide()
        scope.launch {
            sheetState.hide()
            actionSink.dismissBrowserMenu()
            action()
        }
    }
    CandyModalSheet(
        onDismissRequest = actionSink::dismissBrowserMenu,
        sheetState = sheetState,
        dragHandle = null,
        showPlatformHandle = false,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    ) {
        BrowserMenuBottomSheetContent(
            expanded = true,
            items = snapshot.menuItems,
            contentLayout = snapshot.menuLayout.bottomSheetContentLayout,
            toolbarEntries = snapshot.menuLayout.bottomSheetToolbarEntries,
            resources = CandyBrowserMainMenuResources,
            effects = effects.mainMenu,
            snoozedTabCount = snapshot.snoozedTabCount,
            quickActionsTitle = "Schnellzugriff",
            pageActionsTitle = "Seitenaktionen",
            pageControlsTitle = "Seitensteuerung",
            emptySectionMessage = "Hier ist noch Platz für deine Favoriten. Aktiviere Menüeinträge in den Einstellungen.",
            emptySectionActionLabel = "Einstellungen öffnen",
            onOpenMenuSettings = { commit { actionSink.showSettings() } },
            handleDescription = "Menü schließen",
            handleModifier = Modifier.clickable { commit {} },
            addressBar = {
                CandyMenuAddressField(snapshot.address) { address ->
                    commit {
                        actionSink.addressChanged(address)
                        actionSink.perform(CandyBrowserUiAction.Navigate)
                    }
                }
            },
            onCommand = { item -> commit { actionSink.performMenu(item) } },
            onToggle = actionSink::performMenu,
            onExtensionCommit = ::commit,
            modifier = Modifier.fillMaxWidth().height(height),
        )
    }
}

@Composable
private fun CandyMenuAddressField(address: String, onSubmit: (String) -> Unit) {
    var draft by remember(address) { mutableStateOf(TextFieldValue(address)) }
    var editing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val effects = LocalCandyBrowserChromeEffects.current
    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }
    effects.addressFieldSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        morphProgress = 0f,
    ) {
        AddressBarFieldContent(
            editing = editing,
            editValue = draft,
            onEditValueChange = { draft = it },
            ghostCompletion = null,
            placeholder = "Adresse oder Suche",
            displayText = address.ifBlank { "Adresse oder Suche" },
            onSubmitAddress = onSubmit,
            modifier = Modifier.fillMaxWidth().clickable { editing = true },
            editorModifier = Modifier.focusRequester(focusRequester),
        )
    }
}
