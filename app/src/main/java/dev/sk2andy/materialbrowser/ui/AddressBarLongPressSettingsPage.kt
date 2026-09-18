package dev.sk2andy.materialbrowser.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.browser.AddressBarLongPressAction
import dev.sk2andy.materialbrowser.shared.browser.AddressBarLongPressActionSection
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal object AddressBarLongPressSettingsTestTags {
    fun action(action: AddressBarLongPressAction): String =
        "address_bar_long_press_${action.stableId}"
}

@Composable
internal fun AddressBarLongPressSettingsPage(
    selectedAction: AddressBarLongPressAction,
    onActionSelected: (AddressBarLongPressAction) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.settings_address_bar_long_press_title),
        onBack = onBack,
    ) {
        Text(
            text = stringResource(R.string.settings_address_bar_long_press_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        AddressBarLongPressActionSection.entries.forEachIndexed { sectionIndex, section ->
            SettingsSectionTitle(stringResource(section.labelRes()))
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column {
                    val actions = AddressBarLongPressAction.entries.filter { action ->
                        action.section == section
                    }
                    actions.forEachIndexed { actionIndex, action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddressBarLongPressSettingsTestTags.action(action))
                                .selectable(
                                    selected = selectedAction == action,
                                    role = Role.RadioButton,
                                    onClick = { onActionSelected(action) },
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedAction == action,
                                onClick = null,
                            )
                            Text(
                                text = stringResource(action.labelRes()),
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        if (actionIndex != actions.lastIndex) HorizontalDivider()
                    }
                }
            }
            if (sectionIndex != AddressBarLongPressActionSection.entries.lastIndex) {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@StringRes
internal fun AddressBarLongPressAction.labelRes(): Int = when (this) {
    AddressBarLongPressAction.OpenReader -> R.string.reader_open_action
    AddressBarLongPressAction.SaveReaderOffline -> R.string.reader_save_offline
    AddressBarLongPressAction.FindInPage -> R.string.action_find_in_page
    AddressBarLongPressAction.TranslatePage -> R.string.action_translate_page
    AddressBarLongPressAction.ToggleDesktopView -> R.string.action_desktop_view
    AddressBarLongPressAction.CopyUrl -> R.string.external_link_preview_copy_link
    AddressBarLongPressAction.ShareUrl -> R.string.action_share
    AddressBarLongPressAction.Print -> R.string.action_print
    AddressBarLongPressAction.SendToAssistant -> R.string.action_summarize_with_assistant
    AddressBarLongPressAction.ToggleFavorite -> R.string.action_favorite
    AddressBarLongPressAction.TogglePinned -> R.string.action_pin_tab
    AddressBarLongPressAction.Reload -> R.string.action_reload
    AddressBarLongPressAction.GoBack -> R.string.action_back
    AddressBarLongPressAction.DuplicateTab -> R.string.action_duplicate_tab
    AddressBarLongPressAction.SnoozeTab -> R.string.action_snooze_tab
    AddressBarLongPressAction.MoveToProfile -> R.string.action_move_tab_to_profile
    AddressBarLongPressAction.NewTab -> R.string.cd_new_tab
    AddressBarLongPressAction.NewPrivateTab -> R.string.command_new_incognito_tab_name
    AddressBarLongPressAction.OpenHistory -> R.string.action_history
    AddressBarLongPressAction.ParkAddressBar -> R.string.action_dock_address_bar
    AddressBarLongPressAction.OpenCandyTrail -> R.string.action_open_candy_trail
    AddressBarLongPressAction.CreateSiteCapsule -> R.string.action_add_site_capsule
}

@StringRes
internal fun AddressBarLongPressAction.runtimeLabelRes(
    isFavorite: Boolean,
    isPinned: Boolean,
    isDesktopView: Boolean,
): Int = when (this) {
    AddressBarLongPressAction.ToggleFavorite -> if (isFavorite) {
        R.string.action_remove_favorite
    } else {
        R.string.action_add_favorite
    }
    AddressBarLongPressAction.TogglePinned -> if (isPinned) {
        R.string.action_remove_pin
    } else {
        R.string.action_pin_tab
    }
    AddressBarLongPressAction.ToggleDesktopView -> if (isDesktopView) {
        R.string.action_mobile_view
    } else {
        R.string.action_desktop_view
    }
    else -> labelRes()
}

@StringRes
private fun AddressBarLongPressActionSection.labelRes(): Int = when (this) {
    AddressBarLongPressActionSection.Reading -> R.string.reader_studio_title
    AddressBarLongPressActionSection.Navigation -> R.string.browser_menu_navigation_group
    AddressBarLongPressActionSection.Page -> R.string.browser_menu_page_group
    AddressBarLongPressActionSection.Tab -> R.string.settings_section_tabs
    AddressBarLongPressActionSection.Browser -> R.string.browser_menu_browser_group
    AddressBarLongPressActionSection.Candy -> R.string.settings_menu_section_candy
}
