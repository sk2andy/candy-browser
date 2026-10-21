package dev.sk2andy.materialbrowser.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.CustomWorkflowIcons
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflow
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowIcon
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowPlacement
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowRules
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowStep
import dev.sk2andy.materialbrowser.browser.workflow.CustomWorkflowValidationFailure

internal fun CustomWorkflowIcon.imageVector(): ImageVector = when (this) {
    CustomWorkflowIcon.Download -> CustomWorkflowIcons.Download
    CustomWorkflowIcon.Reader -> CustomWorkflowIcons.Reader
    CustomWorkflowIcon.Favorite -> Icons.Default.Favorite
    CustomWorkflowIcon.Steps -> CustomWorkflowIcons.Steps
}

@StringRes
internal fun CustomWorkflowIcon.labelRes(): Int = when (this) {
    CustomWorkflowIcon.Download -> R.string.workflow_icon_download
    CustomWorkflowIcon.Reader -> R.string.workflow_icon_reader
    CustomWorkflowIcon.Favorite -> R.string.workflow_icon_favorite
    CustomWorkflowIcon.Steps -> R.string.workflow_icon_steps
}

@StringRes
internal fun CustomWorkflowStep.labelRes(): Int = when (this) {
    CustomWorkflowStep.SaveReaderOffline -> R.string.workflow_step_offline
    CustomWorkflowStep.AddFavorite -> R.string.workflow_step_favorite
    CustomWorkflowStep.PinTab -> R.string.workflow_step_pin
    CustomWorkflowStep.CopyUrl -> R.string.workflow_step_copy
    CustomWorkflowStep.OpenReader -> R.string.workflow_step_reader
    CustomWorkflowStep.CloseTab -> R.string.workflow_step_close
}

@StringRes
internal fun CustomWorkflowStep.summaryRes(): Int = when (this) {
    CustomWorkflowStep.SaveReaderOffline -> R.string.workflow_step_offline_summary
    CustomWorkflowStep.AddFavorite -> R.string.workflow_step_favorite_summary
    CustomWorkflowStep.PinTab -> R.string.workflow_step_pin_summary
    CustomWorkflowStep.CopyUrl -> R.string.workflow_step_copy_summary
    CustomWorkflowStep.OpenReader -> R.string.workflow_step_reader_summary
    CustomWorkflowStep.CloseTab -> R.string.workflow_step_close_summary
}

@StringRes
internal fun CustomWorkflowPlacement.labelRes(): Int = when (this) {
    CustomWorkflowPlacement.MenuShortcut -> R.string.workflow_placement_shortcut
    CustomWorkflowPlacement.MenuFooter -> R.string.workflow_placement_footer
    CustomWorkflowPlacement.AddressBar -> R.string.workflow_placement_address
    CustomWorkflowPlacement.LinkLongPress -> R.string.workflow_placement_long_press
    CustomWorkflowPlacement.LinkPeekSwipe -> R.string.workflow_placement_peek_swipe
    CustomWorkflowPlacement.LinkPeekToolbar -> R.string.workflow_placement_peek_toolbar
}

@Composable
internal fun CustomWorkflowPlacement.slotLabel(slot: Int): String = when {
    this == CustomWorkflowPlacement.LinkPeekToolbar -> stringResource(
        when (slot) {
            0 -> R.string.workflow_peek_slot_first
            1 -> R.string.workflow_peek_slot_second
            else -> R.string.workflow_peek_slot_last
        },
    )
    slotCount == 1 -> stringResource(labelRes())
    else -> stringResource(R.string.workflow_assignment_slot, slot + 1)
}

@Composable
internal fun CustomWorkflow.summary(): String = steps.map { stringResource(it.labelRes()) }.joinToString(" → ")

@Composable
internal fun CustomWorkflowValidationFailure.message(): String = when (this) {
    CustomWorkflowValidationFailure.EmptyName,
    CustomWorkflowValidationFailure.NameTooLong,
    CustomWorkflowValidationFailure.InvalidName,
    -> stringResource(R.string.workflow_error_name, CustomWorkflowRules.MAX_NAME_LENGTH)
    CustomWorkflowValidationFailure.EmptySteps,
    CustomWorkflowValidationFailure.TooManySteps,
    -> stringResource(R.string.workflow_error_steps, CustomWorkflowRules.MAX_STEPS)
    CustomWorkflowValidationFailure.DuplicateStep -> stringResource(R.string.workflow_error_duplicate)
    CustomWorkflowValidationFailure.TerminalStepNotLast -> stringResource(R.string.workflow_error_terminal)
    CustomWorkflowValidationFailure.ConflictingSteps -> stringResource(R.string.workflow_error_conflict)
    CustomWorkflowValidationFailure.InvalidId -> stringResource(R.string.workflow_error_invalid)
}
