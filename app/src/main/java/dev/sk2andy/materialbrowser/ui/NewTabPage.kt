@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.FavoriteEntry

@Composable
internal fun NewTabPage(
    favorites: List<FavoriteEntry>,
    incognito: Boolean,
    modeProgress: Float,
    revealOriginInRoot: Offset,
    onSearch: () -> Unit,
    onFavorite: (String) -> Unit,
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
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
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
                    .fillMaxWidth(0.86f)
                    .heightIn(max = 520.dp)
                    .then(if (interactive) Modifier.verticalScroll(scrollState) else Modifier)
                    .padding(vertical = BlankTabModeMorphRules.HERO_SHADOW_CLEARANCE_DP.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                onClick = onSearch,
                enabled = interactive,
                modifier = Modifier
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
                        .graphicsLayer {
                            alpha = favoritesAlpha().coerceIn(0f, 1f)
                        },
                    ) {
                        Spacer(Modifier.height(28.dp))
                        Text(
                        stringResource(R.string.favorites_title),
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (profileWallpaper == null) colors.onSurface else Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = colors.surfaceContainerHigh.copy(alpha = 0.9f),
                        tonalElevation = 8.dp,
                        ) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                ExpressiveFavoriteRows(
                                favorites = favorites,
                                onFavorite = onFavorite,
                                enabled = interactive,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

