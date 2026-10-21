package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.ui.SettingsDestination

enum class SettingsHomeIcon {
    Search,
    Sync,
    Tabs,
    Gestures,
    TabsAndGestures,
    Appearance,
    AppIcon,
    Browser,
    Player,
    Downloads,
    RssFeeds,
    Userscripts,
    FirefoxExtensions,
    SiteCapsules,
    ProtectionAndData,
    HistoryAndData,
    DeveloperOptions,
    AboutLegal,
}

enum class SettingsHomeLabel {
    Title,
    Back,
    BrowsingGroup,
    TabsGroup,
    ControlsGroup,
    PersonalizationGroup,
    ExtensionsGroup,
    PrivacyDataGroup,
    AboutGroup,
    SearchTitle,
    SearchSummary,
    SyncTitle,
    SyncSummary,
    TabsAndGesturesTitle,
    TabsAndGesturesSummary,
    TabsTitle,
    TabsSummary,
    GesturesTitle,
    GesturesSummary,
    AppearanceTitle,
    AppearanceSummary,
    AppIconTitle,
    AppIconSummary,
    BrowserTitle,
    BrowserSummary,
    PlayerTitle,
    PlayerSummary,
    DownloadsTitle,
    RssFeedsTitle,
    RssFeedsSummary,
    UserscriptsTitle,
    UserscriptsSummary,
    FirefoxExtensionsTitle,
    FirefoxExtensionsSummary,
    SiteCapsulesTitle,
    SiteCapsulesSummary,
    ProtectionAndDataTitle,
    ProtectionAndDataSummary,
    HistoryAndDataTitle,
    HistoryAndDataSummary,
    DeveloperOptionsTitle,
    DeveloperOptionsSummary,
    UnlockDeveloperOptions,
    AboutLegalTitle,
    AboutLegalSummary,
}

enum class SettingsHomeGroup(val label: SettingsHomeLabel) {
    Browsing(SettingsHomeLabel.BrowsingGroup),
    Tabs(SettingsHomeLabel.TabsGroup),
    Controls(SettingsHomeLabel.ControlsGroup),
    Personalization(SettingsHomeLabel.PersonalizationGroup),
    Extensions(SettingsHomeLabel.ExtensionsGroup),
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
        separateTabControls: Boolean = false,
        hasAppIconSetting: Boolean = false,
        separateHistoryData: Boolean = false,
        separateExtensionsGroup: Boolean = false,
    ): List<SettingsHomeItem> = buildList {
        val extensionsGroup = if (separateExtensionsGroup) {
            SettingsHomeGroup.Extensions
        } else {
            SettingsHomeGroup.Personalization
        }
        add(item(SettingsHomeGroup.Browsing, SettingsDestination.Search, SettingsHomeIcon.Search, SettingsHomeLabel.SearchTitle, SettingsHomeLabel.SearchSummary))
        add(item(SettingsHomeGroup.Browsing, SettingsDestination.Browser, SettingsHomeIcon.Browser, SettingsHomeLabel.BrowserTitle, SettingsHomeLabel.BrowserSummary))
        add(item(SettingsHomeGroup.Browsing, SettingsDestination.Player, SettingsHomeIcon.Player, SettingsHomeLabel.PlayerTitle, SettingsHomeLabel.PlayerSummary))
        add(item(SettingsHomeGroup.Browsing, SettingsDestination.Downloads, SettingsHomeIcon.Downloads, SettingsHomeLabel.DownloadsTitle, null))
        add(item(SettingsHomeGroup.Browsing, SettingsDestination.RssFeeds, SettingsHomeIcon.RssFeeds, SettingsHomeLabel.RssFeedsTitle, SettingsHomeLabel.RssFeedsSummary))
        if (separateTabControls) {
            add(item(SettingsHomeGroup.Tabs, SettingsDestination.Tabs, SettingsHomeIcon.Tabs, SettingsHomeLabel.TabsTitle, SettingsHomeLabel.TabsSummary))
            add(item(SettingsHomeGroup.Controls, SettingsDestination.Gestures, SettingsHomeIcon.Gestures, SettingsHomeLabel.GesturesTitle, SettingsHomeLabel.GesturesSummary))
        } else {
            add(item(SettingsHomeGroup.Browsing, SettingsDestination.TabsAndGestures, SettingsHomeIcon.TabsAndGestures, SettingsHomeLabel.TabsAndGesturesTitle, SettingsHomeLabel.TabsAndGesturesSummary))
        }
        add(item(SettingsHomeGroup.Personalization, SettingsDestination.Appearance, SettingsHomeIcon.Appearance, SettingsHomeLabel.AppearanceTitle, SettingsHomeLabel.AppearanceSummary))
        if (hasAppIconSetting) {
            add(item(SettingsHomeGroup.Personalization, SettingsDestination.AppIcon, SettingsHomeIcon.AppIcon, SettingsHomeLabel.AppIconTitle, SettingsHomeLabel.AppIconSummary))
        }
        add(item(extensionsGroup, SettingsDestination.SiteCapsules, SettingsHomeIcon.SiteCapsules, SettingsHomeLabel.SiteCapsulesTitle, SettingsHomeLabel.SiteCapsulesSummary))
        add(item(extensionsGroup, SettingsDestination.Userscripts, SettingsHomeIcon.Userscripts, SettingsHomeLabel.UserscriptsTitle, SettingsHomeLabel.UserscriptsSummary))
        if (hasFirefoxExtensions) {
            add(
                SettingsHomeItem(
                    group = extensionsGroup,
                    destination = null,
                    icon = SettingsHomeIcon.FirefoxExtensions,
                    title = SettingsHomeLabel.FirefoxExtensionsTitle,
                    summary = SettingsHomeLabel.FirefoxExtensionsSummary,
                    isFirefoxExtensionsAction = true,
                ),
            )
        }
        add(item(SettingsHomeGroup.PrivacyData, SettingsDestination.Sync, SettingsHomeIcon.Sync, SettingsHomeLabel.SyncTitle, SettingsHomeLabel.SyncSummary))
        add(item(SettingsHomeGroup.PrivacyData, SettingsDestination.ProtectionAndData, SettingsHomeIcon.ProtectionAndData, SettingsHomeLabel.ProtectionAndDataTitle, SettingsHomeLabel.ProtectionAndDataSummary))
        if (separateHistoryData) {
            add(item(SettingsHomeGroup.PrivacyData, SettingsDestination.HistoryAndData, SettingsHomeIcon.HistoryAndData, SettingsHomeLabel.HistoryAndDataTitle, SettingsHomeLabel.HistoryAndDataSummary))
        }
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
    isDestinationVisible: (SettingsDestination) -> Boolean = { true },
    separateTabControls: Boolean = false,
    hasAppIconSetting: Boolean = false,
    separateHistoryData: Boolean = false,
    separateExtensionsGroup: Boolean = false,
    searchContent: @Composable ColumnScope.() -> Unit = {},
    showHomeItems: Boolean = true,
) {
    SettingsPage(
        title = resources.text(SettingsHomeLabel.Title),
        backContentDescription = resources.text(SettingsHomeLabel.Back),
        onBack = onDismiss,
    ) {
        searchContent()
        if (!showHomeItems) return@SettingsPage
        val items = SettingsHomeRules.items(
            hasFirefoxExtensions = onOpenFirefoxExtensions != null,
            hasDeveloperOptions = developerOptionsUnlocked,
            separateTabControls = separateTabControls,
            hasAppIconSetting = hasAppIconSetting,
            separateHistoryData = separateHistoryData,
            separateExtensionsGroup = separateExtensionsGroup,
        ).filter { item -> item.destination?.let(isDestinationVisible) != false }
        val groups = items.fold(mutableListOf<MutableList<SettingsHomeItem>>()) { groups, item ->
            if (groups.lastOrNull()?.lastOrNull()?.group != item.group) groups.add(mutableListOf())
            groups.last().add(item)
            groups
        }
        groups.forEachIndexed { groupIndex, groupItems ->
            if (groupIndex != 0) Spacer(Modifier.height(24.dp))
            SettingsSectionTitle(resources.text(groupItems.first().group.label))
            SettingsPreferenceGroup(containerColor = linkContainerColor) {
                groupItems.forEachIndexed { index, item ->
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
                    if (index != groupItems.lastIndex) {
                        SettingsPreferenceDivider()
                    }
                }
            }
        }
    }
}
