package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.MAX_TABS
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutPublisher
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutRules
import dev.sk2andy.materialbrowser.browser.integration.LauncherShortcutTarget

internal class LauncherShortcutIntentHandler(
    private val context: Context,
    private val browserController: BrowserController,
    private val publisher: LauncherShortcutPublisher,
    private val onNavigationRequested: () -> Unit,
    private val onAddressEditorRequested: () -> Unit,
) {
    fun open(intent: Intent): Boolean {
        val target = LauncherShortcutRules.resolve(
            action = intent.action,
            profileId = intent.getStringExtra(LauncherShortcutRules.EXTRA_PROFILE_ID),
            availableProfileIds = browserController.localBrowserProfiles
                .mapTo(mutableSetOf()) { it.id },
            profilesEnabled = browserController.profilesEnabled,
        ) ?: return if (
            intent.action == LauncherShortcutRules.ACTION_OPEN_PROFILE ||
            intent.action == LauncherShortcutRules.ACTION_NEW_TAB_IN_PROFILE
        ) {
            Toast.makeText(context, R.string.command_feedback_rejected, Toast.LENGTH_SHORT).show()
            true
        } else {
            false
        }
        if (target == LauncherShortcutTarget.OpenApp) return true
        if (target is LauncherShortcutTarget.Profile) {
            browserController.requestProfileSelection(target.profileId) { selected ->
                if (selected) {
                    browserController.leaveSiteCapsule()
                    reportCompleted(target)
                }
            }
            return true
        }
        if (target is LauncherShortcutTarget.NewTabInProfile) {
            createTabInProfile(target.profileId) { completed ->
                if (completed) reportCompleted(target)
            }
            return true
        }
        val completed = when (target) {
            LauncherShortcutTarget.OpenApp -> error("OpenApp handled above")
            LauncherShortcutTarget.NewTab -> createTab(isIncognito = false)
            LauncherShortcutTarget.NewPrivateTab,
            LauncherShortcutTarget.NewPrivateTabInCurrentProfile,
            -> {
                createPrivateTab(
                    fallbackToFirstLocalProfile =
                        target == LauncherShortcutTarget.NewPrivateTab,
                ) { privateTabCreated ->
                    if (privateTabCreated) reportCompleted(target)
                }
                return true
            }
            is LauncherShortcutTarget.Profile,
            is LauncherShortcutTarget.NewTabInProfile,
            -> error("Profile targets handled above")
        }
        if (completed) reportCompleted(target)
        return true
    }

    private fun createPrivateTab(
        fallbackToFirstLocalProfile: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        val targetProfileId = LauncherShortcutRules.privateTargetProfileId(
            profiles = browserController.profiles.toList(),
            activeProfileId = browserController.activeProfileId,
            profileIsolationSupported = browserController.isProfileIsolationSupported,
            fallbackToFirstLocalProfile = fallbackToFirstLocalProfile,
        )
        if (targetProfileId == null) {
            Toast.makeText(
                context,
                R.string.toast_incognito_unsupported,
                Toast.LENGTH_SHORT,
            ).show()
            onComplete(false)
            return
        }
        if (!browserController.prepareTabCreation(targetProfileId)) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_tab_limit_reached, MAX_TABS),
                Toast.LENGTH_SHORT,
            ).show()
            onComplete(false)
            return
        }
        if (targetProfileId != browserController.activeProfileId ||
            targetProfileId in browserController.lockedProfileIds
        ) {
            browserController.requestProfileSelection(targetProfileId) { selected ->
                onComplete(selected && createTab(isIncognito = true))
            }
        } else {
            onComplete(createTab(isIncognito = true))
        }
    }

    private fun createTabInProfile(profileId: String, onComplete: (Boolean) -> Unit) {
        if (!browserController.prepareTabCreation()) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_tab_limit_reached, MAX_TABS),
                Toast.LENGTH_SHORT,
            ).show()
            onComplete(false)
            return
        }
        val targetHasTab = browserController.tabs.any { tab -> tab.profileId == profileId }
        if (profileId != browserController.activeProfileId ||
            profileId in browserController.lockedProfileIds
        ) {
            browserController.requestProfileSelection(profileId) { selected ->
                if (!selected) {
                    onComplete(false)
                } else if (!targetHasTab) {
                    browserController.leaveSiteCapsule()
                    onAddressEditorRequested()
                    onComplete(true)
                } else {
                    browserController.leaveSiteCapsule()
                    onComplete(createTab(isIncognito = false))
                }
            }
            return
        }
        browserController.leaveSiteCapsule()
        onComplete(createTab(isIncognito = false))
    }

    private fun reportCompleted(target: LauncherShortcutTarget) {
        publisher.reportUsed(target)
        onNavigationRequested()
    }

    private fun createTab(isIncognito: Boolean): Boolean {
        val previousTabId = browserController.selectedTabId
        val createdTabId = browserController.createTab(isIncognito = isIncognito)
        if (createdTabId == previousTabId) return false
        onAddressEditorRequested()
        return true
    }
}
