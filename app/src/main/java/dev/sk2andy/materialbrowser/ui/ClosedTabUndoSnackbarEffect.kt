package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController

@Composable
internal fun ClosedTabUndoSnackbarEffect(
    controller: BrowserController,
    hostState: SnackbarHostState,
) {
    val offer = controller.closedTabUndoOffer
    val message = stringResource(R.string.tab_closed)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(offer) {
        val token = offer ?: return@LaunchedEffect
        hostState.currentSnackbarData?.dismiss()
        try {
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) controller.undoClosedTab(token)
        } finally {
            controller.dismissClosedTabUndo(token)
        }
    }
}
