@file:OptIn(ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.CapsuleSaveResult
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.capsule.CapsuleChromeMode
import dev.sk2andy.materialbrowser.capsule.CapsuleIconMode
import dev.sk2andy.materialbrowser.capsule.CapsuleIconRenderer
import dev.sk2andy.materialbrowser.capsule.CapsuleNavigationMode
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleDraft
import kotlinx.coroutines.flow.collect

object SiteCapsuleTestTags {
    const val Screen = "site_capsule_screen"
    const val WebView = "site_capsule_webview"
    const val Editor = "site_capsule_editor"
    const val Save = "site_capsule_save"
    const val Chrome = "site_capsule_chrome"
}

@Composable
fun SiteCapsuleBrowserScreen(
    controller: BrowserController,
    capsule: SiteCapsule,
) {
    val tab = controller.selectedTab
    val entrance = remember(capsule.id) { Animatable(0f) }
    LaunchedEffect(capsule.id) {
        entrance.animateTo(1f, spring(dampingRatio = 0.84f, stiffness = 520f))
    }
    PredictiveBackHandler(enabled = tab.canGoBack) { events ->
        events.collect { }
        controller.goBack()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(SiteCapsuleTestTags.Screen)
            .graphicsLayer {
                val progress = entrance.value.coerceIn(0f, 1f)
                alpha = progress
                scaleX = 0.94f + progress * 0.06f
                scaleY = scaleX
                shape = RoundedCornerShape(((1f - progress) * 32f).dp)
                clip = progress < 1f
            },
    ) {
        CapsuleWebViewHost(controller)
        tab.error?.takeIf { capsule.chromeMode.showsControls }?.let { error ->
            CapsuleErrorCard(
                message = error,
                onRetry = controller::reload,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (capsule.chromeMode.showsControls && tab.isLoading) {
            LinearProgressIndicator(
                progress = { (tab.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .align(Alignment.TopCenter),
            )
        }
        if (capsule.chromeMode.showsControls) {
            CapsuleChrome(
                capsule = capsule,
                currentUrl = tab.url,
                canGoBack = tab.canGoBack,
                isLoading = tab.isLoading,
                onBack = controller::goBack,
                onReloadOrStop = {
                    if (tab.isLoading) controller.stopLoading() else controller.reload()
                },
                onOpenFullCandy = controller::openSiteCapsuleInFullCandy,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun CapsuleWebViewHost(controller: BrowserController) {
    val selectedTabId = controller.selectedTabId
    val webViewRevision = controller.webViewRevision
    AndroidView(
        factory = { context -> FrameLayout(context) },
        update = { container ->
            container.tag = selectedTabId to webViewRevision
            controller.attachSelectedWebView(container)
        },
        onRelease = controller::detachWebView,
        modifier = Modifier
            .fillMaxSize()
            .testTag(SiteCapsuleTestTags.WebView),
    )
}

@Composable
private fun CapsuleChrome(
    capsule: SiteCapsule,
    currentUrl: String,
    canGoBack: Boolean,
    isLoading: Boolean,
    onBack: () -> Unit,
    onReloadOrStop: () -> Unit,
    onOpenFullCandy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .fillMaxWidth()
            .testTag(SiteCapsuleTestTags.Chrome),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        tonalElevation = 12.dp,
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            IconButton(onClick = onReloadOrStop) {
                Icon(
                    if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = stringResource(
                        if (isLoading) R.string.action_stop_loading else R.string.action_reload,
                    ),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    capsule.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (capsule.chromeMode == CapsuleChromeMode.Compact) {
                    Text(
                        AddressResolver.displayText(currentUrl),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                onClick = onOpenFullCandy,
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    stringResource(R.string.capsule_open_full_candy),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CapsuleErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(24.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.error_page_unreachable), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
fun SiteCapsuleEditorSheet(
    controller: BrowserController,
    existing: SiteCapsule?,
    sourceTitle: String,
    sourceUrl: String,
    sourceFavicon: Bitmap?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val hapticView = LocalView.current
    val profiles = controller.profiles.toList()
    var name by remember(existing?.id, sourceTitle) {
        mutableStateOf(existing?.name ?: sourceTitle.takeIf(String::isNotBlank).orEmpty())
    }
    var url by remember(existing?.id, sourceUrl) { mutableStateOf(existing?.startUrl ?: sourceUrl) }
    var selectedProfileId by remember(existing?.id, controller.activeProfileId) {
        mutableStateOf(existing?.profileId ?: controller.activeProfileId)
    }
    var createDedicatedProfile by remember(existing?.id) {
        mutableStateOf(existing?.ownsDedicatedProfile == true)
    }
    var dedicatedEmoji by remember(existing?.id) { mutableStateOf("🧩") }
    var isolatedStorage by remember(existing?.id) {
        mutableStateOf(existing?.isolatedStorageRequested == true)
    }
    var navigationMode by remember(existing?.id) {
        mutableStateOf(existing?.navigationMode ?: CapsuleNavigationMode.SameOrigin)
    }
    var chromeMode by remember(existing?.id) {
        mutableStateOf(existing?.chromeMode ?: CapsuleChromeMode.Compact)
    }
    var iconMode by remember(existing?.id, sourceFavicon) {
        mutableStateOf(
            existing?.iconMode ?: if (sourceFavicon != null) {
                CapsuleIconMode.Favicon
            } else {
                CapsuleIconMode.ProfileFallback
            },
        )
    }
    val currentSourceFavicon by rememberUpdatedState(sourceFavicon)
    val storedIcon = remember(existing?.id) { existing?.let { controller.siteCapsuleIcon(it.id) } }
    val faviconAvailable = currentSourceFavicon != null ||
        (existing?.iconMode == CapsuleIconMode.Favicon && storedIcon != null)
    val fallbackEmoji = if (existing == null && createDedicatedProfile) {
        dedicatedEmoji
    } else {
        profiles.firstOrNull { it.id == selectedProfileId }?.emoji ?: dedicatedEmoji
    }
    val iconPreview = remember(name, fallbackEmoji, iconMode, currentSourceFavicon, storedIcon) {
        if (iconMode == CapsuleIconMode.Favicon && currentSourceFavicon == null && storedIcon != null) {
            storedIcon
        } else {
            CapsuleIconRenderer.render(
                name = name,
                profileEmoji = fallbackEmoji,
                favicon = currentSourceFavicon.takeIf { iconMode == CapsuleIconMode.Favicon },
            )
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .testTag(SiteCapsuleTestTags.Editor),
        ) {
            Text(
                stringResource(
                    if (existing == null) R.string.capsule_add_title else R.string.capsule_edit_title,
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.capsule_configuration_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            CapsuleIconPreview(
                bitmap = iconPreview,
                fallback = fallbackEmoji,
            )
            Text(stringResource(R.string.capsule_icon_source), style = MaterialTheme.typography.titleMedium)
            CapsuleOptionRow(
                title = stringResource(R.string.capsule_icon_favicon),
                subtitle = stringResource(R.string.capsule_icon_favicon_summary),
                selected = iconMode == CapsuleIconMode.Favicon,
                enabled = faviconAvailable,
                onClick = { iconMode = CapsuleIconMode.Favicon },
            )
            CapsuleOptionRow(
                title = stringResource(R.string.capsule_icon_fallback),
                subtitle = stringResource(R.string.capsule_icon_fallback_summary),
                selected = iconMode == CapsuleIconMode.ProfileFallback,
                onClick = { iconMode = CapsuleIconMode.ProfileFallback },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(48) },
                label = { Text(stringResource(R.string.capsule_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.take(4_096) },
                label = { Text(stringResource(R.string.capsule_start_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.capsule_profile), style = MaterialTheme.typography.titleMedium)
            if (existing == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { createDedicatedProfile = !createDedicatedProfile }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.capsule_dedicated_profile))
                        Text(
                            stringResource(R.string.capsule_dedicated_profile_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = createDedicatedProfile,
                        onCheckedChange = { createDedicatedProfile = it },
                    )
                }
            }
            if (!createDedicatedProfile || existing != null) {
                ProfileChoices(
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    enabled = existing?.ownsDedicatedProfile != true,
                    onSelect = { selectedProfileId = it },
                )
            } else {
                EmojiChoices(selected = dedicatedEmoji, onSelect = { dedicatedEmoji = it })
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = controller.isProfileIsolationSupported) {
                            isolatedStorage = !isolatedStorage
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.capsule_own_storage))
                        Text(
                            stringResource(
                                if (controller.isProfileIsolationSupported) {
                                    R.string.capsule_own_storage_summary
                                } else {
                                    R.string.settings_profile_isolation_unsupported
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isolatedStorage && controller.isProfileIsolationSupported,
                        enabled = controller.isProfileIsolationSupported,
                        onCheckedChange = { isolatedStorage = it },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.capsule_navigation_mode), style = MaterialTheme.typography.titleMedium)
            CapsuleNavigationMode.entries.forEach { mode ->
                CapsuleOptionRow(
                    title = stringResource(mode.titleResource()),
                    subtitle = stringResource(mode.summaryResource()),
                    selected = mode == navigationMode,
                    onClick = { navigationMode = mode },
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.capsule_chrome_mode), style = MaterialTheme.typography.titleMedium)
            CapsuleChromeMode.entries.forEach { mode ->
                CapsuleOptionRow(
                    title = stringResource(mode.titleResource()),
                    subtitle = stringResource(mode.summaryResource()),
                    selected = mode == chromeMode,
                    onClick = { chromeMode = mode },
                )
            }
            if (!controller.isCapsulePinningSupported && existing == null) {
                Text(
                    stringResource(R.string.capsule_pinning_unsupported),
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (BrowserUriPolicy.normalizeHttpUrl(url) == null || name.isBlank()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.capsule_invalid_configuration),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@Button
                    }
                    if (existing == null && !controller.canCreateSiteCapsule) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.capsule_limit_reached),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@Button
                    }
                    var profileId = selectedProfileId
                    var ownsDedicated = existing?.ownsDedicatedProfile == true &&
                        existing.profileId == profileId
                    val previousProfileId = controller.activeProfileId
                    if (existing == null && createDedicatedProfile) {
                        profileId = controller.createProfile(
                            emoji = dedicatedEmoji,
                            isolationEnabled = isolatedStorage,
                        ) ?: return@Button
                        ownsDedicated = true
                    }
                    val result = controller.upsertSiteCapsule(
                        draft = SiteCapsuleDraft(
                            id = existing?.id,
                            name = name,
                            startUrl = url,
                            profileId = profileId,
                            ownsDedicatedProfile = ownsDedicated,
                            isolatedStorageRequested = isolatedStorage,
                            navigationMode = navigationMode,
                            chromeMode = chromeMode,
                            iconMode = iconMode,
                        ),
                        sourceFavicon = currentSourceFavicon,
                    )
                    if (controller.activeProfileId != previousProfileId) {
                        controller.selectProfile(previousProfileId)
                    }
                    val message = when (result) {
                        CapsuleSaveResult.PinRequested -> R.string.capsule_pin_requested
                        CapsuleSaveResult.PinningUnsupported -> R.string.capsule_pinning_unsupported
                        CapsuleSaveResult.PinRequestFailed -> R.string.capsule_pin_failed
                        CapsuleSaveResult.Updated -> R.string.capsule_updated
                        CapsuleSaveResult.UpdateFailed -> R.string.capsule_update_failed
                        CapsuleSaveResult.LimitReached -> R.string.capsule_limit_reached
                        CapsuleSaveResult.Invalid -> R.string.capsule_invalid_configuration
                    }
                    Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
                    if (result != CapsuleSaveResult.Invalid &&
                        result != CapsuleSaveResult.LimitReached
                    ) {
                        if (result == CapsuleSaveResult.PinRequested ||
                            result == CapsuleSaveResult.Updated
                        ) {
                            hapticView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        }
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SiteCapsuleTestTags.Save),
            ) {
                Text(
                    stringResource(
                        if (existing == null) R.string.capsule_request_pin else R.string.action_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CapsuleIconPreview(bitmap: Bitmap?, fallback: String) {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.capsule_icon_preview),
                modifier = Modifier.padding(12.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(contentAlignment = Alignment.Center) { Text(fallback, fontSize = 30.sp) }
        }
    }
}

@Composable
private fun ProfileChoices(
    profiles: List<BrowserProfile>,
    selectedProfileId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        profiles.forEach { profile ->
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled) { onSelect(profile.id) },
                shape = CircleShape,
                color = if (profile.id == selectedProfileId) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                tonalElevation = if (profile.id == selectedProfileId) 5.dp else 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) { Text(profile.emoji, fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun EmojiChoices(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("🧩", "🍬", "💼", "🛒", "🎵", "📚", "🌍", "⭐").forEach { emoji ->
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(emoji) },
                shape = CircleShape,
                color = if (emoji == selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun CapsuleOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) Icon(Icons.Default.Check, contentDescription = null)
        }
    }
}

private fun CapsuleNavigationMode.titleResource(): Int = when (this) {
    CapsuleNavigationMode.SameOrigin -> R.string.capsule_navigation_same_origin
    CapsuleNavigationMode.SameRegistrableDomain -> R.string.capsule_navigation_same_domain
    CapsuleNavigationMode.AllLinks -> R.string.capsule_navigation_all_links
}

private fun CapsuleNavigationMode.summaryResource(): Int = when (this) {
    CapsuleNavigationMode.SameOrigin -> R.string.capsule_navigation_same_origin_summary
    CapsuleNavigationMode.SameRegistrableDomain -> R.string.capsule_navigation_same_domain_summary
    CapsuleNavigationMode.AllLinks -> R.string.capsule_navigation_all_links_summary
}

private fun CapsuleChromeMode.titleResource(): Int = when (this) {
    CapsuleChromeMode.Minimal -> R.string.capsule_chrome_minimal
    CapsuleChromeMode.Compact -> R.string.capsule_chrome_compact
    CapsuleChromeMode.NoControls -> R.string.capsule_chrome_none
}

private fun CapsuleChromeMode.summaryResource(): Int = when (this) {
    CapsuleChromeMode.Minimal -> R.string.capsule_chrome_minimal_summary
    CapsuleChromeMode.Compact -> R.string.capsule_chrome_compact_summary
    CapsuleChromeMode.NoControls -> R.string.capsule_chrome_none_summary
}
