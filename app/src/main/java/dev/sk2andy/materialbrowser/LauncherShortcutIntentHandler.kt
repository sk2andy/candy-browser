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
        ) ?: return if (intent.action == LauncherShortcutRules.ACTION_OPEN_PROFILE) {
            Toast.makeText(context, R.string.command_feedback_rejected, Toast.LENGTH_SHORT).show()
            true
        } else {
            false
        }
        val completed = when (target) {
            LauncherShortcutTarget.NewTab -> createTab(isIncognito = false)
            LauncherShortcutTarget.NewPrivateTab -> createPrivateTab()
            is LauncherShortcutTarget.Profile -> {
                val selected = target.profileId == browserController.activeProfileId ||
                    browserController.selectProfile(target.profileId)
                if (selected) browserController.leaveSiteCapsule()
                selected
            }
        }
        if (completed) {
            publisher.reportUsed(target)
            onNavigationRequested()
        }
        return true
    }

    private fun createPrivateTab(): Boolean {
        val targetProfileId = LauncherShortcutRules.privateTargetProfileId(
            profiles = browserController.profiles.toList(),
            activeProfileId = browserController.activeProfileId,
            profileIsolationSupported = browserController.isProfileIsolationSupported,
        )
        if (targetProfileId == null) {
            Toast.makeText(
                context,
                R.string.toast_incognito_unsupported,
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        if (!browserController.prepareTabCreation(targetProfileId)) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_tab_limit_reached, MAX_TABS),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        if (
            targetProfileId != browserController.activeProfileId &&
            !browserController.selectProfile(targetProfileId)
        ) {
            return false
        }
        return createTab(isIncognito = true)
    }

    private fun createTab(isIncognito: Boolean): Boolean {
        val previousTabId = browserController.selectedTabId
        val createdTabId = browserController.createTab(isIncognito = isIncognito)
        if (createdTabId == previousTabId) return false
        onAddressEditorRequested()
        return true
    }
}
