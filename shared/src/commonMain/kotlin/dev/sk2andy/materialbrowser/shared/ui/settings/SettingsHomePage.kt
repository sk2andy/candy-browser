package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.ui.SettingsDestination

enum class SettingsHomeIcon {
    Search,
    Sync,
    TabsAndGestures,
    Appearance,
    Browser,
    Downloads,
    Userscripts,
    FirefoxExtensions,
    SiteCapsules,
    ProtectionAndData,
    DeveloperOptions,
    AboutLegal,
}

enum class SettingsHomeLabel {
    Title,
    Back,
    BrowsingGroup,
    PersonalizationGroup,
    PrivacyDataGroup,
    AboutGroup,
    SearchTitle,
    SearchSummary,
    SyncTitle,
    SyncSummary,
    TabsAndGesturesTitle,
    TabsAndGesturesSummary,
    AppearanceTitle,
    AppearanceSummary,
    BrowserTitle,
    BrowserSummary,
    DownloadsTitle,
    UserscriptsTitle,
    UserscriptsSummary,
    FirefoxExtensionsTitle,
    FirefoxExtensionsSummary,
    SiteCapsulesTitle,
    SiteCapsulesSummary,
    ProtectionAndDataTitle,
    ProtectionAndDataSummary,
    DeveloperOptionsTitle,
    DeveloperOptionsSummary,
    UnlockDeveloperOptions,
    AboutLegalTitle,
    AboutLegalSummary,
}

enum class SettingsHomeGroup(val label: SettingsHomeLabel) {
    Browsing(SettingsHomeLabel.BrowsingGroup),
    Personalization(SettingsHomeLabel.PersonalizationGroup),
    PrivacyData(SettingsHomeLabel.PrivacyDataGroup),
    About(SettingsHomeLabel.AboutGroup),
}

interface SettingsHomeResources {
    @Composable
    fun text(label: SettingsHomeLabel): String
}

data class SettingsHomeItem(
    val group: SettingsHomeGroup,
    val destination: SettingsDestination?,
    val icon: SettingsHomeIcon,
    val title: SettingsHomeLabel,
    val summary: SettingsHomeLabel?,
    val isFirefoxExtensionsAction: Boolean = false,
)

object SettingsHomeRules {
    fun items(
        hasFirefoxExtensions: Boolean,
        hasDeveloperOptions: Boolean = false,
    ): List<SettingsHomeItem> = buildList {
        add(item(SettingsHomeGroup.Browsing, SettingsDestination.Search, SettingsHomeIcon.Search, SettingsHomeLabel.SearchTitle, SettingsHomeLabel.SearchSummary))
        add(item(SettingsHomeGroup.Browsing, SettingsDestination.TabsAndGestures, SettingsHomeIcon.TabsAndGestures, SettingsHomeLabel.TabsAndGesturesTitle, SettingsHomeLabel.TabsAndGesturesSummary))
        add(item(SettingsHomeGroup.Browsing, SettingsDestination.Browser, SettingsHomeIcon.Browser, SettingsHomeLabel.BrowserTitle, SettingsHomeLabel.BrowserSummary))
        add(item(SettingsHomeGroup.Browsing, SettingsDestination.Downloads, SettingsHomeIcon.Downloads, SettingsHomeLabel.DownloadsTitle, null))
        add(item(SettingsHomeGroup.Personalization, SettingsDestination.Appearance, SettingsHomeIcon.Appearance, SettingsHomeLabel.AppearanceTitle, SettingsHomeLabel.AppearanceSummary))
        add(item(SettingsHomeGroup.Personalization, SettingsDestination.SiteCapsules, SettingsHomeIcon.SiteCapsules, SettingsHomeLabel.SiteCapsulesTitle, SettingsHomeLabel.SiteCapsulesSummary))
        add(item(SettingsHomeGroup.Personalization, SettingsDestination.Userscripts, SettingsHomeIcon.Userscripts, SettingsHomeLabel.UserscriptsTitle, SettingsHomeLabel.UserscriptsSummary))
        if (hasFirefoxExtensions) {
            add(
                SettingsHomeItem(
                    group = SettingsHomeGroup.Personalization,
                    destination = null,
                    icon = SettingsHomeIcon.FirefoxExtensions,
                    title = SettingsHomeLabel.FirefoxExtensionsTitle,
                    summary = SettingsHomeLabel.FirefoxExtensionsSummary,
                    isFirefoxExtensionsAction = true,
                ),
            )
        }
        add(item(SettingsHomeGroup.PrivacyData, SettingsDestination.ProtectionAndData, SettingsHomeIcon.ProtectionAndData, SettingsHomeLabel.ProtectionAndDataTitle, SettingsHomeLabel.ProtectionAndDataSummary))
        add(item(SettingsHomeGroup.PrivacyData, SettingsDestination.Sync, SettingsHomeIcon.Sync, SettingsHomeLabel.SyncTitle, SettingsHomeLabel.SyncSummary))
        if (hasDeveloperOptions) {
            add(item(SettingsHomeGroup.About, SettingsDestination.DeveloperOptions, SettingsHomeIcon.DeveloperOptions, SettingsHomeLabel.DeveloperOptionsTitle, SettingsHomeLabel.DeveloperOptionsSummary))
        }
        add(item(SettingsHomeGroup.About, SettingsDestination.AboutLegal, SettingsHomeIcon.AboutLegal, SettingsHomeLabel.AboutLegalTitle, SettingsHomeLabel.AboutLegalSummary))
    }

    private fun item(
        group: SettingsHomeGroup,
        destination: SettingsDestination,
        icon: SettingsHomeIcon,
        title: SettingsHomeLabel,
        summary: SettingsHomeLabel?,
    ): SettingsHomeItem = SettingsHomeItem(
        group = group,
        destination = destination,
        icon = icon,
        title = title,
        summary = summary,
    )
}

@Composable
fun SettingsHomePage(
    downloadSummary: String,
    resources: SettingsHomeResources,
    linkContainerColor: Color,
    icon: @Composable (SettingsHomeIcon, Modifier, Color) -> Unit,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onDismiss: () -> Unit,
    onOpenFirefoxExtensions: (() -> Unit)? = null,
    developerOptionsUnlocked: Boolean = false,
    onUnlockDeveloperOptions: (() -> Unit)? = null,
    isDestinationEnabled: (SettingsDestination) -> Boolean = { true },
) {
    SettingsPage(
        title = resources.text(SettingsHomeLabel.Title),
        backContentDescription = resources.text(SettingsHomeLabel.Back),
        onBack = onDismiss,
    ) {
        val items = SettingsHomeRules.items(
            hasFirefoxExtensions = onOpenFirefoxExtensions != null,
            hasDeveloperOptions = developerOptionsUnlocked,
        )
        items.forEachIndexed { index, item ->
            if (index == 0 || item.group != items[index - 1].group) {
                if (index != 0) Spacer(Modifier.height(20.dp))
                SettingsSectionTitle(resources.text(item.group.label))
                Spacer(Modifier.height(8.dp))
            }
            val summary = item.summary
            val destination = item.destination
            SettingsLink(
                title = resources.text(item.title),
                subtitle = if (summary == null) {
                    downloadSummary
                } else {
                    resources.text(summary)
                },
                containerColor = linkContainerColor,
                icon = { modifier, tint -> icon(item.icon, modifier, tint) },
                enabled = item.isFirefoxExtensionsAction ||
                    destination?.let(isDestinationEnabled) == true,
                onLongClickLabel = resources.text(SettingsHomeLabel.UnlockDeveloperOptions)
                    .takeIf {
                        destination == SettingsDestination.AboutLegal &&
                            !developerOptionsUnlocked &&
                            onUnlockDeveloperOptions != null
                    },
                onLongClick = onUnlockDeveloperOptions.takeIf {
                    destination == SettingsDestination.AboutLegal && !developerOptionsUnlocked
                },
                onClick = {
                    if (item.isFirefoxExtensionsAction) {
                        onOpenFirefoxExtensions?.invoke()
                    } else {
                        destination?.let(onDestinationChanged)
                    }
                },
            )
            if (index != items.lastIndex && item.group == items[index + 1].group) {
                SettingsPageSpacer()
            }
        }
    }
}
