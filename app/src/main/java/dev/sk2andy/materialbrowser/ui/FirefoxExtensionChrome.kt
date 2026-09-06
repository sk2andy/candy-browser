package dev.sk2andy.materialbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionActionKey
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionActionState
import dev.sk2andy.materialbrowser.shared.ui.BrowserMenuRow
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal object FirefoxExtensionChromeTestTags {
    const val Actions = "firefox_extension_actions"
    const val SectionTitle = "firefox_extension_section_title"
    const val Popup = "firefox_extension_popup"

    fun action(saveableId: String): String = "firefox_extension_action_$saveableId"
}

/** Firefox popup hosted by Candy chrome; extension content never owns browser controls. */
@Composable
internal fun FirefoxExtensionChrome(controller: BrowserController) {
    controller.firefoxExtensionPopupView?.let { popupView ->
        Dialog(
            onDismissRequest = controller::dismissFirefoxExtensionPopup,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BackHandler(onBack = controller::dismissFirefoxExtensionPopup)
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(min = 160.dp, max = 560.dp)
                    .testTag(FirefoxExtensionChromeTestTags.Popup),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                AndroidView(
                    factory = { popupView },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
internal fun FirefoxExtensionMenuSection(
    actions: List<GeckoExtensionActionState>,
    onAction: (GeckoExtensionActionKey) -> Unit,
) {
    if (actions.isEmpty()) return

    val colors = MaterialTheme.colorScheme
    val outerCorners = MaterialTheme.shapes.medium
    val innerCorners = MaterialTheme.shapes.extraSmall
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.gecko_extensions_title),
        modifier = Modifier
            .padding(start = 8.dp, bottom = 6.dp)
            .testTag(FirefoxExtensionChromeTestTags.SectionTitle),
        style = MaterialTheme.typography.labelLarge,
        color = colors.primary,
        fontWeight = FontWeight.SemiBold,
    )
    Column(
        modifier = Modifier.testTag(FirefoxExtensionChromeTestTags.Actions),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        actions.forEachIndexed { index, action ->
            val shape = when {
                actions.size == 1 -> outerCorners
                index == 0 -> RoundedCornerShape(
                    topStart = outerCorners.topStart,
                    topEnd = outerCorners.topEnd,
                    bottomEnd = innerCorners.bottomEnd,
                    bottomStart = innerCorners.bottomStart,
                )
                index == actions.lastIndex -> RoundedCornerShape(
                    topStart = innerCorners.topStart,
                    topEnd = innerCorners.topEnd,
                    bottomEnd = outerCorners.bottomEnd,
                    bottomStart = outerCorners.bottomStart,
                )
                else -> innerCorners
            }
            BrowserMenuRow(
                label = action.title.ifBlank { action.key.extensionId },
                icon = { FirefoxExtensionActionIcon(action) },
                shape = shape,
                enabled = action.enabled,
                containerColor = browserChromeColor(colors.surfaceContainer),
                modifier = Modifier.testTag(
                    FirefoxExtensionChromeTestTags.action(action.key.saveableId),
                ),
                onClick = { onAction(action.key) },
            )
        }
    }
}

@Composable
private fun FirefoxExtensionActionIcon(action: GeckoExtensionActionState) {
    BadgedBox(
        badge = {
            action.badgeText?.takeIf(String::isNotBlank)?.let { badge ->
                Badge(
                    containerColor = action.badgeBackgroundColor
                        ?.let(::Color)
                        ?: MaterialTheme.colorScheme.error,
                    contentColor = action.badgeTextColor
                        ?.let(::Color)
                        ?: MaterialTheme.colorScheme.onError,
                ) { Text(badge.take(4)) }
            }
        },
    ) {
        action.icon?.let { icon ->
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        } ?: Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}
