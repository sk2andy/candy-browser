package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.shared.ui.TabCardHeroContent
import dev.sk2andy.materialbrowser.shared.ui.TabListHeroContent
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewHeroVisuals
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

@Composable
internal fun tabOverviewHeroVisuals(
    tab: BrowserTab,
    favicon: Bitmap?,
): TabOverviewHeroVisuals {
    val title = displayTabTitle(tab)
    val faviconImage = favicon
        ?.takeUnless(Bitmap::isRecycled)
        ?.asImageBitmap()
    return TabOverviewHeroVisuals(
        title = title,
        subtitle = if (tab.url == BLANK_URL) {
            stringResource(R.string.new_tab_title)
        } else {
            AddressResolver.displayText(tab.url)
        },
        hasFavicon = faviconImage != null,
        chromeContainerColor = browserChromeColor(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            frostedAlpha = 0.88f,
        ),
        pinnedContentDescription = stringResource(R.string.cd_pinned_tab),
        closeContentDescription = stringResource(R.string.cd_close_named_tab, title),
        titleTestTag = SnoozeTestTags.overviewTitle(tab.id),
        closeTestTag = SnoozeTestTags.overviewClose(tab.id),
        favicon = { size ->
            if (faviconImage != null) {
                Image(
                    bitmap = faviconImage,
                    contentDescription = null,
                    modifier = Modifier.size(size),
                    contentScale = ContentScale.Fit,
                )
            } else {
                TabFavicon(tab = tab, favicon = null, size = size)
            }
        },
        incognitoIcon = { modifier, tint ->
            Icon(
                painter = painterResource(R.drawable.ic_incognito_outline),
                contentDescription = null,
                modifier = modifier,
                tint = tint,
            )
        },
        blankIcon = { modifier ->
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground_art),
                contentDescription = null,
                modifier = modifier,
                tint = Color.Unspecified,
            )
        },
        pinnedIcon = { modifier, tint ->
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = stringResource(R.string.cd_pinned_tab),
                modifier = modifier,
                tint = tint,
            )
        },
    )
}

@Composable
internal fun displayTabTitle(tab: BrowserTab): String =
    if (tab.url == BLANK_URL || tab.title.isBlank()) {
        stringResource(R.string.new_tab_title)
    } else {
        tab.title
    }

@Composable
internal fun AndroidTabCardHeroContent(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry>,
    favoriteFavicons: Map<String, Bitmap>,
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: FloatState,
    targetFraction: () -> Float,
) {
    val previewSize = preview
        ?.takeUnless(Bitmap::isRecycled)
        ?.let { bitmap -> IntSize(bitmap.width, bitmap.height) }
    TabCardHeroContent(
        tab = tab,
        previewSize = previewSize,
        targetBounds = targetBounds,
        rootWidthPx = rootWidthPx,
        rootHeightPx = rootHeightPx,
        previewTopInsetPx = previewTopInsetPx,
        bottomBarTopPx = bottomBarTopPx,
        targetFraction = targetFraction,
        fullscreenPreviewContent = { blankFavoritesAlpha ->
            FullscreenTabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                favorites = favorites,
                favoriteFavicons = favoriteFavicons,
                rootHeightPx = rootHeightPx,
                previewTopInsetPx = previewTopInsetPx,
                bottomBarTopPx = bottomBarTopPx,
                blankFavoritesAlpha = blankFavoritesAlpha ?: { 1f },
            )
        },
        previewContent = {
            TabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                favorites = favorites,
                favoriteFavicons = favoriteFavicons,
            )
        },
    )
}

@Composable
internal fun AndroidTabListHeroContent(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry>,
    favoriteFavicons: Map<String, Bitmap>,
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: FloatState,
    targetFraction: () -> Float,
) {
    TabListHeroContent(
        tab = tab,
        visuals = tabOverviewHeroVisuals(tab, favicon),
        targetBounds = targetBounds,
        rootWidthPx = rootWidthPx,
        rootHeightPx = rootHeightPx,
        targetFraction = targetFraction,
        fullscreenPreviewContent = {
            FullscreenTabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                favorites = favorites,
                favoriteFavicons = favoriteFavicons,
                rootHeightPx = rootHeightPx,
                previewTopInsetPx = previewTopInsetPx,
                bottomBarTopPx = bottomBarTopPx,
            )
        },
    )
}
