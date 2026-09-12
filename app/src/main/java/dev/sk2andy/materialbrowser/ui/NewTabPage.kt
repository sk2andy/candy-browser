@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.FavoriteAnimationSpeed
import dev.sk2andy.materialbrowser.data.FavoriteEntry

@Composable
internal fun NewTabPage(
    favorites: List<FavoriteEntry>,
    favicons: Map<String, Bitmap> = emptyMap(),
    incognito: Boolean,
    modeProgress: Float,
    revealOriginInRoot: Offset,
    onSearch: () -> Unit,
    onFavorite: (String) -> Unit,
    favoriteLaunchAnimationEnabled: Boolean = true,
    favoriteAnimationSpeed: FavoriteAnimationSpeed = FavoriteAnimationSpeed.Default,
    interactive: Boolean = true,
    favoritesAlpha: () -> Float = { 1f },
    explicitSafeDrawingPadding: PaddingValues? = null,
) {
    val colors = MaterialTheme.colorScheme
    val profileWallpaper = LocalProfileWallpaper.current.takeUnless { incognito }
    val boundedProgress = BlankTabModeMorphRules.bounded(modeProgress)
    val regularIconAlpha = BlankTabModeMorphRules.regularIconAlpha(boundedProgress)
    val incognitoIconAlpha = BlankTabModeMorphRules.incognitoIconAlpha(boundedProgress)
    val openSearchDescription = stringResource(R.string.cd_open_search)
    var rootOriginInWindow by remember { mutableStateOf(Offset.Unspecified) }
    var heroCenterInWindow by remember { mutableStateOf(Offset.Unspecified) }
    var launchRequest by remember { mutableStateOf<NewTabFavoriteLaunchRequest?>(null) }
    val contentEnabled = interactive && launchRequest == null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                rootOriginInWindow = coordinates.positionInWindow()
            }
            .blankTabModeBackground(
                progress = boundedProgress,
                revealOriginInRoot = revealOriginInRoot,
                regularCenterColor = colors.primaryContainer,
                incognitoCenterColor = colors.inverseSurface,
                edgeColor = colors.surface,
                wallpaper = profileWallpaper,
            ),
    ) {
        if (profileWallpaper != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(colors.surface.copy(alpha = 0.92f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .background(colors.surface.copy(alpha = 0.92f)),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (explicitSafeDrawingPadding != null) {
                        Modifier.padding(explicitSafeDrawingPadding)
                    } else {
                        Modifier.safeDrawingPadding()
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.82f)
                    .heightIn(max = 664.dp)
                    .padding(vertical = BlankTabModeMorphRules.HERO_SHADOW_CLEARANCE_DP.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    onClick = onSearch,
                    enabled = contentEnabled,
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            heroCenterInWindow = coordinates.boundsInWindow().center
                        }
                        .semantics {
                            contentDescription = openSearchDescription
                        },
                    shape = RoundedCornerShape(
                        BlankTabModeMorphRules.heroCornerRadiusDp(boundedProgress).dp,
                    ),
                    color = lerp(colors.primary, colors.inverseSurface, boundedProgress),
                    shadowElevation = BlankTabModeMorphRules.HERO_SHADOW_ELEVATION_DP.dp,
                ) {
                    Box(
                        modifier = Modifier.size(96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground_art),
                            contentDescription = null,
                            modifier = Modifier
                                .size(68.dp)
                                .graphicsLayer {
                                    alpha = regularIconAlpha
                                    scaleX = BlankTabModeMorphRules.iconScale(regularIconAlpha)
                                    scaleY = scaleX
                                },
                            tint = Color.Unspecified,
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_incognito_filled),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    alpha = incognitoIconAlpha
                                    scaleX = BlankTabModeMorphRules.iconScale(incognitoIconAlpha)
                                    scaleY = scaleX
                                },
                            tint = colors.inverseOnSurface,
                        )
                    }
                }
                if (!incognito && favorites.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .graphicsLayer {
                                alpha = favoritesAlpha().coerceIn(0f, 1f)
                            },
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 6.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors.primary, CircleShape),
                            )
                            Text(
                                text = stringResource(R.string.favorites_title),
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            shape = RoundedCornerShape(28.dp),
                            color = colors.surfaceContainerLow.copy(alpha = 0.74f),
                            tonalElevation = 3.dp,
                            border = BorderStroke(
                                width = 1.dp,
                                color = colors.outlineVariant.copy(alpha = 0.32f),
                            ),
                        ) {
                            NewTabFavoriteGrid(
                                favorites = favorites,
                                favicons = favicons,
                                enabled = contentEnabled,
                                animateShapes = interactive && favoriteLaunchAnimationEnabled,
                                animationSpeed = favoriteAnimationSpeed,
                                onFavorite = { favorite, startCenterInWindow, shapeState ->
                                    if (
                                        !favoriteLaunchAnimationEnabled ||
                                        !startCenterInWindow.isUsable() ||
                                        !rootOriginInWindow.isUsable() ||
                                        !heroCenterInWindow.isUsable()
                                    ) {
                                        onFavorite(favorite.url)
                                    } else {
                                        launchRequest = NewTabFavoriteLaunchRequest(
                                            favorite = favorite,
                                            startCenterInWindow = startCenterInWindow,
                                            shapeState = shapeState,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        launchRequest?.let { request ->
            NewTabFavoriteLaunchOverlay(
                request = request,
                favicon = favicons[request.favorite.url],
                rootOriginInWindow = rootOriginInWindow,
                targetCenterInWindow = heroCenterInWindow,
                onFinished = { favorite ->
                    launchRequest = null
                    onFavorite(favorite.url)
                },
            )
        }
    }
}

private fun Offset.isUsable(): Boolean = x.isFinite() && y.isFinite()
