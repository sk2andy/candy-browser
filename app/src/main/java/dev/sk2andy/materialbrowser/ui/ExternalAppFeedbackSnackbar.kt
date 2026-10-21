package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.ExternalAppFeedback
import dev.sk2andy.materialbrowser.browser.ExternalAppFeedbackStage

@Composable
internal fun ExternalAppFeedbackSnackbarEffect(
    controller: BrowserController,
    hostState: SnackbarHostState,
) {
    ExternalAppFeedbackSnackbarEffect(
        offer = controller.externalAppFeedback,
        hostState = hostState,
        onOpenInCandy = controller::openExternalAppInCandy,
        onAlwaysOpenInCandy = controller::confirmAlwaysOpenInCandy,
        onDismiss = controller::dismissExternalAppFeedback,
    )
}

@Composable
internal fun ExternalAppFeedbackSnackbarEffect(
    offer: ExternalAppFeedback?,
    hostState: SnackbarHostState,
    onOpenInCandy: (Long) -> Unit,
    onAlwaysOpenInCandy: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenInCandy by rememberUpdatedState(onOpenInCandy)
    val currentOnAlwaysOpenInCandy by rememberUpdatedState(onAlwaysOpenInCandy)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val confirmation = offer?.stage == ExternalAppFeedbackStage.ConfirmAlwaysInCandy
    val message = stringResource(
        when {
            !confirmation -> R.string.external_app_feedback_opened
            offer.isPrivate -> R.string.external_app_feedback_always_private
            else -> R.string.external_app_feedback_always
        },
    )
    val actionLabel = stringResource(
        if (confirmation) R.string.external_app_feedback_yes
        else R.string.external_app_feedback_open_in_candy,
    )
    val cancelLabel = stringResource(R.string.action_cancel)
    LaunchedEffect(offer, hostState, lifecycleOwner) {
        val feedback = offer ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val result = hostState.showSnackbar(
                ExternalAppFeedbackSnackbarVisuals(
                    message = message,
                    actionLabel = actionLabel,
                    cancelLabel = cancelLabel.takeIf { confirmation },
                ),
            )
            // Lifecycle cancellation leaves the offer intact until Candy returns.
            when {
                result != SnackbarResult.ActionPerformed -> currentOnDismiss(feedback.id)
                confirmation -> currentOnAlwaysOpenInCandy(feedback.id)
                else -> currentOnOpenInCandy(feedback.id)
            }
        }
    }
}

@Composable
internal fun BrowserFeedbackSnackbar(data: SnackbarData) {
    val visuals = data.visuals as? ExternalAppFeedbackSnackbarVisuals
    if (visuals == null || visuals.cancelLabel == null) {
        Snackbar(data)
        return
    }
    Snackbar(
        actionOnNewLine = true,
        action = {
            Row {
                TextButton(
                    onClick = data::dismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = SnackbarDefaults.actionColor,
                    ),
                ) {
                    Text(visuals.cancelLabel)
                }
                TextButton(
                    onClick = data::performAction,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = SnackbarDefaults.actionColor,
                    ),
                ) {
                    Text(visuals.actionLabel)
                }
            }
        },
    ) {
        Text(visuals.message)
    }
}

private data class ExternalAppFeedbackSnackbarVisuals(
    override val message: String,
    override val actionLabel: String,
    val cancelLabel: String?,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = cancelLabel == null
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}
