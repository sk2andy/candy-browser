package dev.sk2andy.materialbrowser.ui

import android.view.WindowManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.BrowserBackdropBlurRegion
import dev.sk2andy.materialbrowser.browser.BrowserBackdropBlurRules
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ExternalLinkPreviewState
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceRole
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens

internal object ExternalLinkPreviewTestTags {
    const val Bar = "external_link_preview_bar"
    const val Back = "external_link_preview_back"
    const val Host = "external_link_preview_host"
    const val Open = "external_link_preview_open"
    const val Profile = "external_link_preview_profile"
    const val Overflow = "external_link_preview_overflow"
    const val Share = "external_link_preview_share"
    const val CopyLink = "external_link_preview_copy_link"
    const val Find = "external_link_preview_find"
    const val Desktop = "external_link_preview_desktop"

    fun profile(profileId: String) = "external_link_preview_profile:$profileId"
}

@Composable
internal fun ExternalLinkPreviewBar(
    state: ExternalLinkPreviewState,
    profiles: List<BrowserProfile>,
    isDesktopView: Boolean,
    backdropSource: CandyChromeBackdropSource?,
    rootBottomInWindowPx: Int,
    onDismissPreview: () -> Unit,
    onOpenInCandy: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onFindInPage: () -> Unit,
    onDesktopViewChange: (Boolean) -> Unit,
    onBackdropBlurRegionChanged: (BrowserBackdropBlurRegion?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var profileMenuExpanded by remember(state.sessionId) { mutableStateOf(false) }
    var overflowMenuExpanded by remember(state.sessionId) { mutableStateOf(false) }
    val targetProfile = profiles.firstOrNull { it.id == state.targetProfileId }
        ?: profiles.firstOrNull()
    val fullWindowHeightPx = LocalContext.current
        .getSystemService(WindowManager::class.java)
        .currentWindowMetrics
        .bounds
        .height()
    val chromeTokens = browserChromeSurfaceTokens(BrowserChromeSurfaceRole.AddressBar)
    val density = LocalDensity.current
    val currentOnBackdropBlurRegionChanged by rememberUpdatedState(onBackdropBlurRegionChanged)
    var barBoundsInWindow by remember(state.sessionId) { mutableStateOf<Rect?>(null) }
    val backdropBlurRegion = barBoundsInWindow
        ?.takeIf {
            !profileMenuExpanded &&
                !overflowMenuExpanded &&
                chromeTokens.backdropBlurEnabled
        }
        ?.let { bounds ->
            BrowserBackdropBlurRules.regionInWindow(
                leftPx = bounds.left,
                topPx = bounds.top,
                rightPx = bounds.right,
                bottomPx = bounds.bottom,
                cornerRadiusPx = with(density) { chromeTokens.cornerRadius.toPx() },
                blurRadiusPx = chromeTokens.blurRadiusPx,
            )
        }
    LaunchedEffect(backdropBlurRegion) {
        currentOnBackdropBlurRegionChanged(backdropBlurRegion)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnBackdropBlurRegionChanged(null) }
    }
    val openDescription = stringResource(
        R.string.external_link_preview_open_description,
        targetProfile?.emoji.orEmpty(),
    )
    val profileDescription = stringResource(
        R.string.external_link_preview_profile_description,
        targetProfile?.emoji.orEmpty(),
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .addressBarWindowInsetsPadding(
                fullWindowHeightPx = fullWindowHeightPx,
                rootBottomInWindowPx = rootBottomInWindowPx,
                imeInsets = WindowInsets.ime,
                navigationBarInsets = WindowInsets.navigationBars,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        CandyChromeSurface(
            backdropSource = backdropSource,
            tokens = chromeTokens,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag(ExternalLinkPreviewTestTags.Bar)
                .onGloballyPositioned { coordinates ->
                    barBoundsInWindow = coordinates.boundsInWindow()
                },
            shape = MaterialTheme.shapes.extraLarge,
            backdropBlurEnabled = !profileMenuExpanded && !overflowMenuExpanded,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismissPreview,
                    modifier = Modifier.testTag(ExternalLinkPreviewTestTags.Back),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_arrow_back),
                        contentDescription = stringResource(
                            R.string.external_link_preview_close_description,
                        ),
                    )
                }
                Text(
                    text = AddressResolver.displayText(state.currentUrl),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ExternalLinkPreviewTestTags.Host)
                        .semantics { contentDescription = state.currentUrl },
                    style = MaterialTheme.typography.labelLarge,
                    color = chromeTokens.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .border(
                            width = 1.dp,
                            color = chromeTokens.outlineColor,
                            shape = CircleShape,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onOpenInCandy,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(ExternalLinkPreviewTestTags.Open),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_add),
                            contentDescription = openDescription,
                            tint = chromeTokens.contentColor,
                        )
                    }
                    if (profiles.size > 1) {
                        VerticalDivider(
                            modifier = Modifier
                                .height(24.dp)
                                .width(1.dp),
                            color = chromeTokens.outlineVariantColor,
                        )
                        Box {
                            IconButton(
                                onClick = { profileMenuExpanded = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag(ExternalLinkPreviewTestTags.Profile)
                                    .semantics { contentDescription = profileDescription },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = targetProfile?.emoji.orEmpty(),
                                        color = chromeTokens.contentColor,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = chromeTokens.contentColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = profileMenuExpanded,
                                onDismissRequest = { profileMenuExpanded = false },
                            ) {
                                profiles.forEachIndexed { index, profile ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                buildString {
                                                    append(index + 1)
                                                    append(" · ")
                                                    append(profile.emoji)
                                                    if (profile.isolationEnabled) append("  🔒")
                                                },
                                            )
                                        },
                                        leadingIcon = if (profile.id == state.targetProfileId) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            profileMenuExpanded = false
                                            onSelectProfile(profile.id)
                                        },
                                        modifier = Modifier.testTag(
                                            ExternalLinkPreviewTestTags.profile(profile.id),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                Box {
                    IconButton(
                        onClick = { overflowMenuExpanded = true },
                        modifier = Modifier.testTag(ExternalLinkPreviewTestTags.Overflow),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_more_options),
                        )
                    }
                    DropdownMenu(
                        expanded = overflowMenuExpanded,
                        onDismissRequest = { overflowMenuExpanded = false },
                    ) {
                        PreviewMenuItem(
                            label = stringResource(R.string.action_share),
                            icon = R.drawable.ic_symbol_share,
                            testTag = ExternalLinkPreviewTestTags.Share,
                            onClick = {
                                overflowMenuExpanded = false
                                onShare()
                            },
                        )
                        PreviewMenuItem(
                            label = stringResource(R.string.external_link_preview_copy_link),
                            icon = R.drawable.ic_content_copy,
                            testTag = ExternalLinkPreviewTestTags.CopyLink,
                            onClick = {
                                overflowMenuExpanded = false
                                onCopyLink()
                            },
                        )
                        PreviewMenuItem(
                            label = stringResource(R.string.action_find_in_page),
                            icon = R.drawable.ic_symbol_find_in_page,
                            testTag = ExternalLinkPreviewTestTags.Find,
                            onClick = {
                                overflowMenuExpanded = false
                                onFindInPage()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_desktop_view)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_symbol_desktop),
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = {
                                Switch(
                                    checked = isDesktopView,
                                    onCheckedChange = null,
                                )
                            },
                            onClick = {
                                overflowMenuExpanded = false
                                onDesktopViewChange(!isDesktopView)
                            },
                            modifier = Modifier.testTag(ExternalLinkPreviewTestTags.Desktop),
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun PreviewMenuItem(
    label: String,
    icon: Int,
    testTag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
            )
        },
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
    )
}
